// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.notifications.BannerContrastPolicy
import com.zhuhuibin.hyperchange.lite.ui.Feature
import org.junit.Assert.*
import org.junit.Test

class BannerReadabilitySettingsTest {
    private val setting get() = LiteCatalog.entries.single { it.key == BannerContrastPolicy.KEY }

    @Test fun experimentHasTheExactOwnerTitleNoSubtitleAndDefaultsOff() {
        assertEquals("横幅通知可读性优化（实验性功能）", setting.title)
        assertEquals("", setting.summary)
        assertEquals(false, setting.defaultValue)
        assertEquals(Feature.NOTIFICATIONS, Feature.owner(setting))
        assertTrue(Feature.NOTIFICATIONS.visibleSettings.contains(setting))
    }

    @Test fun toggleRoundTripsAndResetDisablesItWithoutAcquiringOtherScopes() {
        val specs = LiteCatalog.entries
        val baseline = SettingsCodec.defaults(specs)
        val enabled = baseline + (setting.key to true)
        assertEquals(true, SettingsCodec.decode(specs, SettingsCodec.encode(specs, enabled))[setting.key])
        assertEquals(false, SettingsCodec.defaults(specs)[setting.key])
        val partial = """{"format":"hyperchange-lite","version":1,"settings":{"${setting.key}":true}}"""
        assertEquals(mapOf(setting.key to true), SettingsCodec.decode(specs, partial))
        val newOnly = SettingsMigration.resolve(mapOf(setting.key to true))
        assertEquals(false, newOnly["glass_media"])
        assertEquals(false, newOnly["notification_lockscreen_effects"])
        assertEquals(false, baseline["notification_lockscreen_effects"])
        assertEquals(false, baseline["notification_lockscreen_animations"])
    }
}
