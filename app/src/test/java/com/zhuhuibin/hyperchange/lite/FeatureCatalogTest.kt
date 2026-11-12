package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.ui.Feature
import com.zhuhuibin.hyperchange.lite.ui.MaterialCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureCatalogTest {
    @Test fun everyExistingSettingIsReachableThroughExactlyOneFeature() {
        val destinations = Feature.entries.flatMap { it.settings }
        assertTrue(Feature.entries.all { it.settings.isNotEmpty() })
        assertEquals(LiteCatalog.entries.map { it.key }.toSet(), destinations.map { it.key }.toSet())
        assertEquals(LiteCatalog.entries.size, destinations.size)
    }

    @Test(expected = IllegalStateException::class)
    fun newUnassignedSettingsCannotDisappearSilentlyFromTheApp() {
        Feature.owner(LiteSetting.toggle("unassigned_feature", "New setting", "New group"))
    }

    @Test fun materialParametersHaveOneEditorDestination() {
        val editorSettings = MaterialCategory.entries.flatMap { it.settings }
        val materialKeys = editorSettings.map { it.key }
        assertEquals(Feature.MATERIAL.settings.map { it.key }.toSet(), materialKeys.toSet())
        assertEquals(materialKeys.distinct().size, materialKeys.size)
        assertTrue(MaterialCategory.entries.filter { it != MaterialCategory.PRESETS }.all { it.settings.isNotEmpty() })
        assertTrue(LiteCatalog.entries.filter { it.key.startsWith("glass_") }.all { Feature.owner(it) == Feature.MATERIAL })
    }

    @Test fun notificationAndVolumeBehaviorRemainOutsideTheMaterialEditor() {
        val behaviorKeys = setOf(
            "notification_heads_up_pop_blur", "notification_stack_anim_add_alpha",
            "notification_press_tilt_enabled", "notification_group_stack_enabled",
            "volume_motion_enabled", "volume_long_press", "volume_height", "volume_offset_x",
        )
        val behaviorSettings = LiteCatalog.entries.filter { it.key in behaviorKeys }
        assertEquals(behaviorKeys, behaviorSettings.map { it.key }.toSet())
        behaviorSettings.forEach { setting ->
            assertNull(MaterialCategory.ownerOrNull(setting))
            assertEquals(if (setting.key.startsWith("notification_")) Feature.NOTIFICATIONS else Feature.VOLUME, Feature.owner(setting))
        }
    }

    @Test fun sharedLockscreenScopeAndAllRadiusOverridesBelongToTheEditor() {
        val materialKeys = setOf(
            SettingsMigration.LOCKSCREEN_KEY, "notification_radius_enabled", "notification_radius",
            "cc_radius_enabled", "cc_device_radius", "cc_secondary_radius",
            "volume_corner_enabled", "volume_corner", "volume_timer_corner_enabled", "volume_timer_corner",
        )
        val materialSettings = LiteCatalog.entries.filter { it.key in materialKeys }
        assertEquals(materialKeys, materialSettings.map { it.key }.toSet())
        materialSettings.forEach { setting ->
            assertEquals(Feature.MATERIAL, Feature.owner(setting))
        }
        assertEquals(MaterialCategory.SCOPES, MaterialCategory.ownerOrNull(LiteCatalog.entries.first { it.key == SettingsMigration.LOCKSCREEN_KEY }))
    }
}
