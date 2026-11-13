// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MaterialLayerMigrationTest {
    private val marker = SettingsMigration.BACKDROP_VERSION_KEY
    private val whole = SettingsMigration.backdropKeys
    private val shared = mapOf(
        "glass_background_blur" to 35f,
        "glass_background_scale" to 72f,
        "glass_background_opacity" to 58f,
    )
    private val inherited = mapOf(
        "glass_shade_blur" to 35f,
        "glass_shade_scale" to 72f,
        "glass_shade_opacity" to 58f,
        SettingsMigration.BACKDROP_ENABLED_KEY to false,
    )
    private val wholeDefaults = SettingsMigration.backdropValueKeys.associateWith { 100f } +
        (SettingsMigration.BACKDROP_ENABLED_KEY to false)

    private fun json(values: Map<String, *>): String = JSONObject()
        .put("format", "hyperchange-lite").put("version", 1)
        .put("settings", JSONObject(values)).toString()

    @Test fun oldStorageCopiesSharedValuesOnceIndependentlyOfTheScopeMarker() {
        val old = shared + (SettingsMigration.VERSION_KEY to SettingsMigration.VERSION)
        val migrated = SettingsMigration.resolve(old)
        assertEquals(inherited, migrated.filterKeys { it in whole })
        assertEquals(SettingsMigration.BACKDROP_VERSION, migrated[marker])
        assertEquals(migrated, SettingsMigration.resolve(migrated))
        val cardEdited = SettingsMigration.resolve(migrated + mapOf("glass_background_blur" to 170f))
        assertEquals(35f, cardEdited["glass_shade_blur"])
    }

    @Test fun explicitWholeValuesWinAndMissingNewFormatValuesKeepNativeDefaults() {
        val old = shared + mapOf("glass_shade_blur" to 90f)
        assertEquals(90f, SettingsMigration.resolve(old)["glass_shade_blur"])
        val newFormat = shared + mapOf(marker to 1, "glass_shade_scale" to 22f)
        val resolved = SettingsMigration.resolve(newFormat)
        assertEquals(100f, resolved["glass_shade_blur"])
        assertEquals(22f, resolved["glass_shade_scale"])
        assertEquals(100f, resolved["glass_shade_opacity"])
        assertEquals(false, resolved[SettingsMigration.BACKDROP_ENABLED_KEY])
    }

    @Test fun wholeEnableMigrationPreservesTheOldEffectiveMasterAndScopeOnce() {
        for (master in listOf(false, true)) for (scope in listOf(false, true)) {
            val old = shared + mapOf("glass_enabled" to master, "glass_shade" to scope)
            val migrated = SettingsMigration.resolve(old)
            assertEquals(master && scope, migrated[SettingsMigration.BACKDROP_ENABLED_KEY])
            val toggledCard = SettingsMigration.resolve(migrated + mapOf("glass_enabled" to !master, "glass_shade" to !scope))
            assertEquals(master && scope, toggledCard[SettingsMigration.BACKDROP_ENABLED_KEY])
        }
        assertEquals(true, SettingsMigration.resolve(mapOf("glass_enabled" to true))[SettingsMigration.BACKDROP_ENABLED_KEY])
        assertEquals(false, SettingsMigration.resolve(mapOf("glass_enabled" to true,
            SettingsMigration.BACKDROP_ENABLED_KEY to false))[SettingsMigration.BACKDROP_ENABLED_KEY])
    }

    @Test fun freshMigrationDefaultsDoNotHideRemoteUserValuesOnFirstBinding() {
        val local = SettingsMigration.resolve(emptyMap<String, Any>())
        assertEquals(wholeDefaults, local.filterKeys { it in whole })
        assertFalse(SettingsMigration.hasLocalState(local))
        val restored = SettingsMigration.forBinding(local, shared)
        assertEquals(inherited, restored.filterKeys { it in whole })
        val edited = local + mapOf("_initialized" to true, "glass_shade_blur" to 25f)
        assertEquals(25f, SettingsMigration.forBinding(edited, shared)["glass_shade_blur"])
    }

    @Test fun oldCompleteExportMigratesWholeValuesButMaterialAndUnrelatedPartialsDoNot() {
        val oldExport = SettingsCodec.defaults(LiteCatalog.entries).filterKeys { it !in whole } + shared
        val imported = SettingsCodec.decode(LiteCatalog.entries, json(oldExport))
        assertEquals(inherited, imported.filterKeys { it in whole })
        val materialOnly = SettingsCodec.defaults(LiteCatalog.entries.filter { it.key in glassPresetKeys }) + shared
        for (partial in listOf(shared, materialOnly, mapOf("notification_press_tilt_enabled" to true))) {
            val values = SettingsCodec.decode(LiteCatalog.entries, json(partial))
            assertTrue(values.keys.none { it in whole })
        }
    }

    @Test fun olderCompleteExportsWithoutTheLaterScopeAndPositionKeysStillMigrate() {
        val omitted = whole + setOf(SettingsMigration.MEDIA_KEY, SettingsMigration.LOCKSCREEN_KEY,
            SettingsMigration.EDGE_GAP_KEY)
        val oldExport = SettingsCodec.defaults(LiteCatalog.entries).filterKeys { it !in omitted } + shared
        val imported = SettingsCodec.decode(LiteCatalog.entries, json(oldExport))
        assertEquals(inherited, imported.filterKeys { it in whole })
    }

    @Test fun partialWholeImportsNeverAcquireCardOrLockscreenScopeWrites() {
        val inputs = listOf(
            mapOf<String, Any>(SettingsMigration.BACKDROP_ENABLED_KEY to true),
            mapOf<String, Any>(SettingsMigration.BACKDROP_ENABLED_KEY to false),
        ) + SettingsMigration.backdropValueKeys.map { mapOf<String, Any>(it to 33f) }
        for (modern in listOf(false, true)) for (input in inputs) {
            val root = JSONObject(json(input))
            if (modern) root.put(SettingsMigration.BACKDROP_JSON_VERSION_KEY, 1)
            val decoded = SettingsCodec.decode(LiteCatalog.entries, root.toString())
            assertEquals("modern=$modern input=$input", input, decoded)
        }
    }

    @Test fun legacyCardPartialsKeepTheirExistingCombinedScopeMigration() {
        val input = mapOf("glass_background_blur" to 35f)
        assertEquals(input + mapOf(SettingsMigration.MEDIA_KEY to true, SettingsMigration.LOCKSCREEN_KEY to true),
            SettingsCodec.decode(LiteCatalog.entries, json(input)))
    }

    @Test fun newExportsKeepBothLayersDistinctAcrossRepeatedImportAndExport() {
        val current = SettingsCodec.defaults(LiteCatalog.entries) + shared + inherited +
            mapOf("glass_shade_blur" to 122f)
        val export = SettingsCodec.encode(LiteCatalog.entries, current)
        assertEquals(1, JSONObject(export).getInt(SettingsMigration.BACKDROP_JSON_VERSION_KEY))
        val imported = SettingsCodec.decode(LiteCatalog.entries, export)
        assertEquals(current, imported)
        assertEquals(imported, SettingsCodec.decode(LiteCatalog.entries,
            SettingsCodec.encode(LiteCatalog.entries, imported)))
    }

    @Test fun newFormatDoesNotReinterpretAMissingWholeKeyAsShared() {
        val full = SettingsCodec.defaults(LiteCatalog.entries).filterKeys { it !in whole } + shared
        val root = JSONObject(json(full)).put(SettingsMigration.BACKDROP_JSON_VERSION_KEY, 1)
        val imported = SettingsCodec.decode(LiteCatalog.entries, root.toString())
        assertTrue(imported.keys.none { it in whole })
    }

    @Test fun wholeTypeAndRangeErrorsRejectTheDocumentBeforeMutation() {
        for (bad in listOf(-1, 201, "50", true, JSONObject.NULL)) {
            val input = json(mapOf("glass_background_blur" to 30, "glass_shade_blur" to bad))
            assertThrows(IllegalArgumentException::class.java) { SettingsCodec.decode(LiteCatalog.entries, input) }
        }
    }

    @Test fun fullResetIncludesWholeDefaultsButCardTrialAndResetDoNot() {
        val defaults = SettingsCodec.defaults(LiteCatalog.entries)
        assertEquals(wholeDefaults, defaults.filterKeys { it in whole })
        val specs = LiteCatalog.entries.filter { it.key in glassPresetKeys }
        assertTrue(glassPresetKeys.none { it in whole })
        assertTrue(SettingsCodec.defaults(specs).keys.none { it in whole })
        val undo = SettingsCodec.decode(specs, SettingsCodec.encode(specs, defaults + inherited))
        assertEquals(glassPresetKeys, undo.keys)
        assertTrue(undo.keys.none { it in whole })
    }
}
