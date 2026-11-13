package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.ui.previewTargetForSetting
import com.zhuhuibin.hyperchange.lite.ui.settingDisplayTitle
import org.junit.Assert.*
import org.junit.Test

class AutomaticPreviewTest {
    private fun spec(key: String) = LiteCatalog.entries.first { it.key == key }

    @Test fun everySettingSelectsARepresentativeWithoutASecondPicker() {
        LiteCatalog.entries.filter { it.key !in DeferredSettings.hiddenKeys }.forEach { value ->
            assertEquals(if (value.key.startsWith("cc_")) "" else value.key, previewTargetForSetting(value).focusKey)
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

    @Test fun renamedBannerAndListTabsRouteByStableKeys() {
        val banner = spec("notification_heads_up_pop_enabled")
        val list = spec("notification_stack_anim_enabled")
        assertEquals("顶部横幅动画", banner.group)
        assertEquals("通知列表动画", list.group)
        assertEquals("HEADS_UP", previewTargetForSetting(banner).scene.name)
        assertEquals("NOTIFICATION", previewTargetForSetting(list).scene.name)
    }

    @Test fun controlCornersStartAsACompositeAndOnlyEditsChooseASingleSample() {
        assertEquals("", previewTargetForSetting(spec("cc_radius_enabled")).sampleFocusKey)
        assertEquals("", previewTargetForSetting(spec("cc_media_radius")).sampleFocusKey)
        assertEquals("cc_tile_radius", previewTargetForSetting(spec("cc_tile_radius"),
            editedKey = "cc_tile_radius").sampleFocusKey)
        // Scrolling to another visible parameter preserves the sample selected by the last edit.
        assertEquals("cc_tile_radius", previewTargetForSetting(spec("cc_media_radius"),
            editedKey = "cc_tile_radius").sampleFocusKey)
        assertEquals("", previewTargetForSetting(spec("cc_radius_enabled"),
            editedKey = "cc_radius_enabled").sampleFocusKey)
        assertEquals("", previewTargetForSetting(spec("volume_corner"),
            editedKey = "cc_tile_radius").sampleFocusKey)
    }

    @Test fun wholeBackgroundControlsHaveTheirOwnSceneAndNeverRedirectCardSampling() {
        for (key in listOf("glass_shade_blur", "glass_shade_scale", "glass_shade_opacity")) {
            assertEquals("SHADE", previewTargetForSetting(spec(key)).scene.name)
        }
        assertEquals("NOTIFICATION", previewTargetForSetting(spec("glass_background_blur"),
            mapOf("glass_shade" to true)).scene.name)
        assertEquals("CONTROL", previewTargetForSetting(spec("glass_background_opacity"),
            mapOf("glass_notifications" to false, "glass_control_center" to true)).scene.name)
    }

    @Test fun sharedControlsChooseAnEnabledCoreRange() {
        assertEquals("CONTROL", previewTargetForSetting(spec("glass_refraction"), mapOf(
            "glass_enabled" to true, "glass_notifications" to false,
        )).scene.name)
        assertEquals("WIDGET", previewTargetForSetting(spec("glass_refraction"), mapOf(
            "glass_enabled" to false, "glass_assistant" to true,
        )).scene.name)
        assertEquals("MEDIA", previewTargetForSetting(spec("notification_press_tilt_max_deg"), mapOf(
            "notification_press_tilt_row_enabled" to false, "notification_press_tilt_media_enabled" to true,
        )).scene.name)
        assertEquals("NOTIFICATION", previewTargetForSetting(spec("glass_background_blur"), mapOf(
            "glass_enabled" to true, "glass_shade" to false,
        )).scene.name)
    }

    @Test fun hmcCompatibilityUsesOnlyAShortTitleMarker() {
        assertEquals(spec(SettingsMigration.MEDIA_KEY).title + "（注意兼容）", settingDisplayTitle(spec(SettingsMigration.MEDIA_KEY)))
        assertEquals(spec(SettingsMigration.LOCKSCREEN_KEY).title + "（注意兼容）", settingDisplayTitle(spec(SettingsMigration.LOCKSCREEN_KEY)))
        assertEquals(spec("glass_refraction").title, settingDisplayTitle(spec("glass_refraction")))
        assertEquals("Media material (compatibility)",
            settingDisplayTitle(spec(SettingsMigration.MEDIA_KEY), "Media material", " (compatibility)"))
        assertEquals("Refraction", settingDisplayTitle(spec("glass_refraction"), "Refraction", " (compatibility)"))
    }
    @Test fun scrollingControlCenterRadiusRowsKeepsTheEntirePreviewTargetStable() {
        val first = spec("cc_card_radius")
        val second = spec("cc_slider_radius")
        assertEquals(previewTargetForSetting(first), previewTargetForSetting(second))
        assertEquals(previewTargetForSetting(first, editedKey = first.key),
            previewTargetForSetting(second, editedKey = first.key))
    }

}
