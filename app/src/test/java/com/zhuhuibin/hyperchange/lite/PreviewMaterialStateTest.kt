package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.glass.SoftGlassSettings
import com.zhuhuibin.hyperchange.lite.preview.previewMaterialState
import com.zhuhuibin.hyperchange.lite.preview.previewRadius
import com.zhuhuibin.hyperchange.lite.preview.previewSamplingInputs
import org.junit.Assert.*
import org.junit.Test

class PreviewMaterialStateTest {
    private val saved = SoftGlassSettings(
        enabled = true, backgroundBlur = 160, backgroundScale = 60, backgroundOpacity = 130,
        adjustments = mapOf("refraction" to 50),
    )

    @Test fun nativeSamplingRatioKeepsTheOemBaselineWithoutInventingImageZoom() {
        assertEquals(0f, previewSamplingInputs(saved.copy(backgroundScale = 0), false).backgroundScale, 0f)
        assertEquals(.075f, previewSamplingInputs(saved.copy(backgroundScale = 100), false).backgroundScale, .00001f)
        assertEquals(.15f, previewSamplingInputs(saved.copy(backgroundScale = 200), false).backgroundScale, .00001f)
    }

    @Test fun eachSystemSceneHonorsItsOwnMaterialRange() {
        val sceneKeys = mapOf(
            "NOTIFICATION" to "glass_notifications", "HEADS_UP" to "glass_notifications",
            "MEDIA" to SettingsMigration.MEDIA_KEY, "CONTROL" to "glass_control_center",
            "VOLUME" to "glass_volume",
        )
        sceneKeys.forEach { (scene, key) ->
            val off = previewMaterialState(scene, mapOf(key to false), saved, true)
            assertFalse("$scene must pause while its range is off", off.settings.enabled)
            assertEquals(saved.adjustments, off.settings.adjustments)
            val on = previewMaterialState(scene, mapOf(key to true), saved, true)
            assertTrue("$scene must permit tuning in its enabled range", on.settings.enabled)
        }
    }

    @Test fun wholePreviewUsesItsOwnValuesAndEnableFlagWhenCardValuesDiffer() {
        val values = mapOf<String, Any>(
            SettingsMigration.BACKDROP_VERSION_KEY to 1,
            SettingsMigration.BACKDROP_ENABLED_KEY to true,
            "glass_enabled" to false, "glass_shade" to false,
            "glass_background_blur" to 160f, "glass_background_scale" to 60f, "glass_background_opacity" to 130f,
            "glass_shade_blur" to 35f, "glass_shade_scale" to 24f, "glass_shade_opacity" to 75f,
        )
        val shade = previewMaterialState("SHADE", values, saved.copy(enabled = false), true)
        assertTrue(shade.settings.enabled)
        assertEquals(35, shade.settings.backgroundBlur)
        assertEquals(100, shade.settings.backgroundScale)
        assertEquals(75, shade.settings.backgroundOpacity)
        assertEquals(100, shade.settings.refractionBlur)
        assertTrue(shade.settings.adjustments.values.all { it == 0 })
        assertTrue(shade.scopeNote.contains("不使用卡片折射"))
        val card = previewMaterialState("NOTIFICATION", values, saved, true)
        assertEquals(160, card.settings.backgroundBlur)
        assertEquals(60, card.settings.backgroundScale)
        assertEquals(130, card.settings.backgroundOpacity)
        assertEquals(saved.adjustments, card.settings.adjustments)
    }

    @Test fun wholeToggleDoesNotFollowCardMasterOrForcedLocalCardPreviewMode() {
        val values = mapOf<String, Any>(
            SettingsMigration.BACKDROP_VERSION_KEY to 1,
            SettingsMigration.BACKDROP_ENABLED_KEY to false,
            "glass_enabled" to true, "glass_shade" to true,
            "glass_shade_blur" to 35f,
        )
        for (respectScopes in listOf(false, true)) {
            val shade = previewMaterialState("SHADE", values, saved, respectScopes)
            assertFalse(shade.settings.enabled)
            assertEquals(35, shade.settings.backgroundBlur)
            assertTrue(shade.scopeStatus.contains("已关闭"))
        }
        assertFalse(previewMaterialState("SHADE", emptyMap<String, Any>(), saved, true).settings.enabled)
    }

    @Test fun wholePreviewUsesTheSameLegacyMigrationAsTheRuntime() {
        val old = mapOf("glass_enabled" to true, "glass_shade" to true,
            "glass_background_blur" to 42f, "glass_background_scale" to 18f, "glass_background_opacity" to 67f)
        val shade = previewMaterialState("SHADE", old, saved.copy(enabled = false), true)
        assertTrue(shade.settings.enabled)
        assertEquals(42, shade.settings.backgroundBlur)
        assertEquals(100, shade.settings.backgroundScale)
        assertEquals(67, shade.settings.backgroundOpacity)
        assertFalse(previewMaterialState("SHADE", old + ("glass_shade" to false), saved, true).settings.enabled)
    }

    @Test fun nativeWholeSamplingInputsCannotAcquireCardRefraction() {
        val inputs = previewSamplingInputs(saved.copy(refractionBlur = 190), wholeWindow = true)
        assertEquals(64, inputs.backgroundBlur)
        assertEquals(.045f, inputs.backgroundScale, .000001f)
        assertEquals(130, inputs.opacity)
        assertNull(inputs.smallRefractionBlur)
        assertNull(inputs.bigRefractionBlur)
        val card = previewSamplingInputs(saved.copy(refractionBlur = 190), wholeWindow = false)
        assertEquals(68, card.smallRefractionBlur)
        assertEquals(137, card.bigRefractionBlur)
        val disabled = previewSamplingInputs(saved.copy(enabled = false), wholeWindow = true)
        assertEquals(40, disabled.backgroundBlur)
        assertEquals(.075f, disabled.backgroundScale, 0f)
        assertEquals(100, disabled.opacity)
        assertNull(disabled.smallRefractionBlur)
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
