// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Add/remove uses the OEM's dedicated property channel, never a row's base scale. */
internal class NotificationRowAnimations {
    private val sessions = WeakHashMap<View, Session>()
    private lateinit var runtime: HookRuntime
    private lateinit var prefs: SharedPreferences
    private lateinit var scaleX: Any
    private lateinit var scaleY: Any
    private lateinit var opacity: Any
    private lateinit var setValue: Method
    private lateinit var getValue: Method
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun install(runtime: HookRuntime) {
        this.runtime = runtime
        prefs = runtime.preferences
        val injector = runtime.type("com.android.systemui.statusbar.notification.row.ExpandableViewInjector") ?: return
        val row = runtime.type("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") ?: return
        runCatching {
            scaleX = requireNotNull(injector.fieldOrNull("FOLME_NOTIF_ADD_REMOVE_SCALE_X")?.get(null))
            scaleY = requireNotNull(injector.fieldOrNull("FOLME_NOTIF_ADD_REMOVE_SCALE_Y")?.get(null))
            opacity = requireNotNull(injector.fieldOrNull("FOLME_NOTIF_ADD_REMOVE_TRANSITION_ALPHA")?.get(null))
            setValue = scaleX.javaClass.methodOrNull("setValue", Any::class.java, Float::class.javaPrimitiveType!!)!!
            getValue = scaleX.javaClass.methodOrNull("getValue", Any::class.java)!!
        }.onFailure { runtime.log("Notification add/remove property channel unavailable; native animation kept", it) }.getOrNull() ?: return

        injector.declaredMethods.filter { it.name == "performAddAnimation" && it.parameterCount == 3 && it.parameterTypes[2] == Boolean::class.javaPrimitiveType }
            .forEach { method -> runtime.hook(method, "notification-motion:add") { chain ->
                val view = runCatching { chain.thisObject?.read("view") as? View }.getOrNull()
                if (!prefs.flag("notification_stack_anim_enabled") || view == null || !row.isInstance(view) ||
                    chain.getArg(2) == true || view.call("isHeadsUpState") == true) chain.proceed()
                else if (runCatching { start(view, true, null, null, null) }.onFailure {
                    runtime.log("Notification add animation unavailable; native animation kept", it)
                }.isFailure) chain.proceed() else null
            } }
        row.declaredMethods.filter { it.name == "performRemoveAnimation" && it.parameterCount == 8 &&
            it.parameterTypes[4] == Runnable::class.java && it.parameterTypes[5] == Runnable::class.java }
            .forEach { method -> runtime.hook(method, "notification-motion:remove") { chain ->
                val view = chain.thisObject as? View
                if (!prefs.flag("notification_stack_anim_enabled") || view == null || chain.getArg(2) == true ||
                    view.call("isHeadsUpState") == true) chain.proceed()
                else {
                    val success = runCatching {
                        start(view, false, chain.getArg(4) as? Runnable, chain.getArg(5) as? Runnable,
                            chain.getArg(6) as? AnimatorListenerAdapter)
                    }.onFailure { runtime.log("Notification remove animation unavailable; native animation kept", it) }.isSuccess
                    if (success) 0L else chain.proceed()
                }
            } }
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "notification_stack_anim_enabled") Handler(Looper.getMainLooper()).post {
                if (!prefs.flag("notification_stack_anim_enabled")) sessions.values.toList().forEach { it.finish(true) }
            }
        }.also(prefs::registerOnSharedPreferenceChangeListener)
    }

    private fun write(view: View, scale: Float, alpha: Float) {
        setValue.invoke(scaleX, view, scale)
        setValue.invoke(scaleY, view, scale)
        setValue.invoke(opacity, view, alpha.coerceIn(0f, 1f))
    }

    private fun start(view: View, adding: Boolean, onStart: Runnable?, onEnd: Runnable?, listener: AnimatorListenerAdapter?) {
        sessions.remove(view)?.finish(true)
        // Read before any write. A failing preflight leaves the native method untouched.
        val baseline = arrayOf(scaleX, scaleY, opacity).map { (getValue.invoke(it, view) as Number).toFloat() }.toFloatArray()
        check(baseline.all(Float::isFinite))
        val prefix = "notification_stack_anim_${if (adding) "add" else "remove"}"
        val scale = prefs.number("${prefix}_scale", .5f, .4f, 1f)
        val alpha = prefs.number("${prefix}_alpha", 0f, 0f, 1f)
        val blur = prefs.number("${prefix}_blur", if (adding) 50f else 20f, 0f, 100f)
        val duration = prefs.number("${prefix}_duration", if (adding) 700f else 500f, 0f, 1500f).toLong()
        val damping = prefs.number("notification_stack_anim_damping", .6f, .2f, 1.2f)
        val frequency = prefs.number("notification_stack_anim_frequency", 8f, 2f, 24f)
        val session = Session(view, baseline, onEnd, listener)
        sessions[view] = session
        view.addOnAttachStateChangeListener(session)
        try {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                setInterpolator { NotificationMotion.spring(it, damping, frequency) }
                addUpdateListener {
                    val p = it.animatedValue as Float
                    val amount = if (adding) 1f - p else p
                    runCatching {
                        write(view, 1f - (1f - scale) * amount, 1f - (1f - alpha) * amount)
                        session.blur.apply(blur * amount)
                    }.onFailure { session.finish(true) }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        session.committed = true
                        runCatching { onStart?.run() }
                        runCatching { listener?.onAnimationStart(animation) }
                    }
                    override fun onAnimationEnd(animation: Animator) { session.finish(false) }
                })
            }
            session.animator = animator
            if (adding) { write(view, scale, alpha); session.blur.apply(blur) }
            animator.start()
        } catch (error: Throwable) {
            session.finish(true)
            // Once callbacks have begun, finish our removal rather than calling native twice.
            if (!session.committed) throw error
            runtime.log("Notification animation ended after a runtime failure", error)
        }
    }

    private inner class Session(private val view: View, private val baseline: FloatArray, onEnd: Runnable?, private val external: AnimatorListenerAdapter?) : View.OnAttachStateChangeListener {
        val blur = SurfaceBlur(view) { runtime.log(it) }
        var committed = false
        var animator: ValueAnimator? = null
        private val completion = Completion {
            sessions.remove(view, this)
            view.removeOnAttachStateChangeListener(this)
            arrayOf(scaleX, scaleY, opacity).forEachIndexed { index, property ->
                runCatching { setValue.invoke(property, view, if (committed) 1f else baseline[index]) }
            }
            runCatching { blur.restore() }
            if (committed) {
                runCatching { onEnd?.run() }
                runCatching { animator?.let { external?.onAnimationEnd(it) } }
            }
        }
        fun finish(cancel: Boolean) {
            if (cancel) { animator?.removeAllListeners(); animator?.cancel() }
            completion.finish()
        }
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { finish(true) }
    }
}

/** Captures an existing effect on ROMs exposing the getter; owns only the animation interval. */
internal class SurfaceBlur(private val view: View, private val log: (String) -> Unit = {}) {
    private val capture = runCatching {
        val getter = requireNotNull(view.javaClass.methodOrNull("getRenderEffect"))
        val value = getter.invoke(view)
        check(value == null || value is RenderEffect)
        value as? RenderEffect
    }
    private val original = capture.getOrNull()
    private var wrote = false
    private var last = Float.NaN
    fun apply(radius: Float) {
        if (capture.isFailure) {
            if (radius > 0f && warned.compareAndSet(false, true)) log("Surface blur skipped: OEM RenderEffect baseline cannot be read safely")
            return
        }
        val rounded = NotificationMotion.blurRadius(radius.coerceAtLeast(0f))
        if (last == rounded) return
        last = rounded
        val blur = if (rounded > 0f) RenderEffect.createBlurEffect(rounded, rounded, Shader.TileMode.CLAMP) else null
        view.setRenderEffect(if (blur != null && original != null) RenderEffect.createChainEffect(blur, original) else blur ?: original)
        wrote = true
    }
    fun restore() { if (wrote) view.setRenderEffect(original); wrote = false; last = Float.NaN }
    companion object { private val warned = java.util.concurrent.atomic.AtomicBoolean() }
}
