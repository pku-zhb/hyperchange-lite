// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger normal notification recipe and focus/media enforcement.
package com.zhuhuibin.hyperchange.lite.glass

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.view.ViewGroup
import java.lang.ref.WeakReference
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import com.zhuhuibin.hyperchange.lite.scope.NotificationScopeToken
import java.util.WeakHashMap

internal class NotificationMaterial(
    private val runtime: HookRuntime,
    private val permitted: (View) -> Boolean,
    private val tuning: () -> Boolean,
    private val selected: (View) -> Boolean,
) {
    private data class Recipe(val configuration: Int, val values: FloatArray)
    private val recipes = WeakHashMap<Resources, Recipe>()
    private val applying = ThreadLocal<Boolean>()
    private val knownRoots = WeakHashMap<View, NotificationScopeToken>()
    private val forcedRoots = WeakHashMap<View, MaterialOwnership>()
    private class Pending(val job: Runnable, val scope: NotificationScopeToken)
    private val pending = WeakHashMap<View, Pending>()
    private val refreshedRoots = NativeMaterialRefreshes<View, NotificationScopeToken>()
    private val releases = MaterialReleaseLedger<View>()
    private val generation = MaterialGeneration()
    private val native = NativeMaterialRebind(runtime)
    private val effectPrefix = "com.android.systemui.statusbar.notification.style.vieweffect."
    private val installedLoaders = WeakHashMap<ClassLoader, Boolean>()
    private var firstFailureLogged = false

    fun shouldUnify(view: View): Boolean = permitted(view) && selected(view) && !GlassSurfaces.excluded(view) && !GlassSurfaces.headsUp(view) &&
        (GlassSurfaces.media(view) || GlassSurfaces.focus(view))

    private fun active(view: View): Boolean = !GlassSurfaces.excluded(view) && permitted(view) &&
        (tuning() || shouldUnify(view))

    fun normalRecipe(view: View): FloatArray? {
        val resource = runCatching {
            val local = view.resources
            if (local.getIdentifier("notification_glass_params_normal", "array", "com.android.systemui") != 0) local
            else view.context.createPackageContext("com.android.systemui", 0).resources
        }.getOrNull() ?: return null
        val configuration = resource.configuration.hashCode()
        synchronized(recipes) { recipes[resource]?.takeIf { it.configuration == configuration }?.let { return it.values.copyOf() } }
        val value = runCatching {
            val id = resource.getIdentifier("notification_glass_params_normal", "array", "com.android.systemui")
            if (id == 0) return@runCatching null
            resource.getStringArray(id).map { it.toFloatOrNull() ?: return@runCatching null }.toFloatArray().takeIf(::hasSoftGlassRecipe)
        }.getOrNull() ?: return null
        synchronized(recipes) { recipes[resource] = Recipe(configuration, value.copyOf()) }
        return value
    }

    fun normalBlends(view: View): ArrayList<Point>? {
        if (!shouldUnify(view)) return null
        return runCatching {
            val resources = view.context.createPackageContext("com.android.systemui", 0).resources
            ArrayList((1..2).map { index ->
                val color = resources.getIdentifier("notification_element_blend_shade_color_$index", "color", "com.android.systemui")
                val mode = resources.getIdentifier("notification_element_blend_shade_mode_$index", "integer", "com.android.systemui")
                check(color != 0 && mode != 0)
                Point(resources.getColor(color, null), resources.getInteger(mode))
            })
        }.getOrNull()
    }

    fun installEffects(loader: ClassLoader) {
        if (installedLoaders.put(loader, true) != null) return
        native.install(loader)
        val focus = listOf("FocusNotificationNormalEffect", "FocusNotificationNormalCustomBgEffect", "FocusNotificationBlurEffect", "FocusNotificationBlurOnKeyguardEffect", "FocusNotificationGlassEffect", "FocusNotificationGlassCustomBgEffect", "FocusNotificationGlassOnKeyguardEffect", "FocusNotificationGlassOnKeyguardLightWallPaperEffect", "FocusNotificationGlassFullAodEffect")
        val media = listOf("MediaViewNormalEffect", "MediaViewBlurEffect", "MediaViewBlurOnKeyguardEffect", "MediaViewGlassEffect", "MediaViewGlassOnKeyguardEffect", "MediaViewGlassOnKeyguardLightWallPaperEffect", "MediaViewGlassFullAodEffect")
        var found = 0
        (focus + media).forEach { name ->
            val type = runCatching { loader.loadClass(effectPrefix + name) }.getOrNull() ?: return@forEach
            val method = type.declaredMethods.firstOrNull { it.name == "apply" && it.parameterCount == 2 && Context::class.java.isAssignableFrom(it.parameterTypes[1]) } ?: return@forEach
            runtime.hook(method, "lite-glass:effect:$name") { chain ->
                val root = chain.getArg(0) as? View
                // Mark before the OEM calls its setters: a detached FrameLayout is still
                // media, and must not temporarily inherit ordinary-notification permission.
                if (root != null && name in media) GlassSurfaces.markMediaRoot(root)
                if (root != null) NotificationMaterialWrites.observe(root, permitted(root))
                val result = chain.proceed()
                if (root == null || applying.get() == true) return@hook result
                synchronized(knownRoots) { knownRoots[root] = NotificationScope.token(root) }
                if (shouldUnify(root)) {
                    applyNormal(root, loader, name in media)
                    enqueue(root, { shouldUnify(root) }) { applyNormal(root, loader, name in media) }
                } else synchronized(forcedRoots) { forcedRoots.remove(root) }
                result
            }
            found++
        }
        val injector = runCatching { loader.loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector") }.getOrNull()
        injector?.declaredMethods?.filter { it.name == "updateFullAodAnimState" }?.forEachIndexed { index, method ->
            runtime.hook(method, "lite-glass:full-aod-$index") { chain ->
                val result = chain.proceed()
                val view = field(chain.thisObject, "view") as? View
                if (view != null && shouldUnify(view)) applyNormal(view, loader, false)
                result
            }
        }
        runtime.log("Native notification material effects installed: $found")
    }

    /** Invalidate late work, then let today's binder choose its keyguard/AOD/theme effect. */
    fun refresh(sceneOnly: Boolean = false) {
        if (!sceneOnly) generation.invalidate()
        synchronized(pending) {
            val stale = pending.filter { (view, task) -> !sceneOnly || !NotificationScope.isCurrent(view, task.scope) }
            stale.forEach { (view, task) -> view.removeCallbacks(task.job); pending.remove(view) }
        }
        val previous = synchronized(forcedRoots) {
            forcedRoots.filter { (view, ownership) -> !sceneOnly || !ownership.scopeCurrent(view) }
        }
        previous.forEach { (view, ownership) ->
            val allowed = shouldUnify(view)
            val stillOwned = ownership.stillOwned()
            val transferred = if (!allowed && stillOwned) {
                val reference = WeakReference(view)
                requestRefresh(view, true) { reference.get()?.let { !shouldUnify(it) && ownership.stillOwned() } == true }
            } else false
            if (canForgetMaterialState(allowed, stillOwned, transferred)) synchronized(forcedRoots) {
                if (forcedRoots[view] === ownership) forcedRoots.remove(view)
            }
        }
        // The OEM binder has seen ordinary rows and media even while our tuning
        // was off. Re-enter its current selector on first enable as well; there
        // may be no setter baseline or focus/media effect callback to replay.
        val candidates = (synchronized(knownRoots) { knownRoots.keys.toList() } + native.roots())
            .mapNotNull(native::root).distinct()
        val roots = synchronized(knownRoots) {
            candidates.filter { view ->
                val token = knownRoots[view]
                !sceneOnly || token == null || !NotificationScope.isCurrent(view, token)
            }.also {
                it.forEach { view -> knownRoots[view] = NotificationScope.token(view) }
            }
        }
        roots.filter(::active).forEach { requestRefresh(it, false) }
        // Cancellation of an old queue generation must not lose an unfinished release.
        releases.snapshot().forEach { (view, guard) -> scheduleRelease(view, guard) }
    }

    fun requestRefresh(view: View, releasing: Boolean, ownership: () -> Boolean = { true }): Boolean {
        val root = native.root(view) ?: return false
        if (releasing) {
            releases.offer(root, ownership)
            scheduleRelease(root, ownership)
            return true
        }
        if (!active(root)) return false
        enqueue(root, { ownership() && active(root) }) {
            if (!refreshNative(root)) logOnce("Current notification binder unavailable; waiting for the next OEM material update", null)
        }
        return true
    }

    private fun scheduleRelease(root: View, guard: () -> Boolean) {
        if (!guard()) { releases.discard(root, guard); return }
        enqueue(root, { true }) {
            // Remove only this delivery. A newer owner/setting can have supplied another.
            if (releases.take(root, guard) && !refreshNative(root)) logOnce("Current notification release deferred to the next OEM update", null)
        }
    }

    /** Both effect discovery and delayed setter refresh can request this same root. */
    private fun refreshNative(root: View): Boolean {
        val stamp = refreshedRoots.claim(root, generation.value, NotificationScope.token(root)) ?: return true
        var requested = false
        return try { native.refresh(root).also { requested = it } }
        finally { if (!requested) refreshedRoots.failed(root, stamp) }
    }

    private fun enqueue(view: View, allowed: () -> Boolean, action: () -> Unit) {
        val ticket = generation.value
        val scene = NotificationScope.token(view)
        lateinit var job: Runnable
        job = Runnable {
            synchronized(pending) { if (pending[view]?.job !== job) return@Runnable; pending.remove(view) }
            runCatching {
                if (view.isAttachedToWindow && generation.current(ticket, allowed() && NotificationScope.isCurrent(view, scene))) action()
            }.onFailure { logOnce("Deferred notification material update cancelled", it) }
        }
        synchronized(pending) {
            pending.remove(view)?.let { view.removeCallbacks(it.job) }
            pending[view] = Pending(job, scene)
        }
        if (!view.post(job)) synchronized(pending) { if (pending[view]?.job === job) pending.remove(view) }
    }

    private fun applyNormal(view: View, loader: ClassLoader, media: Boolean) {
        if (applying.get() == true || !shouldUnify(view)) return
        val ticket = generation.value
        val scene = NotificationScope.token(view)
        applying.set(true)
        try {
            val name = if (media) "MediaViewGlassEffect" else "NotificationRowGlassEffect"
            val type = loader.loadClass(effectPrefix + name)
            val singleton = type.declaredFields.firstOrNull { it.name == "INSTANCE" }?.apply { isAccessible = true }?.get(null) ?: return
            val apply = type.methods.firstOrNull { it.name == "apply" && it.parameterCount == 2 } ?: return
            if (!generation.current(ticket, NotificationScope.isCurrent(view, scene) && shouldUnify(view))) return
            apply.invoke(singleton, view, view.context)
            synchronized(forcedRoots) { forcedRoots[view] = MaterialOwnership(view) }
        } catch (error: Throwable) { logOnce("Could not apply normal notification material", error) }
        finally { applying.remove() }
    }

    private fun logOnce(message: String, error: Throwable?) {
        if (!firstFailureLogged) { firstFailureLogged = true; runtime.log(message, error) }
    }

    private fun field(instance: Any?, name: String): Any? {
        var type = instance?.javaClass
        while (type != null) {
            val current = type
            runCatching { current.getDeclaredField(name).apply { isAccessible = true }.get(instance) }.getOrNull()?.let { return it }
            type = type.superclass
        }
        return null
    }
}

/** First enable needs no old setter baseline; duplicate entry paths share one request. */
internal class NativeMaterialRefreshes<K : Any, S> {
    data class Stamp<S>(val configuration: Long, val scene: S)
    private val requests = WeakHashMap<K, Stamp<S>>()

    @Synchronized fun claim(root: K, configuration: Long, scene: S): Stamp<S>? {
        val stamp = Stamp(configuration, scene)
        if (requests[root] == stamp) return null
        requests[root] = stamp
        return stamp
    }

    @Synchronized fun failed(root: K, stamp: Stamp<S>) {
        if (requests[root] == stamp) requests.remove(root)
    }
}

/** A replacement background or a new transition alpha means this module must yield. */
private class MaterialOwnership(root: View) {
    private val ticket = NotificationMaterialWrites.ticket(root)
    private val scope = NotificationScope.token(root)
    fun scopeCurrent(view: View): Boolean = NotificationScope.isCurrent(view, scope)
    private class Property(view: View) {
        val reference = WeakReference(view)
        val background = WeakReference(view.background)
        val hadBackground = view.background != null
        val alpha = view.alpha
        val children = (view as? ViewGroup)?.let { group ->
            (0 until group.childCount).map { WeakReference(group.getChildAt(it)) }
        }
        fun unchanged(): Boolean = reference.get()?.let {
            (if (hadBackground) background.get() != null && background.get() === it.background else it.background == null) &&
                it.alpha == alpha && (if (it is ViewGroup) children?.let { saved ->
                    it.childCount == saved.size && saved.indices.all { index -> saved[index].get() === it.getChildAt(index) }
                } == true else children == null)
        } == true
    }
    private val properties = buildList {
        fun visit(view: View, depth: Int) {
            if (depth > 12 || size >= 128) return
            add(Property(view))
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index), depth + 1)
        }
        visit(root, 0)
    }
    fun stillOwned(): Boolean = NotificationMaterialWrites.current(ticket) && properties.all { it.unchanged() }
}
