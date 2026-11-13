// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import android.os.Handler
import android.os.Looper
import android.view.SurfaceControl
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.ShadeScope
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.WeakHashMap

/**
 * The retained OEM sends full-window blur to BlurUtils.applyBlur(ViewRootImpl,int),
 * not to View.setMiBackgroundBlurRadius. Admit only that native controller's run.
 */
internal class WindowBackdropHooks(private val runtime: HookRuntime,
                                  private val settings: () -> SoftGlassSettings,
                                  private val nativeBackground: (View) -> Unit) {
    private val calls = WindowBackdropCallGuard<View>({ ShadeScope.generation }) {
        it.isAttachedToWindow && ShadeScope.allows(it)
    }
    private val sources = WeakHashMap<Runnable, WeakReference<View>>()
    private val handler = Handler(Looper.getMainLooper())
    private val viewRoot = runCatching {
        View::class.java.getMethod("getViewRootImpl").apply { isAccessible = true }
    }.getOrNull()
    private val surface = runCatching {
        viewRoot?.returnType?.getMethod("getSurfaceControl")?.apply { isAccessible = true }
    }.getOrNull()
    private val rebinders = mutableListOf<() -> Unit>()
    private var solidRefresh: (() -> Unit)? = null
    private var refresh: Runnable? = null
    private var installed = false

    fun install() {
        if (installed) return
        // Provider identity is useful even when an unknown ROM has no compositor adapter.
        installProvider()
        installBlendRefresh()
        installSolidRefresh()
        val task = runtime.type("com.miui.systemui.shade.blur.ShadeWindowBlurController\$updateBlurRatioRunnable\$1") ?: return
        val blur = runtime.type("com.android.systemui.statusbar.BlurUtils") ?: return
        val owner = runCatching { task.getDeclaredField("this\$0").apply { isAccessible = true } }.getOrNull() ?: return
        val lazyView = runCatching { owner.type.getDeclaredField("windowViewLazy").apply { isAccessible = true } }.getOrNull() ?: return
        val getView = runCatching { lazyView.type.getMethod("get").apply { isAccessible = true } }.getOrNull() ?: return
        val run = runCatching { task.getDeclaredMethod("run") }.getOrNull() ?: return
        val apply = blur.declaredMethods.singleOrNull { it.name == "applyBlur" && it.returnType == Void.TYPE &&
            it.parameterTypes.size == 2 && it.parameterTypes[0].name == "android.view.ViewRootImpl" &&
            it.parameterTypes[1] == Int::class.javaPrimitiveType } ?: return
        val rootMethod = viewRoot ?: return
        runtime.hook(run, "window-backdrop:native-task") { chain ->
            val runnable = chain.thisObject as? Runnable ?: return@hook chain.proceed()
            val view = runCatching {
                getView.invoke(lazyView.get(owner.get(runnable))) as? View
            }.getOrNull()
            if (view != null) synchronized(sources) { sources[runnable] = WeakReference(view) }
            val admitted = view?.takeIf { settings().enabled }
            val root = admitted?.let { runCatching { rootMethod.invoke(it) }.getOrNull() }
            calls.during(admitted, root) { chain.proceed() }
        }
        runtime.hook(apply, "window-backdrop:compositor-radius") { chain ->
            val state = settings()
            if (!state.enabled || !calls.allows(chain.getArg(0))) return@hook chain.proceed()
            val input = chain.getArg(1) as? Int ?: return@hook chain.proceed()
            val output = WindowBackdropPolicy.radius(input, state.enabled, state.backgroundBlur)
            if (input == output) chain.proceed()
            else chain.proceedWith(chain.thisObject, arrayOf(chain.getArg(0), output))
        }
        installed = true
        runtime.log("Full-window compositor blur channel installed")
    }

    private fun installProvider() {
        val type = runtime.type("com.miui.systemui.shade.blur.ShadeBlendBlurController\$BlurProvider") ?: return
        val field = runCatching { type.getDeclaredField("view").apply { isAccessible = true } }.getOrNull() ?: return
        if (!View::class.java.isAssignableFrom(field.type)) return
        val enabled = runCatching { type.getDeclaredField("enabled").apply { isAccessible = true } }.getOrNull()
        val maxima = runCatching { listOf("maxRadius", "maxGlassSmallBlurRadius", "maxGlassBigBlurRadius")
            .map { type.getDeclaredField(it).apply { isAccessible = true } } }.getOrNull()
        val update = runCatching { type.getDeclaredMethod("updateMaxRadius", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true } }.getOrNull()
        val mode = runCatching { View::class.java.getMethod("setMiBackgroundBlurMode", Int::class.javaPrimitiveType) }.getOrNull()
        track(type, field, "provider", setOf("setBlurRatio", "applyGlassBlurBlurRatio", "setPassBlur", "updateMaxRadius")) { owner, view, current ->
            // These are current OEM facts, not saved View properties or an old scene.
            if (enabled != null && mode != null && current())
                mode.invoke(view, if (enabled.getBoolean(owner)) 1 else 0)
            if (maxima != null && update != null && current())
                update.invoke(owner, *maxima.map { it.getInt(owner) as Any }.toTypedArray())
        }
    }

    private fun installBlendRefresh() {
        val type = runtime.type("com.miui.systemui.shade.blur.ShadeBlendBlurController\$BlendBackground") ?: return
        val field = runCatching { type.getDeclaredField("view").apply { isAccessible = true } }.getOrNull() ?: return
        val apply = runCatching { type.getDeclaredMethod("applyBlendColors").apply { isAccessible = true } }.getOrNull() ?: return
        track(type, field, "blend", setOf("applyBlendColors", "updateBlendColors")) { owner, _, current ->
            if (current()) apply.invoke(owner)
        }
    }

    private fun installSolidRefresh() {
        val type = runtime.type("com.miui.systemui.shade.blur.ShadeSolidBackgroundController") ?: return
        val field = runCatching { type.getDeclaredField("windowBackground").apply { isAccessible = true } }.getOrNull() ?: return
        val get = runCatching { field.type.getMethod("get").apply { isAccessible = true } }.getOrNull() ?: return
        val update = runCatching { type.getDeclaredMethod("updateWindow").apply { isAccessible = true } }.getOrNull() ?: return
        val owners = WeakHashMap<Any, WeakReference<View>>()
        runtime.hook(update, "window-backdrop:solid-current") { chain ->
            val result = chain.proceed()
            runCatching {
                val owner = chain.thisObject ?: return@runCatching
                // Resolve only after the OEM has used this Lazy, preserving startup order.
                val view = get.invoke(field.get(owner)) as? View ?: return@runCatching
                synchronized(owners) { owners[owner] = WeakReference(view) }
                nativeBackground(view)
                val epoch = ShadeScope.generation
                if (WindowBackdropPolicy.transparent(settings()) && view.isAttachedToWindow &&
                    ShadeScope.allows(view) && epoch == ShadeScope.generation) {
                    // Also removes the OEM's preblurred screenshot fallback at the
                    // explicit transparent endpoint; never multiply a card's alpha.
                    view.alpha = 0f
                }
            }.onFailure { runtime.log("Solid backdrop retained native state", it) }
            result
        }
        solidRefresh = {
            val current = synchronized(owners) { owners.entries.map { it.key to it.value.get() } }
            current.forEach { (owner, view) ->
                if (view != null && view.isAttachedToWindow && ShadeScope.allows(view)) {
                    val epoch = ShadeScope.generation
                    if (get.invoke(field.get(owner)) === view && epoch == ShadeScope.generation)
                        update.invoke(owner)
                }
            }
        }
    }

    /** Retain native writers weakly; never turn a missing baseline into a stale replay. */
    private fun track(type: Class<*>, field: Field, id: String, methods: Set<String>,
                      write: (Any, View, () -> Boolean) -> Unit) {
        if (!View::class.java.isAssignableFrom(field.type)) return
        val owners = WeakHashMap<Any, WeakReference<View>>()
        fun observe(owner: Any?) {
            if (owner == null) return
            val view = runCatching { field.get(owner) as? View }.getOrNull() ?: return
            GlassSurfaces.markWindowBackdrop(view)
            synchronized(owners) { owners[owner] = WeakReference(view) }
        }
        type.declaredConstructors.forEachIndexed { index, constructor ->
            runtime.hook(constructor, "window-backdrop:$id-created:$index") { chain ->
                val result = chain.proceed()
                runCatching { observe(chain.thisObject) }
                result
            }
        }
        type.declaredMethods.filter { it.name in methods }.forEach { method ->
            runtime.hook(method, "window-backdrop:$id-view:${method.name}") { chain ->
                runCatching { observe(chain.thisObject) }
                chain.proceed()
            }
        }
        rebinders += {
            val active = synchronized(owners) { owners.entries.map { it.key to it.value.get() } }
            active.forEach { (owner, view) ->
                if (view != null && view.isAttachedToWindow && GlassSurfaces.classify(view) == GlassSurface.SHADE && ShadeScope.allows(view)) {
                    val epoch = ShadeScope.generation
                    val root = view.rootView
                    val current = { view.isAttachedToWindow && field.get(owner) === view && view.rootView === root &&
                        epoch == ShadeScope.generation && ShadeScope.allows(view) && epoch == ShadeScope.generation }
                    runCatching { write(owner, view, current) }
                        .onFailure { runtime.log("Native backdrop writer retained its current state: $id", it) }
                }
            }
        }
    }

    /** Ask the native task for current scene state, never replay a radius from an old window. */
    fun scheduleRefresh() {
        refresh?.let(handler::removeCallbacks)
        refresh = Runnable {
            rebinders.forEach { rebind -> runCatching(rebind)
                .onFailure { runtime.log("Native backdrop refresh cancelled safely", it) } }
            val active = synchronized(sources) { sources.entries.map { it.key to it.value.get() } }
            active.forEach { (task, view) ->
                if (view != null && view.isAttachedToWindow && ShadeScope.allows(view)) {
                    // Do not start the OEM's invalid-surface retry loop on behalf of an edit.
                    val valid = runCatching {
                        val root = viewRoot?.invoke(view)
                        root != null && (surface?.invoke(root) as? SurfaceControl)?.isValid == true
                    }.getOrDefault(false)
                    if (valid) runCatching { task.run() }
                        .onFailure { runtime.log("Window backdrop refresh retained native state", it) }
                }
            }
            // Last writer is the OEM's current solid/screenshot decision, including
            // its native background clear. An old color baseline cannot resurrect it.
            solidRefresh?.let { rebind -> runCatching(rebind)
                .onFailure { runtime.log("Solid backdrop refresh cancelled safely", it) } }
        }.also { handler.postDelayed(it, 16L) }
    }
}
