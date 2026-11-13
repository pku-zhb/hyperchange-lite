// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger SoftGlassSettings and runtime preference handling.
package com.zhuhuibin.hyperchange.lite.glass

import android.content.SharedPreferences
import com.zhuhuibin.hyperchange.lite.SettingsMigration
import kotlin.math.roundToInt

internal fun readSoftGlassSettings(p: SharedPreferences): SoftGlassSettings = readSoftGlassSettings(p.all)

internal fun readSoftGlassSettings(values: Map<String, *>): SoftGlassSettings = SoftGlassSettings(
    enabled = values.flag("glass_enabled", false),
    refractionBlur = values.number("glass_refraction_blur", 100f).roundToInt(),
    backgroundBlur = values.number("glass_background_blur", 100f).roundToInt(),
    backgroundScale = values.number("glass_background_scale", 100f).roundToInt(),
    backgroundOpacity = values.number("glass_background_opacity", 100f).roundToInt(),
    adjustments = softGlassParameters.associate { definition ->
        definition.key to values.number("glass_${definition.key}", if (definition.key == "darker") -100f else 0f).roundToInt()
    },
).normalized()

/** Whole-window sampling has no card refraction or optical-slot adjustments. */
internal fun shadeGlassSettings(values: Map<String, *>, enabled: Boolean? = null): SoftGlassSettings {
    val resolved = SettingsMigration.resolve(values)
    return resolvedShadeGlassSettings(resolved, enabled)
}

private fun resolvedShadeGlassSettings(resolved: Map<String, *>, enabled: Boolean? = null): SoftGlassSettings {
    return SoftGlassSettings(
        enabled = enabled ?: resolved[SettingsMigration.BACKDROP_ENABLED_KEY] as Boolean,
        backgroundBlur = (resolved.getValue("glass_shade_blur") as Number).toFloat().roundToInt(),
        // Retained in storage/exports only; whole-window controls do not zoom the App.
        backgroundScale = 100,
        backgroundOpacity = (resolved.getValue("glass_shade_opacity") as Number).toFloat().roundToInt(),
    ).normalized()
}

internal fun readShadeGlassSettings(p: SharedPreferences): SoftGlassSettings =
    shadeGlassSettings(p.all)

internal fun SharedPreferences.number(key: String, fallback: Float): Float =
    all.number(key, fallback)

private fun Map<String, *>.number(key: String, fallback: Float): Float =
    ((this[key] as? Number)?.toFloat() ?: fallback).takeIf { it.isFinite() } ?: fallback

private fun Map<String, *>.flag(key: String, fallback: Boolean): Boolean = this[key] as? Boolean ?: fallback

internal data class GlassRuntimeSettings(
    val glass: SoftGlassSettings,
    val shadeGlass: SoftGlassSettings,
    val notifications: Boolean,
    val media: Boolean,
    val controlCenter: Boolean,
    val volume: Boolean,
    val shade: Boolean,
    val unify: Boolean,
    val assistant: Boolean,
    val sidebar: Boolean,
    val unifySidebar: Boolean,
) {
    companion object {
        fun read(p: SharedPreferences) = from(p.all)

        internal fun from(values: Map<String, *>): GlassRuntimeSettings {
            val resolved = SettingsMigration.resolve(values)
            val shade = resolvedShadeGlassSettings(resolved)
            return GlassRuntimeSettings(
                readSoftGlassSettings(values), shade, values.flag("glass_notifications", true),
                resolved[SettingsMigration.MEDIA_KEY] as Boolean,
                values.flag("glass_control_center", true), values.flag("glass_volume", true),
                shade.enabled, values.flag("glass_unify_notifications", false),
                values.flag("glass_assistant", false), values.flag("glass_sidebar", false),
                values.flag("glass_sidebar_unify", false),
            )
        }
    }
}
