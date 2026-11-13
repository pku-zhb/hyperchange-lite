// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger's native View material setters, baseline refresh and blend guards.
package com.zhuhuibin.hyperchange.lite.glass

import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import com.zhuhuibin.hyperchange.lite.scope.NotificationScopeSnapshot
import com.zhuhuibin.hyperchange.lite.scope.NotificationScopeToken
import com.zhuhuibin.hyperchange.lite.scope.ShadeScope
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** One engine per process. Every retained setting can be replayed or restored without restart. */
internal class ViewMaterialEngine(
    private val runtime: HookRuntime,
    private val accepts: (View) -> Boolean,
    private val settingsFor: (View) -> SoftGlassSettings,
    private val unifiedRecipe: (View) -> FloatArray? = { null },
    private val unifiedBlends: (View) -> ArrayList<Point>? = { null },
    private val contextBrightness: (View) -> Float = { 0f },
    private val scopeAllows: (View) -> Boolean = { true },
    // Notification effects must be selected from the current OEM state, never from
    // a baseline saved on the previous keyguard/AOD/theme or another owner's view.
    private val nativeNotificationRefresh: ((View, Boolean, () -> Boolean) -> Boolean)? = null,
) {
    private val baselines = WeakHashMap<View, MutableMap<Method, Array<Any?>>>()
    private val arrayPairs = WeakHashMap<View, Pair<FloatArray, FloatArray>>()
    private val legacyBlends = WeakHashMap<View, ArrayList<Point>>()
    private class Ownership(view: View) {
        private val background = WeakReference(view.background)
        private val hadBackground = view.background != null
        private val alpha = view.alpha
        private val ticket = NotificationMaterialWrites.ticket(view)
        private val root = WeakReference(GlassSurfaces.materialRoot(view))
        private val rootBackground = WeakReference(root.get()?.background)
        private val rootHadBackground = root.get()?.background != null
        private val rootAlpha = root.get()?.alpha
        private val rootChildren = (root.get() as? ViewGroup)?.let { group ->
            (0 until group.childCount).map { WeakReference(group.getChildAt(it)) }
        }
        fun current(view: View): Boolean = NotificationMaterialWrites.current(ticket) &&
            (if (hadBackground) background.get() != null && background.get() === view.background else view.background == null) && alpha == view.alpha &&
            root.get()?.let {
                (if (rootHadBackground) rootBackground.get() != null && rootBackground.get() === it.background else it.background == null) &&
                    it.alpha == rootAlpha && (if (it is ViewGroup) rootChildren?.let { children ->
                        it.childCount == children.size && children.indices.all { index -> children[index].get() === it.getChildAt(index) }
                    } == true else rootChildren == null)
            } == true
    }
    private val owned = WeakHashMap<View, Ownership>()
    private val scopeTokens = WeakHashMap<View, NotificationScopeToken>()
    private val backdropGenerations = WeakHashMap<View, Long>()
    private val generation = MaterialGeneration()
    private val refreshing = ThreadLocal<Boolean>()
    private val backdropReplay = BackdropReplayGuard { ShadeScope.generation }
    private val blendDispatch = ThreadLocal<Boolean>()
    private val bypassView = ThreadLocal<View>()
    private val handler = Handler(Looper.getMainLooper())
    private var clearBlend: Method? = null
    private var addBlend: Method? = null
    private var refresh: Runnable? = null
    private val refreshPlan = MaterialRefreshPlan()
    private class Prepared(val input: Array<Any?>, val args: Array<Any?>, val ticket: Long,
                           val scene: NotificationScopeSnapshot?, val parent: Any?, val backdropRevision: Long?)

    /** No fact or permission memo survives a native call; heads-up state can change there. */
    private fun <T> prepare(view: View, action: (GlassSurface?, NotificationScopeSnapshot?) -> T): T =
        GlassSurfaces.withFacts {
            val surface = GlassSurfaces.classify(view)
            val scene = if (surface == GlassSurface.NOTIFICATION) NotificationScope.capture(view) else null
            if (scene == null) action(surface, null)
            else NotificationScope.withSnapshot(view, scene) { action(surface, scene) }
        }

    private fun isBackdrop(view: View): Boolean =
        GlassSurfaces.isShadeWindow(view) || GlassSurfaces.classify(view) == GlassSurface.SHADE

    private fun backdropCurrent(view: View, revision: Long?): Boolean =
        revision == null || (isBackdrop(view) && scopeAllows(view) && revision == ShadeScope.generation)

    private fun skipBackdropReplay(view: View): Boolean {
        val recorded = synchronized(baselines) { backdropGenerations[view] }
        val backdrop = isBackdrop(view)
        if (!backdrop && recorded == null) return false
        if (refreshing.get() == true && recorded != null && recorded != ShadeScope.generation) return true
        return backdropReplay.shouldSkip(backdrop && scopeAllows(view))
    }

    private fun ready(view: View, value: Prepared): Boolean = generation.current(value.ticket,
        value.parent === view.parent && backdropCurrent(view, value.backdropRevision) &&
            (value.scene == null || value.scene.revision == NotificationScope.generation))

    private fun current(view: View, value: Prepared): Boolean = generation.current(value.ticket,
        value.parent === view.parent && backdropCurrent(view, value.backdropRevision) &&
            (value.scene == null || NotificationScope.stillCurrent(view, value.scene)))

    /** Closing a panel drops its historical inputs, without writing into the native window. */
    fun retireInactiveBackdrops() {
        val views = synchronized(baselines) {
            (baselines.keys.toList() + legacyBlends.keys.toList() + owned.keys.toList() + backdropGenerations.keys.toList()).distinct()
        }
        views.forEach { view ->
            val recorded = synchronized(baselines) { backdropGenerations[view] }
            val backdrop = isBackdrop(view)
            if ((backdrop || recorded != null) &&
                (!backdrop || !scopeAllows(view) || recorded == null || recorded != ShadeScope.generation)) forget(view)
        }
    }

    fun install() {
        setter("setMiGlass", FloatArray::class.java)
        setter("setMiGlassBlurRadius", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
        setter("setMiBackgroundBlurRadius", Int::class.javaPrimitiveType!!)
        setter("setMiBackgroundBlurScaleRatio", Float::class.javaPrimitiveType!!)
        setter("setMiBackgroundBlendColors", ArrayList::class.java)
        setter("setMiSelfBlur", Int::class.javaPrimitiveType!!, ArrayList::class.java)
        // A background color is a material component only on dedicated shade surfaces.
        setter("setBackgroundColor", Int::class.javaPrimitiveType!!)
        installLegacyBlend()
    }

    @Synchronized fun scheduleRefresh(sceneOnly: Boolean = false, cards: Boolean = true, backdrops: Boolean = true) {
        refreshPlan.include(sceneOnly, cards, backdrops)
        val ticket = generation.invalidate()
        refresh?.let(handler::removeCallbacks)
        refresh = Runnable {
            if (generation.current(ticket, true)) {
                val request = synchronized(this) { refreshPlan.take() }
                runCatching { replay(ticket, request) }.onFailure { runtime.log("Material refresh cancelled after a runtime failure", it) }
            }
        }.also { handler.postDelayed(it, 16L) }
    }

    fun <T> bypass(view: View, action: () -> T): T {
        val previous = bypassView.get()
        bypassView.set(view)
        return try { action() } finally { if (previous == null) bypassView.remove() else bypassView.set(previous) }
    }

    private fun setter(name: String, vararg types: Class<*>) {
        runCatching {
            val method = View::class.java.getMethod(name, *types)
            runtime.hook(method, "lite-glass:$name") { chain ->
                val view = chain.thisObject as? View ?: return@hook chain.proceed()
                if (bypassView.get() === view) return@hook chain.proceed()
                // A replay invokes a native setter with a saved argument. If the
                // panel closed in a nested callback, passing that argument through
                // would itself resurrect the old whole-window blur.
                if (skipBackdropReplay(view)) { forget(view); return@hook null }
                val prepared = prepare(view) { surface, scene ->
                    val windowSelfBlur = name == "setMiSelfBlur" && GlassSurfaces.isShadeWindow(view)
                    if (!accepts(view) && !windowSelfBlur) return@prepare null
                    val allowed = scopeAllows(view)
                    if (surface == GlassSurface.NOTIFICATION) NotificationMaterialWrites.observe(view, allowed)
                    if (name == "setMiSelfBlur" && !windowSelfBlur && surface != GlassSurface.SHADE) return@prepare null
                    if (name == "setBackgroundColor" && surface != GlassSurface.SHADE) {
                        // A different owner's plain background supersedes our old material.
                        if (surface == GlassSurface.NOTIFICATION) { NotificationMaterialWrites.touch(view); forget(view) }
                        return@prepare null
                    }
                    if (!allowed) { forget(view); return@prepare null }
                    if ((name == "setMiBackgroundBlendColors" || name == "setMiSelfBlur") && blendDispatch.get() == true)
                        return@prepare null
                    val ticket = generation.value
                    val parent = view.parent
                    val input = Array<Any?>(types.size) { chain.getArg(it) }
                    val backdropRevision = if (isBackdrop(view)) ShadeScope.generation else null
                    Prepared(input, adjustPrepared(view, method, input, surface, scene, false), ticket, scene, parent, backdropRevision)
                } ?: return@hook if (skipBackdropReplay(view)) null else chain.proceed()
                if (!ready(view, prepared)) {
                    forget(view)
                    return@hook if (skipBackdropReplay(view)) null else chain.proceed()
                }
                val blending = name == "setMiBackgroundBlendColors" || name == "setMiSelfBlur"
                if (blending) blendDispatch.set(true)
                val result = try { chain.proceedWith(view, prepared.args) } finally { if (blending) blendDispatch.remove() }
                if (current(view, prepared))
                    synchronized(baselines) { if (!sameArgs(prepared.input, prepared.args) || owned.containsKey(view)) owned[view] = Ownership(view) }
                else forget(view)
                result
            }
        }.onFailure { runtime.log("OEM glass setter unavailable: $name", it) }
    }

    /** Also used for MAML's reflective writer so it is tuned exactly once. */
    fun adjustExternal(view: View, method: Method, input: Array<Any?>): Array<Any?> = prepare(view) { surface, scene ->
        if (!scopeAllows(view)) { forget(view); input }
        else adjustPrepared(view, method, input, surface, scene, true)
    }

    private fun adjustPrepared(view: View, method: Method, input: Array<Any?>, surface: GlassSurface?,
                               scene: NotificationScopeSnapshot?, recordOwnership: Boolean): Array<Any?> {
        val name = method.name
        var baseline = copyArgs(input)
        if (name == "setMiGlass") {
            val original = input.firstOrNull() as? FloatArray
            // A null recipe is a native clear just like an empty/zero recipe.
            // Retire the old source as well, or a later settings refresh can
            // replay glass that the current OEM state no longer has.
            if (original == null || !hasSoftGlassRecipe(original)) {
                if (refreshing.get() != true) synchronized(baselines) {
                    baselines.getOrPut(view) { linkedMapOf() }[method] = baseline
                    arrayPairs.remove(view)
                    owned.remove(view)
                    if (surface == GlassSurface.NOTIFICATION) NotificationMaterialWrites.touch(view)
                }
                return input
            }
            val previous = synchronized(baselines) { arrayPairs[view] }
            if (original === previous?.second) baseline = arrayOf(previous.first.copyOf())
        }
        if (refreshing.get() != true) synchronized(baselines) {
            if (isBackdrop(view)) backdropGenerations[view] = ShadeScope.generation
            baselines.getOrPut(view) { linkedMapOf() }[method] = baseline
            if (scene != null) scopeTokens[view] = scene.token
            if (name == "setMiBackgroundBlendColors") legacyBlends.remove(view)
        }
        val settings = settingsFor(view)
        val output: Array<Any?> = when (name) {
            "setMiGlass" -> {
                val native = baseline[0] as FloatArray
                // Whole-panel glass keeps its OEM optical token. Only the card
                // layer accepts global recipes, refraction and fourteen-slot tuning.
                if (isBackdrop(view)) arrayOf(native)
                else {
                    val selected = unifiedRecipe(view)?.takeIf(::hasSoftGlassRecipe) ?: native
                    arrayOf(if (settings.enabled) tuneSoftGlass(selected, settings, contextBrightness = contextBrightness(view)) else selected)
                }
            }
            "setMiGlassBlurRadius" -> arrayOf(
                if (settings.enabled && !isBackdrop(view)) softGlassBlur(baseline[0] as Int, settings.refractionBlur) else baseline[0],
                if (settings.enabled && !isBackdrop(view)) softGlassBlur(baseline[1] as Int, settings.refractionBlur) else baseline[1],
            )
            "setMiBackgroundBlurRadius" -> arrayOf(if (settings.enabled) softGlassBlur(baseline[0] as Int, settings.backgroundBlur) else baseline[0])
            "setMiBackgroundBlurScaleRatio" -> arrayOf(if (settings.enabled) softGlassScale(baseline[0] as Float, settings.backgroundScale) else baseline[0])
            "setMiBackgroundBlendColors" -> arrayOf(points(unifiedBlends(view) ?: baseline[0], opacity(view, settings)))
            "setMiSelfBlur" -> arrayOf(
                if (settings.enabled) softGlassBlur(baseline[0] as Int, settings.backgroundBlur) else baseline[0],
                points(baseline[1], if (settings.enabled) settings.backgroundOpacity else 100),
            )
            "setBackgroundColor" -> arrayOf(if (settings.enabled) softGlassColorAlpha(baseline[0] as Int, settings.backgroundOpacity) else baseline[0])
            else -> baseline
        }
        if (name == "setMiGlass") synchronized(baselines) { arrayPairs[view] = (baseline[0] as FloatArray) to (output[0] as FloatArray) }
        if (recordOwnership && !sameArgs(input, output)) synchronized(baselines) { owned[view] = Ownership(view) }
        return output
    }

    // Volume/heads-up native glass already owns its backdrop alpha; keep the sampler's
    // points intact to avoid multiplying the same tint twice (upstream behavior).
    private fun opacity(view: View, settings: SoftGlassSettings): Int =
        if (!settings.enabled || GlassSurfaces.classify(view) == GlassSurface.VOLUME || GlassSurfaces.headsUp(view)) 100
        else settings.backgroundOpacity

    private fun installLegacyBlend() {
        runCatching {
            clearBlend = View::class.java.getMethod("clearMiBackgroundBlendColor")
            addBlend = View::class.java.getMethod("addMiBackgroundBlendColor", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            runtime.hook(clearBlend!!, "lite-glass:legacy-clear") { chain ->
                val view = chain.thisObject as? View
                if (view != null && skipBackdropReplay(view)) { forget(view); return@hook null }
                if (view != null) prepare(view) { surface, _ ->
                    val allowed = scopeAllows(view)
                    if (surface == GlassSurface.NOTIFICATION) NotificationMaterialWrites.observe(view, allowed)
                    if (!allowed && isBackdrop(view)) forget(view)
                    if (refreshing.get() != true && blendDispatch.get() != true) synchronized(baselines) {
                        legacyBlends.remove(view)
                        if (!allowed) owned.remove(view)
                        baselines[view]?.keys?.removeAll { it.name == "setMiBackgroundBlendColors" }
                    }
                }
                chain.proceed()
            }
            runtime.hook(addBlend!!, "lite-glass:legacy-add") { chain ->
                val view = chain.thisObject as? View ?: return@hook chain.proceed()
                if (skipBackdropReplay(view)) { forget(view); return@hook null }
                val prepared = prepare(view) { surface, scene ->
                    val allowed = scopeAllows(view)
                    if (surface == GlassSurface.NOTIFICATION) NotificationMaterialWrites.observe(view, allowed)
                    if (bypassView.get() === view || blendDispatch.get() == true || !accepts(view)) return@prepare null
                    if (!allowed) { forget(view); return@prepare null }
                    val ticket = generation.value
                    val parent = view.parent
                    val color = chain.getArg(0) as Int
                    val mode = chain.getArg(1)
                    if (refreshing.get() != true) synchronized(baselines) {
                        if (isBackdrop(view)) backdropGenerations[view] = ShadeScope.generation
                        legacyBlends.getOrPut(view) { arrayListOf() }.add(Point(color, mode as Int))
                        if (scene != null) scopeTokens[view] = scene.token
                    }
                    val settings = settingsFor(view)
                    val tuned = softGlassColorAlpha(color, opacity(view, settings))
                    Prepared(arrayOf(color, mode), arrayOf(tuned, mode), ticket, scene, parent,
                        if (isBackdrop(view)) ShadeScope.generation else null)
                } ?: return@hook if (skipBackdropReplay(view)) null else chain.proceed()
                if (!ready(view, prepared)) {
                    forget(view)
                    return@hook if (skipBackdropReplay(view)) null else chain.proceed()
                }
                val result = chain.proceedWith(view, prepared.args)
                if (current(view, prepared))
                    synchronized(baselines) { if (!sameArgs(prepared.input, prepared.args) || owned.containsKey(view)) owned[view] = Ownership(view) }
                else forget(view)
                result
            }
        }.onFailure { runtime.log("OEM legacy blend API unavailable", it) }
    }

    private fun replay(ticket: Long, request: MaterialRefreshPlan.Request) = backdropReplay.duringReplay {
        retireInactiveBackdrops()
        val (surfaces, legacy) = synchronized(baselines) {
            baselines.map { (view, setters) -> view to setters.toMap() } to
                legacyBlends.map { it.key to it.value.map { p -> Point(p.x, p.y) } }
        }
        refreshing.set(true)
        try {
            val notificationViews = (surfaces.map { it.first } + legacy.map { it.first }).distinct()
                .filter { nativeNotificationRefresh != null && GlassSurfaces.classify(it) == GlassSurface.NOTIFICATION }
            notificationViews.forEach { view ->
                if (!request.notifications) return@forEach
                if (!generation.current(ticket, true)) return@duringReplay
                val token = synchronized(baselines) { scopeTokens[view] }
                if (!request.cards && token != null && NotificationScope.isCurrent(view, token)) return@forEach
                val wasOwned = synchronized(baselines) { owned[view] }
                val allowed = scopeAllows(view)
                val stillOwned = wasOwned?.current(view) == true
                // Keep the current ownership while an admitted OEM request is queued:
                // disabling before that queue drains must still find something to release.
                val transferred = if (view.isAttachedToWindow && (allowed || stillOwned)) {
                    val reference = WeakReference(view)
                    nativeNotificationRefresh?.invoke(view, !allowed) {
                        reference.get()?.let { current -> allowed || (!scopeAllows(current) && wasOwned?.current(current) == true) } == true
                    } == true
                } else false
                if (canForgetMaterialState(allowed, stillOwned, transferred)) forget(view)
            }
            surfaces.forEach { (view, setters) ->
                if (view in notificationViews || (if (isBackdrop(view)) !request.backdrops else !request.cards)) return@forEach
                if (!scopeAllows(view)) { forget(view); return@forEach }
                if (view.isAttachedToWindow) setters.forEach { (method, args) ->
                    if (!generation.current(ticket, scopeAllows(view))) return@forEach
                    runCatching { method.invoke(view, *copyArgs(args)) }
                        .onFailure { runtime.log("Could not refresh ${method.name}", it) }
                }
            }
            legacy.forEach { (view, colors) ->
                if (view in notificationViews || (if (isBackdrop(view)) !request.backdrops else !request.cards)) return@forEach
                if (!scopeAllows(view)) { forget(view); return@forEach }
                if (view.isAttachedToWindow && generation.current(ticket, scopeAllows(view))) runCatching {
                    clearBlend?.invoke(view)
                    colors.forEach { p ->
                        if (generation.current(ticket, scopeAllows(view))) addBlend?.invoke(view, p.x, p.y)
                    }
                }
            }
        } finally { refreshing.remove() }
    }

    private fun forget(view: View) = synchronized(baselines) {
        baselines.remove(view); arrayPairs.remove(view); legacyBlends.remove(view); owned.remove(view); scopeTokens.remove(view)
        backdropGenerations.remove(view)
    }

    private fun sameArgs(left: Array<Any?>, right: Array<Any?>): Boolean =
        left.size == right.size && left.indices.all { index ->
            val a = left[index]; val b = right[index]
            when {
                a is FloatArray && b is FloatArray -> a.contentEquals(b)
                a is List<*> && b is List<*> -> a == b
                else -> a == b
            }
        }

    private fun copyArgs(args: Array<Any?>): Array<Any?> = args.map {
        when (it) {
            is FloatArray -> it.copyOf()
            is ArrayList<*> -> points(it, 100)
            else -> it
        }
    }.toTypedArray()

    private fun points(input: Any?, percent: Int): ArrayList<Point> = ArrayList(
        (input as? List<*>)?.filterIsInstance<Point>()?.map { Point(softGlassColorAlpha(it.x, percent), it.y) } ?: emptyList()
    )
}
