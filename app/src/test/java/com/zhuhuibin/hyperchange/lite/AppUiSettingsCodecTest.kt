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
        for (theme in 0..2) for (language in listOf("system", "zh-CN", "en")) {
            val saved = AppUiSettings(theme, language)
            val module = JSONObject().put("volume_thin_width", 9)
            val root = JSONObject().put("settings", module).put("appUi", AppUiSettingsCodec.encode(saved))
            assertEquals(saved, AppUiSettingsCodec.fromBackup(root.toString()))
            assertEquals(setOf("volume_thin_width"), module.keys().asSequence().toSet())
        }
    }

    @Test fun invalidAppearanceIsRejectedBeforeTheCallerImportsAnySettings() {
        for (theme in listOf("-1", "3", "1.5", "true", "\"dark\"")) {
            val text = """{"appUi":{"version":1,"themeMode":$theme,"language":"en"}}"""
            assertThrows(IllegalArgumentException::class.java) { AppUiSettingsCodec.fromBackup(text) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppUiSettingsCodec.fromBackup("""{"appUi":{"version":1,"themeMode":0,"language":"xx"}}""")
        }
    }
}
