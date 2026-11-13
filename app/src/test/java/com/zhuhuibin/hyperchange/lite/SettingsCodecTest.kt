package com.zhuhuibin.hyperchange.lite

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SettingsCodecTest {
    private val specs = listOf(
        LiteSetting.toggle("on", "On", "Test"),
        LiteSetting.number("radius", "Radius", "Test", 12f, 0f, 100f, .5f),
    )

    @Test fun roundTripPreservesExplicitDisabledAndZero() {
        val input = mapOf("on" to false, "radius" to 0f)
        assertEquals(input, SettingsCodec.decode(specs, SettingsCodec.encode(specs, input)))
    }

    @Test fun invalidPresetIsRejectedBeforeReturningAnyMutation() {
        val invalid = """{"format":"hyperchange-lite","version":1,"settings":{"on":true,"radius":1000}}"""
        assertThrows(IllegalArgumentException::class.java) { SettingsCodec.decode(specs, invalid) }
    }

    @Test fun partialImportKeepsOnlyValidatedKnownKeys() {
        val input = """{"format":"hyperchange-lite","version":1,"settings":{"radius":12.25,"old_feature":true}}"""
        val decoded = SettingsCodec.decode(specs, input)
        assertEquals(setOf("radius"), decoded.keys)
        assertEquals(12.5f, decoded["radius"])
    }

    @Test fun wrongFormatsAndNonFiniteValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SettingsCodec.decode(specs, """{"format":"hyperlight","version":1,"settings":{}}""") }
        assertThrows(IllegalArgumentException::class.java) { specs[1].validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { specs[0].validate(1) }
    }

    @Test fun oldVolumeOffsetAliasMigratesToPhysicalEdgeGapAndKeepsLegacyScopes() {
        for ((offset, expected) in listOf(-50 to 0f, 0 to 6f, 50 to 56f)) {
            val json = """{"format":"hyperchange-lite","version":1,"settings":{"volume_offset_x":$offset}}"""
            val values = SettingsCodec.decode(LiteCatalog.entries, json)
            assertEquals(expected, values[SettingsMigration.EDGE_GAP_KEY])
            assertFalse(SettingsMigration.LEGACY_VOLUME_OFFSET_KEY in values)
            assertEquals(true, values[SettingsMigration.MEDIA_KEY])
            assertEquals(true, values[SettingsMigration.LOCKSCREEN_KEY])
        }
    }

    @Test fun invalidLegacyVolumeOffsetsAreRejectedBeforeReturningAnyMutation() {
        for (offset in listOf("-51", "51", "true", "null", "\"12\"", "1e1000", "\"NaN\"")) {
            val json = """{"format":"hyperchange-lite","version":1,"settings":{"glass_enabled":true,"volume_offset_x":$offset}}"""
            assertThrows(offset, IllegalArgumentException::class.java) {
                SettingsCodec.decode(LiteCatalog.entries, json)
            }
        }
    }

    @Test fun explicitNewVolumeGapIgnoresTheRetiredAlias() {
        for (alias in listOf("50", "\"invalid\"", "null")) {
            val json = """{"format":"hyperchange-lite","version":1,"settings":{"volume_edge_gap":0,"volume_offset_x":$alias}}"""
            val values = SettingsCodec.decode(LiteCatalog.entries, json)
            assertEquals(0f, values[SettingsMigration.EDGE_GAP_KEY])
            assertFalse(SettingsMigration.LEGACY_VOLUME_OFFSET_KEY in values)
        }
        for (gap in listOf("-1", "201", "true", "\"6\"")) {
            val json = """{"format":"hyperchange-lite","version":1,"settings":{"volume_edge_gap":$gap,"volume_offset_x":0}}"""
            assertThrows(IllegalArgumentException::class.java) { SettingsCodec.decode(LiteCatalog.entries, json) }
        }
    }

    @Test fun unrelatedPartialPresetsDoNotResetTheExistingVolumePosition() {
        val json = """{"format":"hyperchange-lite","version":1,"settings":{"glass_background_blur":120}}"""
        val values = SettingsCodec.decode(LiteCatalog.entries, json)
        assertFalse(SettingsMigration.EDGE_GAP_KEY in values)
        assertEquals(true, values[SettingsMigration.MEDIA_KEY])
        assertEquals(true, values[SettingsMigration.LOCKSCREEN_KEY])
        assertTrue(SettingsCodec.decode(LiteCatalog.entries,
            """{"format":"hyperchange-lite","version":1,"settings":{}}""").isEmpty())
        val edgeOnly = LiteCatalog.entries.filter { it.key == SettingsMigration.EDGE_GAP_KEY }
        val oldPosition = """{"format":"hyperchange-lite","version":1,"settings":{"volume_offset_x":9}}"""
        assertEquals(mapOf(SettingsMigration.EDGE_GAP_KEY to 15f), SettingsCodec.decode(edgeOnly, oldPosition))
    }

    @Test fun exportPublishesOnlyTheNewVolumePositionKey() {
        val json = SettingsCodec.encode(LiteCatalog.entries, mapOf(SettingsMigration.LEGACY_VOLUME_OFFSET_KEY to 10f))
        val values = JSONObject(json).getJSONObject("settings")
        assertFalse(values.has(SettingsMigration.LEGACY_VOLUME_OFFSET_KEY))
        assertEquals(16.0, values.getDouble(SettingsMigration.EDGE_GAP_KEY), 0.0)
        assertEquals(16f, SettingsCodec.decode(LiteCatalog.entries, json)[SettingsMigration.EDGE_GAP_KEY])
    }
}
