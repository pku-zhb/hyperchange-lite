// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.graphics.Rect
import android.os.Handler
import android.view.SurfaceControl
import android.view.View
import android.view.ViewTreeObserver
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/** Use the shipped helper; never capture the notification window or sample without its stop layer. */
internal class NativeBannerSampler(private val view: View, loader: ClassLoader,
                                   background: Executor, private val onLuma: (Float) -> Unit,
                                   onFailure: (Throwable) -> Unit) {
    private val type = loader.loadClass("com.android.wm.shell.shared.handles.RegionSamplingHelper")
    private val callback = loader.loadClass("${type.name}\$SamplingCallback")
    private val bounds = Rect()
    private var enabled = false
    private var closed = false
    private var received = false
    private val expectedRoot = view.rootView
    private val helper: Any
    private val start = type.getMethod("start", Rect::class.java)
    private val stop = type.getMethod("stop")
    private val destroy = type.getMethod("stopAndDestroy")

    init {
        require(callback.isInterface)
        // Resolve every private read before constructing a native listener.
        requireNotNull(type.fieldOrNull("mCurrentMedianLuma"))
        requireNotNull(type.fieldOrNull("mRegisteredStopLayer"))
        val proxy = Proxy.newProxyInstance(loader, arrayOf(callback)) { instance, method, args -> when (method.name) {
            "getSampledRegion" -> Rect(bounds)
            "isSamplingEnabled" -> enabled && !closed && currentSurface() != null
            "onRegionDarknessChanged" -> { received = true; readLuma()?.let(onLuma); null }
            "toString" -> "hc-lite banner sampling callback"
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> args?.getOrNull(0) === instance
            else -> null
        } }
        helper = type.getConstructor(View::class.java, callback, Executor::class.java, Executor::class.java)
            .newInstance(view, proxy, view.context.mainExecutor, Executor { command ->
                background.execute { try { command.run() } catch (error: Throwable) { onFailure(error) } }
            })
    }

    private fun currentSurface(): SurfaceControl? = runCatching {
        if (!view.isAttachedToWindow || view.rootView !== expectedRoot || view.display?.displayId != 0) return null
        (view.call("getViewRootImpl")?.call("getSurfaceControl") as? SurfaceControl)?.takeIf { it.isValid }
    }.getOrNull()

    fun begin(region: Rect): Boolean {
        if (closed || currentSurface() == null || region.isEmpty) return false
        bounds.set(region)
        received = false
        enabled = true
        type.getMethod("setWindowVisible", Boolean::class.javaPrimitiveType).invoke(helper, true)
        start.invoke(helper, Rect(bounds))
        // The helper's no-surface fallback can sample without excluding its own drawing.
        // That path is never admissible here, even if it later supplies a plausible luma.
        if (registeredSurface() == null) { end(); return false }
        return true
    }

    private fun registeredSurface(): SurfaceControl? =
        (helper.read("mRegisteredStopLayer") as? SurfaceControl)?.takeIf { it.isValid }

    fun readLuma(): Float? = runCatching {
        if (!enabled || closed || !received || currentSurface() == null || registeredSurface() == null) return null
        (helper.read("mCurrentMedianLuma") as? Number)?.toFloat()?.takeIf { it.isFinite() && it in 0f..1f }
    }.getOrNull()

    fun end() { enabled = false; if (!closed) stop.invoke(helper) }
    fun close() {
        if (closed) return
        enabled = false
        closed = true
        runCatching { destroy.invoke(helper) }
        // Native helper destruction frees its listener, but leaves View callbacks installed.
        (helper as? View.OnAttachStateChangeListener)?.let(view::removeOnAttachStateChangeListener)
        (helper as? View.OnLayoutChangeListener)?.let(view::removeOnLayoutChangeListener)
        runCatching {
            val remove = helper.read("mRemoveDrawRunnable") as? Runnable
            if (remove != null) (helper.read("mHandler") as? Handler)?.removeCallbacks(remove)
            (helper.read("mUpdateOnDraw") as? ViewTreeObserver.OnDrawListener)?.let {
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(it)
            }
        }
    }
}
