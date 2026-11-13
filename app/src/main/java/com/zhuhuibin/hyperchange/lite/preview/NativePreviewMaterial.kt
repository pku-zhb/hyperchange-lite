// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.preview

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.view.View
import com.zhuhuibin.hyperchange.lite.SettingsMigration
import com.zhuhuibin.hyperchange.lite.glass.shadeBlendColor
import com.zhuhuibin.hyperchange.lite.glass.SoftGlassSettings
import com.zhuhuibin.hyperchange.lite.glass.shadeGlassSettings
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
    if (scene == "SHADE") {
        val shade = shadeGlassSettings(values)
        return PreviewMaterialState(shade,
            if (shade.enabled) "整屏背景调节已开启" else "整屏背景调节已关闭，参数保留",
            "仅预览整层背景取样与底色；不使用卡片折射和光学配方，系统仍需实际展开面板")
    }
    fun enabled(key: String, fallback: Boolean = false) = values[key] as? Boolean ?: fallback
    val range = when (scene) {
        "NOTIFICATION", "HEADS_UP" -> enabled("glass_notifications", true)
        "MEDIA" -> SettingsMigration.resolve(values)[SettingsMigration.MEDIA_KEY] == true
        "CONTROL" -> enabled("glass_control_center", true)
        "VOLUME" -> enabled("glass_volume", true)
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

/** Schematic fallback follows the OEM extra-zoom direction, using the local sample baseline. */
internal fun previewExtraZoom(percent: Int): Float = 1f + softGlassScale(.075f, percent)

internal data class PreviewSamplingInputs(
    val backgroundBlur: Int, val backgroundScale: Float, val opacity: Int,
    val smallRefractionBlur: Int?, val bigRefractionBlur: Int?,
)

/** Whole-window sampling never acquires the card's independent refraction channel. */
internal fun previewSamplingInputs(settings: SoftGlassSettings, wholeWindow: Boolean): PreviewSamplingInputs =
    PreviewSamplingInputs(
        softGlassBlur(40, if (settings.enabled) settings.backgroundBlur else 100),
        softGlassScale(.075f, if (settings.enabled) settings.backgroundScale else 100),
        if (settings.enabled) settings.backgroundOpacity else 100,
        if (wholeWindow) null else softGlassBlur(36, if (settings.enabled) settings.refractionBlur else 100),
        if (wholeWindow) null else softGlassBlur(72, if (settings.enabled) settings.refractionBlur else 100),
    )

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
    private var backdropBlends: List<Point>? = null
    private var night = false
    private var resolved = false
    private val methods = HashMap<String, Method?>()

    fun setNight(value: Boolean) {
        if (night != value) { night = value; resolved = false; recipe = null; backdropBlends = null }
    }

    fun apply(view: View, glass: SoftGlassSettings, scene: String = "NOTIFICATION"): Boolean {
        if (!resolved) resolveRecipe()
        if (view.width <= 0 || view.height <= 0 || !view.isHardwareAccelerated) return false
        if (scene == "SHADE") return applyBackdrop(view, glass)
        val original = recipe ?: run { clear(view); return false }
        val inputs = previewSamplingInputs(glass, wholeWindow = false)
        return runCatching {
            // The test chart is inside this Activity. Use the OEM local-content
            // path, as HyperChanger's applyLocalBlur does, rather than sampling
            // the separate window behind this App. A missing setup API must not
            // be reported as a successfully configured native sample.
            invoke(view, "setMiBackgroundBlurMode", 1)
            invoke(view, "setMiBackgroundBlurType", 1)
            invoke(view, "setMiBackgroundBlurRadius", inputs.backgroundBlur)
            invoke(view, "setPassWindowBlurEnabled", false)
            invoke(view, "setMiViewBlurMode", 1)
            invoke(view, "setMiViewMaterialType", 1)
            invoke(view, "setMiBackgroundBlurScaleRatio", inputs.backgroundScale)
            optional(view, "setMiGlassSdfMaxSize", view.width.toFloat(), view.height.toFloat())
            invoke(view, "setMiGlassBlurRadius", requireNotNull(inputs.smallRefractionBlur), requireNotNull(inputs.bigRefractionBlur))
            invoke(view, "setMiGlass", tuneSoftGlass(original.glass.copyOf(), glass))
            // The local view has no OEM NotificationRowBlurEffect to seed its blend
            // texture. Supply that effect's normal shade colors once through the
            // batch setter; do not add a second Drawable tint above native glass.
            invoke(view, "setMiBackgroundBlendColors", ArrayList(original.blends.map {
                Point(softGlassColorAlpha(it.x, inputs.opacity), it.y)
            }))
            view.invalidate()
            true
        }.getOrElse { clear(view); false }
    }

    private fun applyBackdrop(view: View, settings: SoftGlassSettings): Boolean {
        val colors = backdropBlends ?: run { clear(view); return false }
        val inputs = previewSamplingInputs(settings, wholeWindow = true)
        return runCatching {
            // This is an App-local backdrop sampler, not a glass card or a
            // cross-window blur over another App. The OEM shade colors supply tint.
            optional(view, "setMiGlass", floatArrayOf())
            optional(view, "setMiGlassBlurRadius", 0, 0)
            invoke(view, "setMiViewMaterialType", 0)
            invoke(view, "setMiBackgroundBlurMode", 1)
            invoke(view, "setMiBackgroundBlurType", 1)
            invoke(view, "setPassWindowBlurEnabled", false)
            invoke(view, "setMiViewBlurMode", 1)
            invoke(view, "setMiBackgroundBlurRadius", inputs.backgroundBlur)
            invoke(view, "setMiBackgroundBlurScaleRatio", inputs.backgroundScale)
            invoke(view, "setMiBackgroundBlendColors", ArrayList(colors.map {
                Point(shadeBlendColor(it.x, it.y, inputs.opacity), it.y)
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
        val resources = runCatching {
            val app = context.createPackageContext("com.android.systemui", 0)
            val config = Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            app.createConfigurationContext(config).resources
        }.getOrNull() ?: return
        recipe = runCatching {
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
        backdropBlends = runCatching {
            val id = resources.getIdentifier("shade_blend_colors_bionics", "array", "com.android.systemui")
            check(id != 0) { "Whole-window shade colors are unavailable" }
            val colors = resources.getIntArray(id)
            check(colors.isNotEmpty() && colors.size % 2 == 0) { "Invalid whole-window shade colors" }
            colors.toList().chunked(2).map { Point(it[0], it[1]) }
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
