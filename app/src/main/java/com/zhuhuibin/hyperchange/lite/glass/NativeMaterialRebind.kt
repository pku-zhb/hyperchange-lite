// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Re-enter the current OEM state selector, not the previous effect's apply method. */
internal class NativeMaterialRebind(private val runtime: HookRuntime,
                                    private val onSettled: (List<View>) -> Unit = {}) {
    private class RowBinding(val method: Method, val args: Array<Any?>)
    private val rows = WeakHashMap<View, RowBinding>()
    private val mediaInteractors = WeakHashMap<View, WeakReference<Any>>()
    private val loaders = WeakHashMap<ClassLoader, Boolean>()
    private val refreshing = ThreadLocal<Boolean>()
    private val selecting = ThreadLocal<View>()
    private var rowSelections = 0L
    private var mediaBindings = 0L
    private var lastRefresh = "none"
    private val deferred = DeferredMaterialRoots<View, Any>()
    private var transitionRetries = 0L

    fun diagnostics(): String = "rowBindings=${synchronized(rows) { rows.size }} rowSelections=$rowSelections " +
        "mediaBindings=$mediaBindings mediaRoots=${synchronized(mediaInteractors) { mediaInteractors.size }} lastRefresh=$lastRefresh " +
        "deferredRoots=${deferred.size()} transitionRetries=$transitionRetries"

    fun selecting(view: View): Boolean = selecting.get() === view

    fun install(loader: ClassLoader) {
        if (loaders.put(loader, true) != null) return
        runCatching {
            val binder = loader.loadClass("com.android.systemui.statusbar.notification.style.view.ExpandableNotificationRowViewBinder")
            val update = binder.declaredMethods.single { it.name == "bindInternal\$updateEffect" &&
                it.parameterTypes.map { parameter -> parameter.name } == listOf(
                    "com.android.systemui.statusbar.notification.style.domain.NotificationMaterialStateInteractor",
                    "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
                    "com.miui.systemui.material.NotificationViewType", "kotlin.jvm.internal.Ref\$ObjectRef",
                ) }
                .apply { isAccessible = true }
            runtime.hook(update, "lite-material:current-row") { chain ->
                val row = chain.getArg(1) as? View
                rowSelections++
                // A new native selector run supersedes a previous conversion, even
                // when it reuses the same background object and transition alpha.
                val interactor = chain.getArg(0)
                val aod = interactor?.let { field(it, "notifiFullAodController") }
                val transition = aod?.let { field(it, "mWillDoAodAnim") } as? Boolean
                if (row != null && LockscreenRowMaterialPolicy.selectorMayApply(transition,
                        if (transition == true) call(interactor, "shouldApplyFullAodEffect") as? Boolean else null,
                        if (transition == true) call(row, "isChildInGroup") as? Boolean else null))
                    NotificationMaterialWrites.touch(row)
                if (row != null && refreshing.get() != true) {
                    val args = Array<Any?>(4) { if (it == 1) null else chain.getArg(it) }
                    synchronized(rows) { rows[row] = RowBinding(update, args) }
                }
                val previous = selecting.get()
                if (row != null) selecting.set(row)
                try { chain.proceed() } finally {
                    if (previous == null) selecting.remove() else selecting.set(previous)
                }
            }
        }.onFailure { runtime.log("Current notification material selector unavailable", it) }
        runCatching {
            val binder = loader.loadClass("com.android.systemui.statusbar.notification.style.view.MediaViewBinder")
            val bind = binder.declaredMethods.single { it.name == "bind" && it.parameterCount == 2 &&
                it.parameterTypes[0].name.endsWith(".MiuiMediaHeaderView") }
            runtime.hook(bind, "lite-material:current-media") { chain ->
                val header = chain.getArg(0) as? View
                val interactor = chain.getArg(1)
                if (header != null && interactor != null) {
                    mediaBindings++
                    GlassSurfaces.markMediaRoot(header)
                    synchronized(mediaInteractors) { mediaInteractors[header] = WeakReference(interactor) }
                }
                chain.proceed()
            }
        }.onFailure { runtime.log("Current media material selector unavailable", it) }
        runCatching {
            val listener = loader.loadClass("com.android.systemui.statusbar.notification.fullaod.NotifiFullAodController\$FullAodTransitionListener")
            listener.declaredMethods.filter { it.name in setOf("onComplete", "onCancel") && it.parameterCount == 1 }
                .forEach { method ->
                    runtime.hook(method, "lite-material:settled:${method.name}") { chain ->
                        val result = chain.proceed()
                        val owner = chain.thisObject?.let { field(it, "this\$0") }
                        if (owner != null) {
                            val ready = deferred.take(owner, field(owner, "mWillDoAodAnim") == false)
                                .filter { root -> root.isAttachedToWindow && boundTo(root, owner) }
                            if (ready.isNotEmpty()) {
                                transitionRetries += ready.size
                                // Queue through the existing current-scene path after all native
                                // completion hooks return; do not replay a captured effect here.
                                onSettled(ready)
                            }
                        }
                        result
                    }
                }
        }.onFailure { runtime.log("Native material transition completion unavailable", it) }
    }

    private fun boundTo(view: View, owner: Any): Boolean {
        val interactor = if (GlassSurfaces.media(view)) synchronized(mediaInteractors) { mediaInteractors[view]?.get() }
            else synchronized(rows) { rows[view] }?.args?.getOrNull(0)
        return interactor?.let { field(it, "notifiFullAodController") } === owner
    }

    fun root(view: View): View? = if (GlassSurfaces.media(view)) GlassSurfaces.materialRoot(view) else GlassSurfaces.row(view)

    /** Bindings exist even when tuning was disabled and no setter baseline was kept. */
    fun roots(): List<View> = (synchronized(rows) { rows.keys.toList() } +
        synchronized(mediaInteractors) { mediaInteractors.filterValues { it.get() != null }.keys.toList() }).distinct()

    /** Same state used by bindInternal$updateEffect, read only for its current ordinary row. */
    fun lockscreenGlassEffect(view: View): String? {
        val interactor = synchronized(rows) { rows[view] }?.args?.getOrNull(0) ?: return null
        val aod = field(interactor, "notifiFullAodController") ?: return null
        val delegate = field(interactor, "materialViewState\$delegate") ?: return null
        val flow = call(delegate, "getValue") ?: return null
        val current = call(flow, "getValue") ?: return null
        return LockscreenRowMaterialPolicy.effect(NotificationScope.confirmedLitKeyguard(),
            field(current, "isShowingKeyguardNotification") as? Boolean,
            field(current, "isLightWallPaper") as? Boolean,
            call(interactor, "shouldApplyFullAodEffect") as? Boolean,
            field(aod, "mWillDoAodAnim") as? Boolean)
    }

    fun refresh(view: View): Boolean {
        if (!view.isAttachedToWindow || refreshing.get() == true) { lastRefresh = "detached-or-reentrant"; return false }
        if (GlassSurfaces.media(view)) return refreshMedia(view)
        val binding = synchronized(rows) { rows[view] } ?: run { lastRefresh = "missing-row-binding"; return false }
        val interactor = binding.args.getOrNull(0) ?: return false
        // The native selector intentionally does no material work during this transition.
        val aod = field(interactor, "notifiFullAodController") ?: return false
        val busy = field(aod, "mWillDoAodAnim")
        if (busy != false) {
            if (busy == true) deferred.blocked(view, aod)
            lastRefresh = "row-aod-transition"; return false
        }
        deferred.clear(view)
        refreshing.set(true)
        return try {
            // Target ROM's four-argument binder always selects/applies current flows.
            // Keep its previous-effect reference intact: it owns required clear steps.
            binding.method.invoke(null, *binding.args.copyOf().also { it[1] = view })
            lastRefresh = "row-selector-returned"
            true
        } catch (error: Throwable) {
            runtime.log("Current notification material refresh failed", error)
            lastRefresh = "row-selector-failed"
            false
        } finally { refreshing.remove() }
    }

    private fun refreshMedia(view: View): Boolean {
        val interactor = synchronized(mediaInteractors) { mediaInteractors[view]?.get() } ?: run { lastRefresh = "missing-media-binding"; return false }
        val aod = field(interactor, "notifiFullAodController") ?: return false
        // The native collector deliberately suppresses material writes during this
        // transition. Do not claim a rebind or replay an older effect through it.
        val busy = field(aod, "mWillDoAodAnim")
        if (busy != false) {
            if (busy == true) deferred.blocked(view, aod)
            lastRefresh = "media-aod-transition"; return false
        }
        deferred.clear(view)
        refreshing.set(true)
        return try {
            val injector = view.javaClass.getMethod("getInjector").invoke(view) ?: return false
            val model = field(injector, "viewModel") ?: return false
            val events = field(model, "_onBackgroundUpdateEvents") ?: return false
            val unit = view.javaClass.classLoader!!.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
            // MediaViewBinder's existing combine collector ignores the event payload,
            // reads the current state flows and applies the selected current effect.
            // Calling bind() again would install a second collector and is forbidden.
            (events.javaClass.getMethod("tryEmit", Any::class.java).invoke(events, unit) == true).also {
                lastRefresh = if (it) "media-event-admitted" else "media-event-rejected"
            }
        } catch (error: Throwable) {
            runtime.log("Current media material update request failed", error)
            lastRefresh = "media-event-failed"
            false
        } finally { refreshing.remove() }
    }

    private fun field(owner: Any, name: String): Any? {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            val current = type
            runCatching { current.getDeclaredField(name).apply { isAccessible = true }.get(owner) }.getOrNull()?.let { return it }
            type = current.superclass
        }
        return null
    }

    private fun call(owner: Any, name: String): Any? = runCatching {
        owner.javaClass.getMethod(name).apply { isAccessible = true }.invoke(owner)
    }.getOrNull()
}
