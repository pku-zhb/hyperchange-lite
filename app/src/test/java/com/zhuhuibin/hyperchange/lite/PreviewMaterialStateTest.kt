package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.glass.SoftGlassSettings
import com.zhuhuibin.hyperchange.lite.preview.previewMaterialState
import com.zhuhuibin.hyperchange.lite.preview.previewRadius
import org.junit.Assert.*
import org.junit.Test

class PreviewMaterialStateTest {
    private val saved = SoftGlassSettings(
        enabled = true, backgroundBlur = 160, backgroundScale = 60, backgroundOpacity = 130,
        adjustments = mapOf("refraction" to 50),
    )

    @Test fun eachSystemSceneHonorsItsOwnMaterialRange() {
        val sceneKeys = mapOf(
            "NOTIFICATION" to "glass_notifications", "HEADS_UP" to "glass_notifications",
            "MEDIA" to SettingsMigration.MEDIA_KEY, "CONTROL" to "glass_control_center",
            "VOLUME" to "glass_volume", "SHADE" to "glass_shade",
        )
        sceneKeys.forEach { (scene, key) ->
            val off = previewMaterialState(scene, mapOf(key to false), saved, true)
            assertFalse("$scene must pause while its range is off", off.settings.enabled)
            assertEquals(saved.adjustments, off.settings.adjustments)
            val on = previewMaterialState(scene, mapOf(key to true), saved, true)
            assertTrue("$scene must permit tuning in its enabled range", on.settings.enabled)
        }
    }

    @Test fun unifiedRecipesAndLockscreenScopeDoNotSilentlyEnableTuning() {
        val state = previewMaterialState("MEDIA", mapOf(
            SettingsMigration.MEDIA_KEY to true,
            "glass_unify_notifications" to true,
            SettingsMigration.LOCKSCREEN_KEY to true,
        ), saved.copy(enabled = false), true)
        assertFalse(state.settings.enabled)
        assertTrue(state.scopeNote.contains("统一材质开"))
        assertTrue(state.scopeNote.contains("锁屏范围开"))
        assertTrue(state.scopeNote.contains("未模拟"))
        val headsUp = previewMaterialState("HEADS_UP", emptyMap<String, Any>(), saved, true)
        assertTrue(headsUp.scopeNote.contains("不参与统一材质"))
    }

    @Test fun assistantAndSidebarUseIndependentSwitchesAndKeepTheirBackgroundSampler() {
        listOf("WIDGET" to "glass_assistant", "SIDEBAR" to "glass_sidebar").forEach { (scene, key) ->
            val state = previewMaterialState(scene, mapOf(key to true), saved.copy(enabled = false), true)
            assertTrue(state.settings.enabled)
            assertEquals(100, state.settings.backgroundBlur)
            assertEquals(100, state.settings.backgroundScale)
            assertEquals(100, state.settings.backgroundOpacity)
            assertEquals(saved.adjustments, state.settings.adjustments)
            assertFalse(previewMaterialState(scene, mapOf(key to false), saved, true).settings.enabled)
        }
    }

    @Test fun geometryOverridesOnlyChangeTheirEnabledSamples() {
        val pairs = mapOf(
            "notification_radius" to "notification_radius_enabled",
            "cc_card_radius" to "cc_radius_enabled", "cc_tile_radius" to "cc_radius_enabled",
            "cc_device_radius" to "cc_radius_enabled", "cc_secondary_radius" to "cc_radius_enabled",
            "volume_corner" to "volume_corner_enabled", "volume_timer_corner" to "volume_timer_corner_enabled",
        )
        pairs.forEach { (radius, key) ->
            assertEquals(26f, previewRadius(radius, 8f, 26f, mapOf(key to false), true), 0f)
            assertEquals(8f, previewRadius(radius, 8f, 26f, mapOf(key to true), true), 0f)
            assertEquals(8f, previewRadius(radius, 8f, 26f, mapOf(key to false), false), 0f)
        }
    }
}
