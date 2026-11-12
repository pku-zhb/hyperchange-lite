// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger's native View material setters, baseline refresh and blend guards.
package com.zhuhuibin.hyperchange.lite.glass

import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import java.lang.reflect.Method
import java.util.WeakHashMap

/** One engine per process. Every retained setting can be replayed or restored without restart. */
internal class ViewMaterialEngine(
    private val runtime: HookRuntime,
    private val accepts: (View) -> Boolean,
    private val settingsFor: (View) -> SoftGlassSettings,
    private val unifiedRecipe: (View) -> FloatArray? = { null },
    private val unifiedBlends: (View) -> ArrayList<Point>? = { null },
    private val contextBrightness: (View) -> Float = { 0f },
) {
    private val baselines = WeakHashMap<View, MutableMap<Method, Array<Any?>>>()
    private val arrayPairs = WeakHashMap<View, Pair<FloatArray, FloatArray>>()
    private val legacyBlends = WeakHashMap<View, ArrayList<Point>>()
    private val refreshing = ThreadLocal<Boolean>()
    private val blendDispatch = ThreadLocal<Boolean>()
    private val bypassView = ThreadLocal<View>()
    private val handler = Handler(Looper.getMainLooper())
    private var clearBlend: Method? = null
    private var addBlend: Method? = null
    private val refresh = Runnable { replay() }

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

    fun scheduleRefresh() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 16L)
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
                val windowSelfBlur = name == "setMiSelfBlur" && GlassSurfaces.isShadeWindow(view)
                if (!accepts(view) && !windowSelfBlur) return@hook chain.proceed()
                if (name == "setMiSelfBlur" && !windowSelfBlur && GlassSurfaces.classify(view) != GlassSurface.SHADE) return@hook chain.proceed()
                if (name == "setBackgroundColor" && GlassSurfaces.classify(view) != GlassSurface.SHADE) return@hook chain.proceed()
                if ((name == "setMiBackgroundBlendColors" || name == "setMiSelfBlur") && blendDispatch.get() == true) return@hook chain.proceed()
                val input = Array<Any?>(types.size) { chain.getArg(it) }
                val args = adjustExternal(view, method, input)
                val blending = name == "setMiBackgroundBlendColors" || name == "setMiSelfBlur"
                if (blending) blendDispatch.set(true)
                try { chain.proceedWith(view, args) } finally { if (blending) blendDispatch.remove() }
            }
        }.onFailure { runtime.log("OEM glass setter unavailable: $name", it) }
    }

    /** Also used for MAML's reflective writer so it is tuned exactly once. */
    fun adjustExternal(view: View, method: Method, input: Array<Any?>): Array<Any?> {
        val name = method.name
        var baseline = copyArgs(input)
        if (name == "setMiGlass") {
            val original = input.firstOrNull() as? FloatArray ?: return input
            if (!hasSoftGlassRecipe(original)) {
                if (refreshing.get() != true) synchronized(baselines) {
                    baselines.getOrPut(view) { linkedMapOf() }[method] = baseline
                    arrayPairs.remove(view)
                }
                return input
            }
            val previous = synchronized(baselines) { arrayPairs[view] }
            if (original === previous?.second) baseline = arrayOf(previous.first.copyOf())
        }
        if (refreshing.get() != true) synchronized(baselines) {
            baselines.getOrPut(view) { linkedMapOf() }[method] = baseline
            if (name == "setMiBackgroundBlendColors") legacyBlends.remove(view)
        }
        val settings = settingsFor(view)
        val output: Array<Any?> = when (name) {
            "setMiGlass" -> {
                val native = baseline[0] as FloatArray
                val selected = unifiedRecipe(view)?.takeIf(::hasSoftGlassRecipe) ?: native
                arrayOf(if (settings.enabled) tuneSoftGlass(selected, settings, contextBrightness = contextBrightness(view)) else selected)
            }
            "setMiGlassBlurRadius" -> arrayOf(
                if (settings.enabled) softGlassBlur(baseline[0] as Int, settings.refractionBlur) else baseline[0],
                if (settings.enabled) softGlassBlur(baseline[1] as Int, settings.refractionBlur) else baseline[1],
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
                if (view != null && refreshing.get() != true && blendDispatch.get() != true) synchronized(baselines) {
                    legacyBlends.remove(view)
                    baselines[view]?.keys?.removeAll { it.name == "setMiBackgroundBlendColors" }
                }
                chain.proceed()
            }
            runtime.hook(addBlend!!, "lite-glass:legacy-add") { chain ->
                val view = chain.thisObject as? View ?: return@hook chain.proceed()
                if (bypassView.get() === view || blendDispatch.get() == true || !accepts(view)) return@hook chain.proceed()
                val color = chain.getArg(0) as Int
                if (refreshing.get() != true) synchronized(baselines) {
                    legacyBlends.getOrPut(view) { arrayListOf() }.add(Point(color, chain.getArg(1) as Int))
                }
                val settings = settingsFor(view)
                chain.proceedWith(view, arrayOf(softGlassColorAlpha(color, opacity(view, settings)), chain.getArg(1)))
            }
        }.onFailure { runtime.log("OEM legacy blend API unavailable", it) }
    }

    private fun replay() {
        val (surfaces, legacy) = synchronized(baselines) {
            baselines.map { (view, setters) -> view to setters.toMap() } to
                legacyBlends.map { it.key to it.value.map { p -> Point(p.x, p.y) } }
        }
        refreshing.set(true)
        try {
            surfaces.forEach { (view, setters) ->
                if (view.isAttachedToWindow) setters.forEach { (method, args) ->
                    runCatching { method.invoke(view, *copyArgs(args)) }
                        .onFailure { runtime.log("Could not refresh ${method.name}", it) }
                }
            }
            legacy.forEach { (view, colors) ->
                if (view.isAttachedToWindow) runCatching {
                    clearBlend?.invoke(view)
                    colors.forEach { p -> addBlend?.invoke(view, p.x, p.y) }
                }
            }
        } finally { refreshing.remove() }
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
