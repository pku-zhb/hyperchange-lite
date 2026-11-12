// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger SoftGlassSettings and runtime preference handling.
package com.zhuhuibin.hyperchange.lite.glass

import android.content.SharedPreferences
import com.zhuhuibin.hyperchange.lite.SettingsMigration
import kotlin.math.roundToInt

internal fun readSoftGlassSettings(p: SharedPreferences): SoftGlassSettings = SoftGlassSettings(
    enabled = p.getBoolean("glass_enabled", false),
    refractionBlur = p.number("glass_refraction_blur", 100f).roundToInt(),
    backgroundBlur = p.number("glass_background_blur", 100f).roundToInt(),
    backgroundScale = p.number("glass_background_scale", 100f).roundToInt(),
    backgroundOpacity = p.number("glass_background_opacity", 100f).roundToInt(),
    adjustments = softGlassParameters.associate { definition ->
        definition.key to p.number("glass_${definition.key}", if (definition.key == "darker") -100f else 0f).roundToInt()
    },
).normalized()

internal fun SharedPreferences.number(key: String, fallback: Float): Float =
    ((all[key] as? Number)?.toFloat() ?: fallback).takeIf { it.isFinite() } ?: fallback

internal data class GlassRuntimeSettings(
    val glass: SoftGlassSettings,
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
        fun read(p: SharedPreferences) = GlassRuntimeSettings(
            readSoftGlassSettings(p), p.getBoolean("glass_notifications", true),
            SettingsMigration.resolve(p.all)[SettingsMigration.MEDIA_KEY] as Boolean,
            p.getBoolean("glass_control_center", true), p.getBoolean("glass_volume", true),
            p.getBoolean("glass_shade", true), p.getBoolean("glass_unify_notifications", false),
            p.getBoolean("glass_assistant", false), p.getBoolean("glass_sidebar", false),
            p.getBoolean("glass_sidebar_unify", false),
        )
    }
}
