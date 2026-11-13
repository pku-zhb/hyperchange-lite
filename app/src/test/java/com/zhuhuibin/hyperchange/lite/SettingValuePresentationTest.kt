package com.zhuhuibin.hyperchange.lite

import org.junit.Assert.*
import org.junit.Test

class SettingValuePresentationTest {
    @Test fun commonRangesAreLegalAndContainExistingDefaults() {
        LiteCatalog.entries.filterNot { it.isToggle }.forEach { spec ->
            val range = spec.sliderRange
            assertTrue(spec.key, range.start >= spec.min && range.endInclusive <= spec.max)
            assertTrue(spec.key, range.start < range.endInclusive)
            assertTrue(spec.key, (spec.defaultValue as Number).toFloat() in range)
        }
    }

    @Test fun narrowRangesDoNotRewriteExtremeExistingPresets() {
        val spec = LiteCatalog.entries.first { it.key == "glass_refraction" }
        val value = builtInGlassPresets.first { it.id == "builtin-custom-c" }.values.getValue(spec.key)
        assertFalse((value as Number).toFloat() in spec.sliderRange)
        val stored = SettingsCodec.encode(listOf(spec), mapOf(spec.key to value))
        assertEquals(200f, SettingsCodec.decode(listOf(spec), stored)[spec.key])
        assertEquals(200f, spec.max, 0f)
    }

    @Test fun oneStepMovesWithinTheWindowWithoutLargeMaterialJumps() {
        val spec = LiteCatalog.entries.first { it.key == "glass_background_brightness" }
        assertEquals(-30f..30f, spec.sliderRange)
        assertEquals(1f, spec.validate(spec.step))
        assertEquals("%", spec.valueUnit)
        assertEquals("px", LiteCatalog.entries.first { it.key == "volume_edge_gap" }.valueUnit)
        assertEquals("dp", LiteCatalog.entries.first { it.key == "volume_thin_width" }.valueUnit)
    }

    @Test fun fullWindowControlsCanReachClearAndOriginalWithoutChangingBackups() {
        for (key in listOf("glass_shade_blur", "glass_shade_opacity")) {
            val spec = LiteCatalog.entries.first { it.key == key }
            assertEquals(0f..100f, spec.sliderRange)
            assertEquals(100f, spec.defaultValue)
            assertEquals(200f, SettingsCodec.decode(listOf(spec),
                SettingsCodec.encode(listOf(spec), mapOf(spec.key to 200f)))[spec.key])
        }
        assertTrue("glass_shade_scale" in DeferredSettings.hiddenKeys)
        assertTrue(LiteCatalog.entries.any { it.key == "glass_shade_scale" })
    }

    @Test fun edgeGapUsesASmallPixelSliderWithoutLimitingImportedPositions() {
        val spec = LiteCatalog.entries.first { it.key == "volume_edge_gap" }
        assertEquals(0f..32f, spec.sliderRange)
        assertEquals("px", spec.valueUnit)
        assertEquals(6f, spec.defaultValue)
        assertEquals(0f, spec.min, 0f)
        assertEquals(200f, spec.max, 0f)
        assertEquals(200f, SettingsCodec.decode(listOf(spec),
            SettingsCodec.encode(listOf(spec), mapOf(spec.key to 200f)))[spec.key])
    }
}
