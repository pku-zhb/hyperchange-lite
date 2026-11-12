// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Re-enter the current OEM state selector, not the previous effect's apply method. */
internal class NativeMaterialRebind(private val runtime: HookRuntime) {
    private class RowBinding(val method: Method, val args: Array<Any?>)
    private val rows = WeakHashMap<View, RowBinding>()
    private val mediaInteractors = WeakHashMap<View, WeakReference<Any>>()
    private val loaders = WeakHashMap<ClassLoader, Boolean>()
    private val refreshing = ThreadLocal<Boolean>()

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
                if (row != null && refreshing.get() != true) {
                    val args = Array<Any?>(4) { if (it == 1) null else chain.getArg(it) }
                    synchronized(rows) { rows[row] = RowBinding(update, args) }
                }
                chain.proceed()
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
                    GlassSurfaces.markMediaRoot(header)
                    synchronized(mediaInteractors) { mediaInteractors[header] = WeakReference(interactor) }
                }
                chain.proceed()
            }
        }.onFailure { runtime.log("Current media material selector unavailable", it) }
    }

    fun root(view: View): View? = if (GlassSurfaces.media(view)) GlassSurfaces.materialRoot(view) else GlassSurfaces.row(view)

    /** Bindings exist even when tuning was disabled and no setter baseline was kept. */
    fun roots(): List<View> = (synchronized(rows) { rows.keys.toList() } +
        synchronized(mediaInteractors) { mediaInteractors.filterValues { it.get() != null }.keys.toList() }).distinct()

    fun refresh(view: View): Boolean {
        if (!view.isAttachedToWindow || refreshing.get() == true) return false
        if (GlassSurfaces.media(view)) return refreshMedia(view)
        val binding = synchronized(rows) { rows[view] } ?: return false
        refreshing.set(true)
        return try {
            // Target ROM's four-argument binder always selects/applies current flows.
            // Keep its previous-effect reference intact: it owns required clear steps.
            binding.method.invoke(null, *binding.args.copyOf().also { it[1] = view })
            true
        } catch (error: Throwable) {
            runtime.log("Current notification material refresh failed", error)
            false
        } finally { refreshing.remove() }
    }

    private fun refreshMedia(view: View): Boolean {
        val interactor = synchronized(mediaInteractors) { mediaInteractors[view]?.get() } ?: return false
        val aod = field(interactor, "notifiFullAodController") ?: return false
        // The native collector deliberately suppresses material writes during this
        // transition. Do not claim a rebind or replay an older effect through it.
        if (field(aod, "mWillDoAodAnim") != false) return false
        refreshing.set(true)
        return try {
            val injector = view.javaClass.getMethod("getInjector").invoke(view) ?: return false
            val model = field(injector, "viewModel") ?: return false
            val events = field(model, "_onBackgroundUpdateEvents") ?: return false
            val unit = view.javaClass.classLoader!!.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
            // MediaViewBinder's existing combine collector ignores the event payload,
            // reads the current state flows and applies the selected current effect.
            // Calling bind() again would install a second collector and is forbidden.
            events.javaClass.getMethod("tryEmit", Any::class.java).invoke(events, unit) == true
        } catch (error: Throwable) {
            runtime.log("Current media material update request failed", error)
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
}
