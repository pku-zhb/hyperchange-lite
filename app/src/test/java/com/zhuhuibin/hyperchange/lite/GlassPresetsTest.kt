// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.glass.SoftGlassSettings
import com.zhuhuibin.hyperchange.lite.glass.softGlassBlur
import com.zhuhuibin.hyperchange.lite.glass.softGlassColorAlpha
import com.zhuhuibin.hyperchange.lite.glass.softGlassParameters
import com.zhuhuibin.hyperchange.lite.glass.softGlassScale
import com.zhuhuibin.hyperchange.lite.glass.tuneSoftGlass
import org.junit.Assert.*
import org.junit.Test

class GlassPresetsTest {
    // Independent fixtures transcribed from upstream's original A-F preset files.
    private val numericKeys = listOf(
        "refraction_blur", "background_blur", "background_scale", "background_opacity",
        "luminance", "saturation", "brightness", "alpha", "refraction", "thickness",
        "background_saturation", "background_brightness", "burn", "edge", "edge_feather",
        "reflection_offset", "light", "darker",
    )
    private val expected = listOf(
        listOf(13, 12, 32, 53, 0, 0, 0, 0, 0, 0, 0, -38, 0, 200, 75, 0, 0, -100),
        listOf(22, 29, 32, 53, 0, 0, 0, 0, 0, 0, 0, -38, 0, 200, 75, 0, 0, -100),
        listOf(44, 36, 0, 73, -79, -20, 5, 110, 200, -16, 107, -59, 80, 200, 100, 200, 31, -100),
        listOf(55, 28, 0, 73, -79, -20, 5, 4, 132, -16, 107, -59, 62, 126, 100, 200, 31, -100),
        listOf(115, 81, 0, 73, -79, -20, 5, 4, 132, -16, 107, -59, 62, 126, 100, 200, 31, -100),
        listOf(117, 74, 32, 59, 0, 0, 0, 0, 0, 0, 0, -51, 0, 48, 75, 0, 0, -100),
    )

    @Test fun upstreamNamesIdsAndAllNumericValuesArePreserved() {
        assertEquals(listOf("澄光", "薄纱", "浮影", "朦光", "凝雾", "云隐"), builtInGlassPresets.map { it.title })
        assertEquals(('a'..'f').map { "builtin-custom-$it" }, builtInGlassPresets.map { it.id })
        assertEquals(expected.size, builtInGlassPresets.size)
        builtInGlassPresets.forEachIndexed { index, preset ->
            assertEquals(true, preset.values["glass_enabled"])
            assertEquals(expected[index].map(Int::toFloat), numericKeys.map { preset.values.getValue("glass_$it") })
        }
    }

    @Test fun everyPresetHasExactlyTheMaterialKeysAndCanonicalValues() {
        val expectedKeys = numericKeys.map { "glass_$it" }.toSet() + "glass_enabled"
        val specs = LiteCatalog.entries.associateBy { it.key }
        assertEquals(expectedKeys, glassPresetKeys)
        builtInGlassPresets.forEach { preset ->
            assertEquals(expectedKeys, preset.values.keys)
            preset.values.forEach { (key, value) -> assertEquals(value, specs.getValue(key).validate(value)) }
        }
    }

    @Test fun applyingMaterialValuesCannotChangeCompatibilityRangesOrOtherFeatures() {
        val original = SettingsCodec.defaults(LiteCatalog.entries).toMutableMap().apply {
            this["glass_media"] = false
            this["glass_notifications"] = false
            this["notification_lockscreen_effects"] = false
            this["glass_unify_notifications"] = true
            this["glass_assistant"] = true
            this["notification_press_tilt_enabled"] = true
        }
        builtInGlassPresets.forEach { preset ->
            val result = original + preset.values
            assertEquals(original.filterKeys { it !in glassPresetKeys }, result.filterKeys { it !in glassPresetKeys })
        }
    }

    @Test fun trialBackupRestoresParametersWithoutMigratingOrRestoringScopes() {
        val specs = LiteCatalog.entries.filter { it.key in glassPresetKeys }
        val before = SettingsCodec.defaults(LiteCatalog.entries).toMutableMap().apply {
            this["glass_enabled"] = false
            this["glass_background_blur"] = 87f
            this["glass_media"] = true
            this["notification_lockscreen_effects"] = true
        }
        val backup = SettingsCodec.encode(specs, before)
        val restore = SettingsCodec.decode(specs, backup)
        assertEquals(glassPresetKeys, restore.keys)
        assertEquals(before.filterKeys { it in glassPresetKeys }, restore)
        val during = (before + builtInGlassPresets.first().values + builtInGlassPresets.last().values)
            .toMutableMap().apply {
                this["glass_media"] = false
                this["notification_lockscreen_effects"] = false
            }
        val after = during + restore
        assertEquals(false, after["glass_media"])
        assertEquals(false, after["notification_lockscreen_effects"])
        assertEquals(false, after["glass_enabled"])
        assertEquals(87f, after["glass_background_blur"])
    }

    @Test fun allSixRecipesProduceDifferentNativeMaterialInputs() {
        val recipe = FloatArray(42) { .1f }.apply {
            this[4] = 1f; this[5] = 1.2f; this[6] = -.1f; this[7] = .5f
            this[14] = .6f; this[19] = 10f; this[21] = 20f; this[22] = 100f
            this[24] = .8f; this[28] = .4f; this[32] = 1.2f
            this[33] = 1.2f; this[34] = .3f; this[35] = .2f
        }
        val snapshots = builtInGlassPresets.map { preset ->
            fun n(key: String) = (preset.values.getValue("glass_$key") as Number).toInt()
            val settings = SoftGlassSettings(
                enabled = true, refractionBlur = n("refraction_blur"), backgroundBlur = n("background_blur"),
                backgroundScale = n("background_scale"), backgroundOpacity = n("background_opacity"),
                adjustments = softGlassParameters.associate { it.key to n(it.key) },
            ).normalized()
            val tuned = tuneSoftGlass(recipe, settings)
            assertFalse(preset.title, recipe.contentEquals(tuned))
            // A/B and D/E intentionally share glass slots; their sampler blur differs.
            tuned.toList() + listOf(
                softGlassBlur(80, settings.refractionBlur), softGlassBlur(90, settings.backgroundBlur),
                softGlassScale(.9f, settings.backgroundScale), softGlassColorAlpha(0x80445566.toInt(), settings.backgroundOpacity),
            )
        }
        assertEquals(6, snapshots.distinct().size)
    }
}
