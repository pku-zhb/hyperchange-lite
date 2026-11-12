// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.preview

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.View
import com.zhuhuibin.hyperchange.lite.glass.readSoftGlassSettings
import com.zhuhuibin.hyperchange.lite.glass.softGlassBlur
import com.zhuhuibin.hyperchange.lite.glass.softGlassScale
import com.zhuhuibin.hyperchange.lite.glass.tuneSoftGlass
import java.lang.reflect.Method

/** Uses the installed OEM recipe; no copied HyperLight material or native binary. */
internal class NativePreviewMaterial(private val context: Context) {
    private var recipe: FloatArray? = null
    private var night = false
    private var resolved = false
    private val methods = HashMap<String, Method?>()

    fun setNight(value: Boolean) {
        if (night != value) { night = value; resolved = false; recipe = null }
    }

    fun apply(view: View, preferences: SharedPreferences): Boolean {
        if (!resolved) resolveRecipe()
        val original = recipe ?: return false
        if (view.width <= 0 || view.height <= 0 || !view.isHardwareAccelerated) return false
        return runCatching {
            val glass = readSoftGlassSettings(preferences).copy(enabled = true)
            // Register this sample in the window compositor before setting its shader.
            optional(view, "setPassWindowBlurEnabled", true)
            optional(view, "setMiBackgroundBlurMode", 1)
            optional(view, "setMiViewBlurMode", 1)
            optional(view, "setMiViewMaterialType", 1)
            optional(view, "setMiBackgroundBlurRadius", softGlassBlur(40, glass.backgroundBlur))
            optional(view, "setMiBackgroundBlurScaleRatio", softGlassScale(.075f, glass.backgroundScale))
            optional(view, "setMiGlassSdfMaxSize", view.width.toFloat(), view.height.toFloat())
            invoke(view, "setMiGlassBlurRadius", softGlassBlur(36, glass.refractionBlur), softGlassBlur(72, glass.refractionBlur))
            invoke(view, "setMiGlass", tuneSoftGlass(original.copyOf(), glass))
            view.invalidate()
            true
        }.getOrDefault(false)
    }

    private fun resolveRecipe() {
        resolved = true
        recipe = runCatching {
            val app = context.createPackageContext("com.android.systemui", 0)
            val config = Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val resources = app.createConfigurationContext(config).resources
            val id = resources.getIdentifier("notification_glass_params_normal", "array", "com.android.systemui")
            if (id == 0) return@runCatching null
            resources.getStringArray(id).map { it.toFloatOrNull() ?: return@runCatching null }.toFloatArray()
                .takeIf { it.size >= 42 && it.all(Float::isFinite) && it.any { value -> value != 0f } }
        }.getOrNull()
    }

    private fun optional(view: View, name: String, vararg args: Any) { runCatching { invoke(view, name, *args) } }

    private fun invoke(view: View, name: String, vararg args: Any) {
        val types = args.map {
            when (it) {
                is Int -> Int::class.javaPrimitiveType!!
                is Float -> Float::class.javaPrimitiveType!!
                is Boolean -> Boolean::class.javaPrimitiveType!!
                is FloatArray -> FloatArray::class.java
                else -> it.javaClass
            }
        }.toTypedArray()
        val key = name + types.joinToString { it.name }
        val method = if (methods.containsKey(key)) methods[key] else
            runCatching { View::class.java.getMethod(name, *types) }.getOrNull().also { methods[key] = it }
        checkNotNull(method) { "$name is unavailable" }.invoke(view, *args)
    }
}
