// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger SoftGlassPresets.kt, Copyright 2026 btm_m.
// Changes: map the original A-F material recipes to Lite's existing settings keys.
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.glass.softGlassParameters

/** A complete material adjustment, without feature ranges or notification behavior. */
data class GlassPreset(
    val id: String,
    val title: String,
    val description: String,
    val values: Map<String, Any>,
)

/** Card material only: whole-window sampling and scope switches never enter a trial. */
val glassPresetKeys: Set<String> = buildSet {
    add("glass_enabled")
    add("glass_refraction_blur")
    add("glass_background_blur")
    add("glass_background_scale")
    add("glass_background_opacity")
    softGlassParameters.forEach { add("glass_${it.key}") }
}

private fun glassPreset(
    suffix: String, title: String, description: String,
    refractionBlur: Int, backgroundBlur: Int, backgroundScale: Int, backgroundOpacity: Int,
    adjustments: Map<String, Int>,
): GlassPreset {
    require(adjustments.keys == softGlassParameters.map { it.key }.toSet())
    val raw = buildMap<String, Any> {
        put("glass_enabled", true)
        put("glass_refraction_blur", refractionBlur)
        put("glass_background_blur", backgroundBlur)
        put("glass_background_scale", backgroundScale)
        put("glass_background_opacity", backgroundOpacity)
        adjustments.forEach { (key, value) -> put("glass_$key", value) }
    }
    require(raw.keys == glassPresetKeys)
    val specs = GlassSettingsSpec.entries.associateBy { it.key }
    return GlassPreset(
        id = "builtin-custom-$suffix",
        title = title,
        description = description,
        values = raw.mapValues { (key, value) -> specs.getValue(key).validate(value) },
    )
}

/** Original upstream A-F recipes, in their clear-to-blurred presentation order. */
val builtInGlassPresets: List<GlassPreset> = listOf(
    glassPreset("a", "澄光", "高透清亮，轻底色与细亮边", 13, 12, 32, 53, mapOf(
        "luminance" to 0, "saturation" to 0, "brightness" to 0,
        "alpha" to 0, "refraction" to 0, "thickness" to 0,
        "background_saturation" to 0, "background_brightness" to -38, "burn" to 0,
        "edge" to 200, "edge_feather" to 75, "reflection_offset" to 0,
        "light" to 0, "darker" to -100,
    )),
    glassPreset("b", "薄纱", "轻度柔雾，柔化背后线条", 22, 29, 32, 53, mapOf(
        "luminance" to 0, "saturation" to 0, "brightness" to 0,
        "alpha" to 0, "refraction" to 0, "thickness" to 0,
        "background_saturation" to 0, "background_brightness" to -38, "burn" to 0,
        "edge" to 200, "edge_feather" to 75, "reflection_offset" to 0,
        "light" to 0, "darker" to -100,
    )),
    glassPreset("c", "浮影", "色彩鲜明，折射与亮边更突出", 44, 36, 0, 73, mapOf(
        "luminance" to -79, "saturation" to -20, "brightness" to 5,
        "alpha" to 110, "refraction" to 200, "thickness" to -16,
        "background_saturation" to 107, "background_brightness" to -59, "burn" to 80,
        "edge" to 200, "edge_feather" to 100, "reflection_offset" to 200,
        "light" to 31, "darker" to -100,
    )),
    glassPreset("d", "朦光", "柔光立体，比浮影更轻盈", 55, 28, 0, 73, mapOf(
        "luminance" to -79, "saturation" to -20, "brightness" to 5,
        "alpha" to 4, "refraction" to 132, "thickness" to -16,
        "background_saturation" to 107, "background_brightness" to -59, "burn" to 62,
        "edge" to 126, "edge_feather" to 100, "reflection_offset" to 200,
        "light" to 31, "darker" to -100,
    )),
    glassPreset("e", "凝雾", "浓厚磨砂，保留立体玻璃边", 115, 81, 0, 73, mapOf(
        "luminance" to -79, "saturation" to -20, "brightness" to 5,
        "alpha" to 4, "refraction" to 132, "thickness" to -16,
        "background_saturation" to 107, "background_brightness" to -59, "burn" to 62,
        "edge" to 126, "edge_feather" to 100, "reflection_offset" to 200,
        "light" to 31, "darker" to -100,
    )),
    glassPreset("f", "云隐", "柔和雾面，反光更克制", 117, 74, 32, 59, mapOf(
        "luminance" to 0, "saturation" to 0, "brightness" to 0,
        "alpha" to 0, "refraction" to 0, "thickness" to 0,
        "background_saturation" to 0, "background_brightness" to -51, "burn" to 0,
        "edge" to 48, "edge_feather" to 75, "reflection_offset" to 0,
        "light" to 0, "darker" to -100,
    )),
)
