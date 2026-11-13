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
    private val headsUpSelected: (View) -> Boolean = { false },
    private val selected: (View) -> Boolean,
) {
    private data class Recipe(val configuration: Int, val values: FloatArray)
    private val recipes = WeakHashMap<Resources, Recipe>()
    private val applying = ThreadLocal<Boolean>()
    private val knownRoots = WeakHashMap<View, NotificationScopeToken>()
    private val forcedRoots = WeakHashMap<View, MaterialOwnership>()
    private val convertedHeadsUpRoots = WeakHashMap<View, MaterialOwnership>()
    private class LockscreenConversion(val effect: String, root: View, target: View) {
        private val ticket = NotificationMaterialWrites.ticket(root)
        private val background = WeakReference(target)
        fun matches(target: View?): Boolean = target != null && background.get() === target
        fun stillOwned(target: View?): Boolean = matches(target) && NotificationMaterialWrites.current(ticket)
    }
    private val convertedLockscreenRoots = WeakHashMap<View, LockscreenConversion>()
    private val ordinaryDepth = ThreadLocal<Int>()
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

    fun normalRecipe(view: View): FloatArray? = normalRecipe(view.context)

    private fun normalRecipe(context: Context): FloatArray? {
        val resource = runCatching {
            val local = context.resources
            if (local.getIdentifier("notification_glass_params_normal", "array", "com.android.systemui") != 0) local
            else context.createPackageContext("com.android.systemui", 0).resources
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
        installHeadsUpEffects(loader)
        installLockscreenEffects(loader)
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

    private fun lockscreenSelected(view: View): Boolean = tuning() && permitted(view) &&
        NotificationScope.confirmedLitKeyguard() && !GlassSurfaces.excluded(view) &&
        !GlassSurfaces.media(view) && !GlassSurfaces.headsUp(view) && !GlassSurfaces.focus(view)

    /** Ordinary keyguard blur/normal effects have no glass token for the setter tuner to adjust. */
    private fun installLockscreenEffects(loader: ClassLoader) {
        val plain = setOf("NotificationRowNormalEffect", "NotificationRowBlurEffect", "NotificationRowBlurOnKeyguardEffect")
        val names = plain + setOf("NotificationRowGlassEffect", "NotificationRowGlassOnKeyguardEffect",
            "NotificationRowGlassOnKeyguardLightWallPaperEffect", "NotificationRowGlassFullAodEffect")
        names.forEach { name ->
            val type = runCatching { loader.loadClass(effectPrefix + name) }.getOrNull() ?: return@forEach
            type.declaredMethods.filter { it.name == "apply" && it.parameterCount == 2 &&
                it.parameterTypes[1] == Context::class.java }.forEachIndexed { index, method ->
                runtime.hook(method, "lite-material:lockscreen:$name:$index") { chain ->
                    val root = chain.getArg(0) as? View
                    val context = chain.getArg(1) as? Context
                    if (root == null || context == null || applying.get() == true || ordinaryDepth.get() == 1)
                        return@hook chain.proceed()
                    ordinaryDepth.set(1)
                    try {
                        retireLockscreenConversion(root, loader)
                        val result = chain.proceed()
                        synchronized(knownRoots) { knownRoots[root] = NotificationScope.token(root) }
                        if (name in plain && lockscreenSelected(root)) {
                            val effect = native.lockscreenGlassEffect(root)
                            if (effect != null) applyLockscreenGlass(root, context, loader, effect)
                        }
                        result
                    } finally { ordinaryDepth.remove() }
                }
            }
            type.declaredMethods.filter { it.name == "clear" && it.parameterCount == 1 }.forEachIndexed { index, method ->
                runtime.hook(method, "lite-material:lockscreen-clear:$name:$index") { chain ->
                    val root = chain.getArg(0) as? View
                    if (root != null && applying.get() != true) retireLockscreenConversion(root, loader)
                    chain.proceed()
                }
            }
        }
    }

    private fun effect(loader: ClassLoader, name: String): Any = requireNotNull(loader.loadClass(effectPrefix + name)
        .getField("INSTANCE").get(null))

    private fun applyLockscreenGlass(root: View, context: Context, loader: ClassLoader, name: String) {
        val ticket = generation.value
        val scene = NotificationScope.token(root)
        runCatching {
            val current = effect(loader, name)
            val apply = current.javaClass.methods.single { it.name == "apply" && it.parameterCount == 2 }
            if (materialTarget(root) == null) return
            if (!generation.current(ticket, NotificationScope.isCurrent(root, scene) && lockscreenSelected(root)) ||
                native.lockscreenGlassEffect(root) != name) return
            applying.set(true)
            try {
                apply.invoke(current, root, context)
                materialTarget(root)?.let { target -> synchronized(convertedLockscreenRoots) {
                    convertedLockscreenRoots[root] = LockscreenConversion(name, root, target)
                } }
            } finally { applying.remove() }
        }.onFailure { logOnce("Current keyguard glass effect unavailable; native row retained", it) }
    }

    private fun retireLockscreenConversion(root: View, loader: ClassLoader) {
        val previous = synchronized(convertedLockscreenRoots) { convertedLockscreenRoots[root] } ?: return
        // Clear only this conversion, before today's native selector applies its new effect.
        val target = materialTarget(root)
        if (!previous.matches(target) || !previous.stillOwned(target) && !native.selecting(root)) {
            synchronized(convertedLockscreenRoots) { convertedLockscreenRoots.remove(root, previous) }
            return
        }
        val cleared = runCatching {
            val current = effect(loader, previous.effect)
            val clear = current.javaClass.methods.single { it.name == "clear" && it.parameterCount == 1 }
            applying.set(true)
            try { clear.invoke(current, root) } finally { applying.remove() }
        }.onFailure { logOnce("Keyguard glass cleanup deferred to the next native effect", it) }.isSuccess
        if (cleared) synchronized(convertedLockscreenRoots) { convertedLockscreenRoots.remove(root, previous) }
    }

    private fun materialTarget(root: View): View? = runCatching {
        val injector = root.javaClass.getMethod("getInjector").invoke(root) ?: return@runCatching null
        (injector.javaClass.getMethod("getBackgroundNormal").invoke(injector) as? View)
            ?.takeUnless(GlassSurfaces::excluded)
    }.getOrNull()

    private fun headsUpAllowed(view: View): Boolean = headsUpSelected(view) && permitted(view) &&
        !GlassSurfaces.excluded(view) && !GlassSurfaces.media(view) && NotificationScope.confirmedUnlocked()

    /** Original HC's opt-in path: normal row blur/blends plus its normal glass resource. */
    private fun installHeadsUpEffects(loader: ClassLoader) = runCatching {
        val blur = loader.loadClass(effectPrefix + "NotificationRowBlurEffect")
        val singleton = blur.getField("INSTANCE").get(null)
        val apply = blur.declaredMethods.filter { it.name == "apply" && it.parameterCount == 2 &&
            it.parameterTypes[1] == Context::class.java }.minByOrNull { if (it.parameterTypes[0] == Any::class.java) 1 else 0 }
            ?.apply { isAccessible = true } ?: return@runCatching
        val clear = blur.declaredMethods.filter { it.name == "clear" && it.parameterCount == 1 }
            .minByOrNull { if (it.parameterTypes[0] == Any::class.java) 1 else 0 }
            ?.apply { isAccessible = true } ?: return@runCatching
        val compat = loader.loadClass("com.miui.systemui.util.MiGlassCompat")
        val glass = compat.getMethod("setMiGlassCompat", View::class.java, FloatArray::class.java)
        val material = compat.getMethod("setMiViewMaterialTypeCompat", Int::class.javaPrimitiveType, View::class.java)
        fun dispatch(root: View, context: Context, clearing: Boolean): Boolean {
            // A converted plain-blur effect has no native glass-clear step. Retire
            // our token with the exact zero recipe used by the OEM HU glass clear,
            // even if a later resource/theme no longer exposes the normal recipe.
            val recipe = if (clearing) FloatArray(42) else normalRecipe(context) ?: return false
            val ticket = generation.value
            val scene = NotificationScope.token(root)
            applying.set(true)
            return try {
                if (clearing) clear.invoke(singleton, root) else apply.invoke(singleton, root, context)
                if (!clearing && !generation.current(ticket,
                        headsUpAllowed(root) && NotificationScope.isCurrent(root, scene))) return false
                // The native blur effect updates the row's background first. Resolve
                // its current target afterwards, exactly as the upstream dispatch does.
                val injector = root.javaClass.getMethod("getInjector").invoke(root) ?: return false
                val background = injector.javaClass.getMethod("getBackgroundNormal").invoke(injector) as? View ?: return false
                glass.invoke(null, background, recipe)
                material.invoke(null, if (clearing) 0 else 1, background)
                true
            } finally { applying.remove() }
        }
        listOf("HeadsUpNotificationBlurEffect", "HeadsUpNotificationGlassEffect", "HeadsUpNotificationGlassDarkEffect").forEach { name ->
            val effect = loader.loadClass(effectPrefix + name)
            effect.declaredMethods.filter { it.name == "apply" && it.parameterCount == 2 &&
                it.parameterTypes[1] == Context::class.java }.forEachIndexed { index, method ->
                runtime.hook(method, "lite-material:heads-up:$name:$index") { chain ->
                    val root = chain.getArg(0) as? View
                    val context = chain.getArg(1) as? Context
                    if (root == null || context == null || applying.get() == true) return@hook chain.proceed()
                    synchronized(knownRoots) { knownRoots[root] = NotificationScope.token(root) }
                    if (headsUpAllowed(root)) {
                        val converted = runCatching { dispatch(root, context, false) }
                            .onFailure { logOnce("Heads-up glass conversion retained the native effect", it) }.getOrDefault(false)
                        if (converted) {
                            synchronized(convertedHeadsUpRoots) { convertedHeadsUpRoots[root] = MaterialOwnership(root) }
                            return@hook null
                        }
                    }
                    synchronized(convertedHeadsUpRoots) { convertedHeadsUpRoots.remove(root) }
                    chain.proceed()
                }
            }
            effect.declaredMethods.filter { it.name == "clear" && it.parameterCount == 1 }.forEachIndexed { index, method ->
                runtime.hook(method, "lite-material:heads-up-clear:$name:$index") { chain ->
                    val root = chain.getArg(0) as? View
                    if (root == null || applying.get() == true) return@hook chain.proceed()
                    val ownership = synchronized(convertedHeadsUpRoots) { convertedHeadsUpRoots.remove(root) }
                    // The current OEM selector clears its previous effect before applying
                    // the next one. A stale clear outside that exact root scope cannot
                    // clear a newer owner merely because its background object is reused.
                    if (ownership != null && (ownership.stillOwned() || native.selecting(root)) &&
                        runCatching { dispatch(root, root.context, true) }.getOrDefault(false)) null
                    else chain.proceed()
                }
            }
        }
    }.onFailure { logOnce("Heads-up glass contract unavailable; native banner retained", it) }.let { Unit }

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
        val headsUp = synchronized(convertedHeadsUpRoots) {
            convertedHeadsUpRoots.filter { (view, ownership) -> !sceneOnly || !ownership.scopeCurrent(view) }
        }
        headsUp.forEach { (view, ownership) ->
            val allowed = headsUpAllowed(view)
            val stillOwned = ownership.stillOwned()
            val transferred = if (!allowed && stillOwned) {
                val reference = WeakReference(view)
                requestRefresh(view, true) { reference.get()?.let { !headsUpAllowed(it) && ownership.stillOwned() } == true }
            } else false
            if (canForgetMaterialState(allowed, stillOwned, transferred)) synchronized(convertedHeadsUpRoots) {
                if (convertedHeadsUpRoots[view] === ownership) convertedHeadsUpRoots.remove(view)
            }
        }
        val lockscreen = synchronized(convertedLockscreenRoots) { convertedLockscreenRoots.toMap() }
        lockscreen.forEach { (view, conversion) ->
            if (!lockscreenSelected(view) && conversion.stillOwned(materialTarget(view))) {
                val reference = WeakReference(view)
                requestRefresh(view, true) { reference.get()?.let {
                    !lockscreenSelected(it) && conversion.stillOwned(materialTarget(it))
                } == true }
                // Keep the clear record until that current selector really applies an effect.
                // AOD transition deferral or queue cancellation is not a completed handoff.
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
