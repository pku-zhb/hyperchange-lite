// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.preview

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.view.View
import com.zhuhuibin.hyperchange.lite.SettingsMigration
import com.zhuhuibin.hyperchange.lite.glass.SoftGlassSettings
import com.zhuhuibin.hyperchange.lite.glass.softGlassBlur
import com.zhuhuibin.hyperchange.lite.glass.softGlassColorAlpha
import com.zhuhuibin.hyperchange.lite.glass.softGlassScale
import com.zhuhuibin.hyperchange.lite.glass.tuneSoftGlass
import java.lang.reflect.Method

internal data class PreviewMaterialState(
    val settings: SoftGlassSettings,
    val scopeStatus: String,
    val scopeNote: String,
)

/** The preview is an unlocked local sample, never a simulated SystemUI scope. */
internal fun previewMaterialState(
    scene: String,
    values: Map<String, *>,
    saved: SoftGlassSettings,
    respectScopes: Boolean,
): PreviewMaterialState {
    fun enabled(key: String, fallback: Boolean = false) = values[key] as? Boolean ?: fallback
    val range = when (scene) {
        "NOTIFICATION", "HEADS_UP" -> enabled("glass_notifications", true)
        "MEDIA" -> SettingsMigration.resolve(values)[SettingsMigration.MEDIA_KEY] == true
        "CONTROL" -> enabled("glass_control_center", true)
        "VOLUME" -> enabled("glass_volume", true)
        "SHADE" -> enabled("glass_shade", true)
        else -> true
    }
    val independent = scene == "WIDGET" || scene == "SIDEBAR"
    val active = !respectScopes || when (scene) {
        "WIDGET" -> enabled("glass_assistant")
        "SIDEBAR" -> enabled("glass_sidebar")
        else -> saved.enabled && range
    }
    val settings = saved.copy(
        enabled = active,
        backgroundBlur = if (independent) 100 else saved.backgroundBlur,
        backgroundScale = if (independent) 100 else saved.backgroundScale,
        backgroundOpacity = if (independent) 100 else saved.backgroundOpacity,
    )
    val status = when {
        !respectScopes -> "本地调参"
        active -> "当前范围调参已开启"
        !range -> "当前范围已关闭，调参暂停"
        independent -> "当前独立开关已关闭，调参暂停"
        else -> "柔光调节已关闭，参数保留"
    }
    val note = when (scene) {
        "NOTIFICATION", "MEDIA" -> "统一材质${if (enabled("glass_unify_notifications")) "开" else "关"}；锁屏范围${if (SettingsMigration.resolve(values)[SettingsMigration.LOCKSCREEN_KEY] == true) "开" else "关"}（系统切换未模拟）"
        "HEADS_UP" -> "悬浮不参与统一材质；锁屏状态未模拟"
        "SIDEBAR" -> "统一侧栏${if (enabled("glass_sidebar_unify")) "开" else "关"}；系统侧栏配方未还原"
        "WIDGET" -> "独立于系统柔光开关；系统组件配方未还原"
        else -> "正常通知配方样例；未还原系统场景配方"
    }
    return PreviewMaterialState(settings, status, note)
}

/** Disabled geometry controls retain a stable local baseline instead of leaking saved overrides. */
internal fun previewRadius(
    key: String,
    saved: Float,
    baseline: Float,
    values: Map<String, *>,
    respectEnabled: Boolean,
): Float {
    val switch = when {
        key == "notification_radius" -> "notification_radius_enabled"
        key.startsWith("cc_") -> "cc_radius_enabled"
        key == "volume_corner" -> "volume_corner_enabled"
        key == "volume_timer_corner" -> "volume_timer_corner_enabled"
        else -> null
    }
    return (if (!respectEnabled || switch == null || values[switch] == true) saved else baseline).coerceAtLeast(0f)
}

/** Uses the installed OEM recipe; no copied HyperLight material or native binary. */
internal class NativePreviewMaterial(private val context: Context) {
    private data class Recipe(val glass: FloatArray, val blends: List<Point>)
    private var recipe: Recipe? = null
    private var night = false
    private var resolved = false
    private val methods = HashMap<String, Method?>()

    fun setNight(value: Boolean) {
        if (night != value) { night = value; resolved = false; recipe = null }
    }

    fun apply(view: View, glass: SoftGlassSettings): Boolean {
        if (!resolved) resolveRecipe()
        val original = recipe ?: return false
        if (view.width <= 0 || view.height <= 0 || !view.isHardwareAccelerated) return false
        return runCatching {
            // The test chart is inside this Activity. Use the OEM local-content
            // path, as HyperChanger's applyLocalBlur does, rather than sampling
            // the separate window behind this App. A missing setup API must not
            // be reported as a successfully configured native sample.
            invoke(view, "setMiBackgroundBlurMode", 1)
            invoke(view, "setMiBackgroundBlurType", 1)
            invoke(view, "setMiBackgroundBlurRadius", softGlassBlur(40, if (glass.enabled) glass.backgroundBlur else 100))
            invoke(view, "setPassWindowBlurEnabled", false)
            invoke(view, "setMiViewBlurMode", 1)
            invoke(view, "setMiViewMaterialType", 1)
            invoke(view, "setMiBackgroundBlurScaleRatio", softGlassScale(.075f, if (glass.enabled) glass.backgroundScale else 100))
            optional(view, "setMiGlassSdfMaxSize", view.width.toFloat(), view.height.toFloat())
            val refraction = if (glass.enabled) glass.refractionBlur else 100
            invoke(view, "setMiGlassBlurRadius", softGlassBlur(36, refraction), softGlassBlur(72, refraction))
            invoke(view, "setMiGlass", tuneSoftGlass(original.glass.copyOf(), glass))
            // The local view has no OEM NotificationRowBlurEffect to seed its blend
            // texture. Supply that effect's normal shade colors once through the
            // batch setter; do not add a second Drawable tint above native glass.
            val opacity = if (glass.enabled) glass.backgroundOpacity else 100
            invoke(view, "setMiBackgroundBlendColors", ArrayList(original.blends.map {
                Point(softGlassColorAlpha(it.x, opacity), it.y)
            }))
            view.invalidate()
            true
        }.getOrElse { clear(view); false }
    }

    fun clear(view: View) {
        optional(view, "setMiBackgroundBlendColors", arrayListOf<Point>())
        optional(view, "clearMiBackgroundBlendColor")
        optional(view, "setMiGlass", floatArrayOf())
        optional(view, "setMiViewMaterialType", 0)
        optional(view, "setMiBackgroundBlurMode", 0)
        optional(view, "setMiViewBlurMode", 0)
        optional(view, "setPassWindowBlurEnabled", false)
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
            val glass = resources.getStringArray(id).map { it.toFloatOrNull() ?: return@runCatching null }.toFloatArray()
                .takeIf { it.size >= 42 && it.all(Float::isFinite) && it.any { value -> value != 0f } }
                ?: return@runCatching null
            val blends = (1..2).map { index ->
                val color = resources.getIdentifier("notification_element_blend_shade_color_$index", "color", "com.android.systemui")
                val mode = resources.getIdentifier("notification_element_blend_shade_mode_$index", "integer", "com.android.systemui")
                check(color != 0 && mode != 0) { "Normal notification blend resources are unavailable" }
                Point(resources.getColor(color, null), resources.getInteger(mode))
            }
            Recipe(glass, blends)
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
