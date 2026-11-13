// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import com.zhuhuibin.hyperchange.lite.SettingsMigration
import com.zhuhuibin.hyperchange.lite.builtInGlassPresets
import org.junit.Assert.*
import org.junit.Test

class MaterialLayerIsolationTest {
    private val source = FloatArray(42) { .3f }.apply {
        this[4] = 1f; this[5] = 1.2f; this[14] = .6f; this[19] = 12f
        this[21] = 24f; this[22] = 150f; this[24] = .8f; this[32] = 1.4f
    }
    private val whole = mapOf<String, Any>(
        SettingsMigration.BACKDROP_VERSION_KEY to 1,
        SettingsMigration.BACKDROP_ENABLED_KEY to true,
        "glass_shade_blur" to 140f, "glass_shade_scale" to 25f, "glass_shade_opacity" to 65f,
    )

    @Test fun everyCardPresetLeavesWholeSamplingAndOpticalRecipeUnchanged() {
        val expected = shadeGlassSettings(whole, enabled = true)
        builtInGlassPresets.forEach { preset ->
            val settings = shadeGlassSettings(whole + preset.values, enabled = true)
            assertEquals(preset.title, expected, settings)
            assertEquals(100, settings.refractionBlur)
            assertArrayEquals(source, tuneSoftGlass(source, settings), 0f)
            assertEquals(112, softGlassBlur(80, settings.backgroundBlur))
            assertEquals(.08f, softGlassScale(.08f, settings.backgroundScale), .000001f)
            assertEquals(0x53445566, softGlassColorAlpha(0x80445566.toInt(), settings.backgroundOpacity))
        }
    }

    @Test fun wholeEditsDoNotChangeTheExistingCardInputs() {
        val preset = builtInGlassPresets.first().values
        val once = SettingsMigration.resolve(whole + preset)
        val edited = SettingsMigration.resolve(once + mapOf(
            "glass_shade_blur" to 0f, "glass_shade_scale" to 200f, "glass_shade_opacity" to 0f,
        ))
        assertEquals(preset, edited.filterKeys { it in preset })
        assertNotEquals(shadeGlassSettings(once, true), shadeGlassSettings(edited, true))
    }

    @Test fun cardMasterAndPresetsCannotEnableOrDisableTheWholeLayer() {
        val off = whole + mapOf(SettingsMigration.BACKDROP_ENABLED_KEY to false, "glass_enabled" to false)
        builtInGlassPresets.forEach { preset ->
            assertFalse(shadeGlassSettings(off + preset.values).enabled)
        }
        assertTrue(shadeGlassSettings(whole + mapOf("glass_enabled" to false, "glass_shade" to false)).enabled)
        assertFalse(shadeGlassSettings(off + mapOf("glass_enabled" to true, "glass_shade" to true)).enabled)
    }

    @Test fun historicalWindowScaleRemainsExportedWithoutChangingRuntime() {
        val legacy = SettingsMigration.resolve(whole + ("glass_shade_scale" to 0f))
        assertEquals(0f, legacy["glass_shade_scale"])
        assertEquals(100, shadeGlassSettings(legacy).backgroundScale)
        val otherScale = SettingsMigration.resolve(legacy + ("glass_shade_scale" to 200f))
        assertEquals(200f, otherScale["glass_shade_scale"])
        assertEquals(shadeGlassSettings(legacy), shadeGlassSettings(otherScale))
    }

    @Test fun oldHookSnapshotResolvesBothLayersBeforeTheSettingsAppOpens() {
        val old = mapOf("glass_background_blur" to 35f, "glass_background_scale" to 64f,
            "glass_background_opacity" to 45f, "glass_alpha" to 200f)
        val settings = shadeGlassSettings(old, enabled = true)
        assertEquals(35, settings.backgroundBlur)
        assertEquals(100, settings.backgroundScale)
        assertEquals(45, settings.backgroundOpacity)
        assertArrayEquals(source, tuneSoftGlass(source, settings), 0f)
    }
}
