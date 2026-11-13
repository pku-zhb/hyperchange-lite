// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import org.junit.Assert.*
import org.junit.Test

class SettingsMigrationTest {
    private val media = SettingsMigration.MEDIA_KEY
    private val lockscreen = SettingsMigration.LOCKSCREEN_KEY
    private val edge = SettingsMigration.EDGE_GAP_KEY
    private val offset = SettingsMigration.LEGACY_VOLUME_OFFSET_KEY
    private val marker = SettingsMigration.VERSION_KEY

    private fun assertRanges(values: Map<String, *>, music: Boolean, locked: Boolean) {
        assertEquals(music, values[media])
        assertEquals(locked, values[lockscreen])
    }

    @Test fun freshStorageAndUnrelatedMetadataKeepConservativeDefaults() {
        assertRanges(SettingsMigration.resolve(emptyMap<String, Any>()), false, false)
        assertEquals(6f, SettingsMigration.resolve(emptyMap<String, Any>())[edge])
        assertRanges(SettingsMigration.resolve(mapOf("_initialized" to false, "unknown" to true)), false, false)
        assertFalse(SettingsMigration.hasLocalState(emptyMap<String, Any>()))
    }

    @Test fun legacyCombinedScopeTrueAndFalseMigrateIndependentlyFromLockscreen() {
        for (old in listOf(false, true)) {
            val values = SettingsMigration.resolve(mapOf("glass_notifications" to old))
            assertRanges(values, old, true)
            assertEquals(old, values["glass_notifications"])
            assertEquals(SettingsMigration.VERSION, values[marker])
        }
    }

    @Test fun oldInitializedOrPartialStorageUsesOldDefaultsForMissingScope() {
        assertRanges(SettingsMigration.resolve(mapOf("_initialized" to true)), true, true)
        assertRanges(SettingsMigration.resolve(mapOf("notification_press_tilt_enabled" to true)), true, true)
        assertRanges(SettingsMigration.resolve(mapOf("glass_unify_notifications" to true, "glass_enabled" to false)), true, true)
    }

    @Test fun explicitNewKeysWinAcrossLegacyValuesAndRepeatedResolution() {
        for (music in listOf(false, true)) for (locked in listOf(false, true)) {
            val original = mapOf("glass_notifications" to !music, media to music, lockscreen to locked)
            val once = SettingsMigration.resolve(original)
            assertRanges(once, music, locked)
            assertEquals(once, SettingsMigration.resolve(once))
            assertEquals(original.keys + setOf(marker, edge), once.keys)
        }
    }

    @Test fun partiallyMigratedKeysKeepExplicitChoiceAndResolveOnlyMissingRange() {
        assertRanges(SettingsMigration.resolve(mapOf("glass_notifications" to true, media to false)), false, true)
        assertRanges(SettingsMigration.resolve(mapOf("glass_notifications" to false, lockscreen to false)), false, false)
        assertRanges(SettingsMigration.resolve(mapOf(media to true)), true, false)
    }

    @Test fun persistedMigrationMarkerPreventsLegacyFallbackFromRunningAgain() {
        assertRanges(SettingsMigration.resolve(mapOf(marker to 1, "_initialized" to true, "glass_notifications" to true)), false, false)
        assertRanges(SettingsMigration.resolve(mapOf(marker to 2, "glass_notifications" to true)), false, false)
    }

    @Test fun firstBindingRestoresLegacyRemoteAfterAppPreparedFreshDefaults() {
        val freshLocal = SettingsMigration.resolve(emptyMap<String, Any>())
        assertFalse(SettingsMigration.hasLocalState(freshLocal))
        val oldRemote = mapOf("glass_notifications" to false, "notification_press_tilt_enabled" to true)
        val restored = SettingsMigration.forBinding(freshLocal, oldRemote)
        assertRanges(restored, false, true)
        assertEquals(true, restored["notification_press_tilt_enabled"])
        assertRanges(SettingsMigration.forBinding(freshLocal, emptyMap<String, Any>()), false, false)
    }

    @Test fun preparedFreshPositionDefaultDoesNotHideRemotePositionOnFirstBinding() {
        val freshLocal = SettingsMigration.resolve(emptyMap<String, Any>())
        assertEquals(6f, freshLocal[edge])
        assertFalse(SettingsMigration.hasLocalState(freshLocal))
        val restored = SettingsMigration.forBinding(freshLocal, mapOf(edge to 27f))
        assertEquals(27f, restored[edge])
        assertEquals(22f, SettingsMigration.forBinding(freshLocal, mapOf(offset to 16f))[edge])
    }

    @Test fun legacyOffsetAloneOwnsLocalStateAndMigratesEvenWithTheExistingMarker() {
        val remote = mapOf(edge to 90f)
        for ((legacy, expected) in listOf(-50f to 0f, 0f to 6f, 50f to 56f)) {
            val old = mapOf(offset to legacy)
            assertTrue(SettingsMigration.hasLocalState(old))
            val resolved = SettingsMigration.resolve(old)
            assertEquals(expected, resolved[edge])
            assertTrue(SettingsMigration.hasLocalState(resolved))
            assertEquals(expected, SettingsMigration.forBinding(old, remote)[edge])
            assertRanges(resolved, true, true)
            assertEquals(expected, SettingsMigration.resolve(old + (marker to 1))[edge])
        }
    }

    @Test fun explicitEdgeGapWinsOverLegacyOffsetAndResolutionIsIdempotent() {
        for (gap in listOf(0f, 27f, 200f)) {
            val resolved = SettingsMigration.resolve(mapOf(edge to gap, offset to 50f))
            assertEquals(gap, resolved[edge])
            assertEquals(resolved, SettingsMigration.resolve(resolved))
        }
        assertEquals(200f, SettingsMigration.resolve(mapOf(offset to 300f))[edge])
        assertEquals(6f, SettingsMigration.resolve(mapOf(edge to Float.NaN, offset to 20f))[edge])
        assertEquals(6f, SettingsMigration.resolve(mapOf(edge to "invalid", offset to 20f))[edge])
        assertEquals(1, SettingsMigration.VERSION)
    }

    @Test fun existingLocalOrOfflineEditsWinOverRemoteOnBinding() {
        val remote = mapOf("glass_notifications" to true, media to true, lockscreen to true)
        val edited = mapOf("_initialized" to true, marker to 1, media to false, lockscreen to false)
        assertRanges(SettingsMigration.forBinding(edited, remote), false, false)
        val oldWithoutInitialized = mapOf("glass_notifications" to false)
        assertTrue(SettingsMigration.hasLocalState(oldWithoutInitialized))
        assertRanges(SettingsMigration.forBinding(oldWithoutInitialized, remote), false, true)
        val explicitNew = mapOf(media to false, lockscreen to false)
        assertTrue(SettingsMigration.hasLocalState(explicitNew))
        assertRanges(SettingsMigration.forBinding(explicitNew, remote), false, false)
    }

    @Test fun oldPresetImportsMigrateBothCombinedValuesAndMissingKeyDefaults() {
        for (old in listOf(false, true)) {
            val json = """{"format":"hyperchange-lite","version":1,"settings":{"glass_notifications":$old}}"""
            val values = SettingsCodec.decode(LiteCatalog.entries, json)
            assertRanges(values, old, true)
            assertFalse(marker in values)
        }
        val partial = """{"format":"hyperchange-lite","version":1,"settings":{"glass_background_blur":120}}"""
        assertRanges(SettingsCodec.decode(LiteCatalog.entries, partial), true, true)
    }

    @Test fun presetExplicitNewKeysWinAndInvalidRangeTypeFailsBeforeMutation() {
        val json = """{"format":"hyperchange-lite","version":1,"settings":{"glass_notifications":true,"glass_media":false,"notification_lockscreen_effects":false}}"""
        assertRanges(SettingsCodec.decode(LiteCatalog.entries, json), false, false)
        val invalid = """{"format":"hyperchange-lite","version":1,"settings":{"glass_notifications":true,"glass_media":1}}"""
        assertThrows(IllegalArgumentException::class.java) { SettingsCodec.decode(LiteCatalog.entries, invalid) }
        val unknown = """{"format":"hyperchange-lite","version":1,"settings":{"removed_setting":true}}"""
        assertTrue(SettingsCodec.decode(LiteCatalog.entries, unknown).isEmpty())
    }

    @Test fun resetAndExportImportPreserveConservativeDefaultsAndExplicitScopeChoices() {
        val defaults = SettingsCodec.defaults(LiteCatalog.entries)
        assertRanges(defaults, false, false)
        for (spec in LiteCatalog.entries) {
            val raw = spec.defaultValue
            val canonical = defaults.getValue(spec.key)
            if (raw is Float) {
                // Existing decimal steps can differ by one Float ULP, never a full step.
                assertEquals(spec.key, raw, canonical as Float, Math.ulp(raw))
            } else assertEquals(spec.key, raw, canonical)
        }
        val resetRoundTrip = SettingsCodec.decode(LiteCatalog.entries, SettingsCodec.encode(LiteCatalog.entries, defaults))
        assertEquals(defaults, resetRoundTrip)
        assertEquals(resetRoundTrip, SettingsCodec.decode(LiteCatalog.entries,
            SettingsCodec.encode(LiteCatalog.entries, resetRoundTrip)))
        assertRanges(SettingsCodec.decode(LiteCatalog.entries,
            SettingsCodec.encode(LiteCatalog.entries, emptyMap<String, Any>())), false, false)
        val legacyExport = SettingsCodec.encode(LiteCatalog.entries, mapOf("glass_notifications" to false))
        assertRanges(SettingsCodec.decode(LiteCatalog.entries, legacyExport), false, true)
    }
}
