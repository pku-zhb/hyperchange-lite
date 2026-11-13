package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.ui.previewTargetForSetting
import com.zhuhuibin.hyperchange.lite.ui.settingDisplayTitle
import org.junit.Assert.*
import org.junit.Test

class AutomaticPreviewTest {
    private fun spec(key: String) = LiteCatalog.entries.first { it.key == key }

    @Test fun everySettingSelectsARepresentativeWithoutASecondPicker() {
        LiteCatalog.entries.forEach { value ->
            assertEquals(value.key, previewTargetForSetting(value).focusKey)
        }
        assertEquals("NOTIFICATION", previewTargetForSetting(spec("glass_refraction")).scene.name)
        assertEquals("CONTROL", previewTargetForSetting(spec("cc_tile_radius")).scene.name)
        assertEquals("VOLUME", previewTargetForSetting(spec("volume_pop_scale")).scene.name)
        assertEquals("SIDEBAR", previewTargetForSetting(spec("glass_sidebar")).scene.name)
    }

    @Test fun exactExitKeysSurviveRoutingToASharedSurface() {
        val target = previewTargetForSetting(spec("notification_heads_up_pop_exit_damping"))
        assertEquals("HEADS_UP", target.scene.name)
        assertEquals("notification_heads_up_pop_exit_damping", target.focusKey)
        assertEquals("NOTIFICATION", previewTargetForSetting(spec("notification_stack_anim_remove_alpha")).scene.name)
    }

    @Test fun hmcCompatibilityUsesOnlyAShortTitleMarker() {
        assertEquals(spec(SettingsMigration.MEDIA_KEY).title + "（注意兼容）", settingDisplayTitle(spec(SettingsMigration.MEDIA_KEY)))
        assertEquals(spec(SettingsMigration.LOCKSCREEN_KEY).title + "（注意兼容）", settingDisplayTitle(spec(SettingsMigration.LOCKSCREEN_KEY)))
        assertEquals(spec("glass_refraction").title, settingDisplayTitle(spec("glass_refraction")))
    }
}
