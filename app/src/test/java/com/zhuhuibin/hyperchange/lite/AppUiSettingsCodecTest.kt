// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUiSettingsCodecTest {
    @Test fun oldModuleBackupsKeepTheCurrentAppAppearance() {
        assertNull(AppUiSettingsCodec.fromBackup("""{"format":"hyperchange-lite","version":1,"settings":{}}"""))
    }

    @Test fun themeAndLanguageRoundTripWithoutEnteringModuleSettings() {
        for (theme in 0..5) for (language in listOf("system", "zh-CN", "en")) {
            val saved = AppUiSettings(theme, language, floatingNavigation = true, appBlur = false, liquidNavigation = true)
            val module = JSONObject().put("volume_thin_width", 9)
            val root = JSONObject().put("settings", module).put("appUi", AppUiSettingsCodec.encode(saved))
            assertEquals(saved, AppUiSettingsCodec.fromBackup(root.toString()))
            assertEquals(setOf("volume_thin_width"), module.keys().asSequence().toSet())
        }
    }

    @Test fun oldAppearanceBackupsUseIndependentDefaults() {
        assertEquals(AppUiSettings(2, "en"), AppUiSettingsCodec.fromBackup("""{"appUi":{"version":1,"themeMode":2,"language":"en"}}"""))
    }

    @Test fun wallpaperThemesResolvePreviewBrightnessExplicitly() {
        assertFalse(AppUiSettings(4).isDark(true))
        assertTrue(AppUiSettings(5).isDark(false))
        assertTrue(AppUiSettings(3).isDark(true))
        assertFalse(AppUiSettings(3).isDark(false))
    }

    @Test fun liquidNavigationRemainsAnAppOnlyBoolean() {
        val old = """{"appUi":{"version":1,"themeMode":0,"language":"en","floatingNavigation":true,"appBlur":true}}"""
        assertFalse(requireNotNull(AppUiSettingsCodec.fromBackup(old)).liquidNavigation)
        assertThrows(IllegalArgumentException::class.java) {
            AppUiSettingsCodec.fromBackup(old.replace("\"appBlur\":true", "\"appBlur\":true,\"liquidNavigation\":1"))
        }
    }

    @Test fun invalidAppearanceIsRejectedBeforeTheCallerImportsAnySettings() {
        for (theme in listOf("-1", "6", "1.5", "true", "\"dark\"")) {
            val text = """{"appUi":{"version":1,"themeMode":$theme,"language":"en"}}"""
            assertThrows(IllegalArgumentException::class.java) { AppUiSettingsCodec.fromBackup(text) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppUiSettingsCodec.fromBackup("""{"appUi":{"version":1,"themeMode":0,"language":"xx"}}""")
        }
    }
}
