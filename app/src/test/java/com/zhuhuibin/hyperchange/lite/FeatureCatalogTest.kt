package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.ui.Feature
import com.zhuhuibin.hyperchange.lite.ui.MaterialCategory
import com.zhuhuibin.hyperchange.lite.ui.settingDisabledReason
import com.zhuhuibin.hyperchange.lite.ui.settingSectionTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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
            "volume_motion_enabled", "volume_long_press", "volume_height", "volume_edge_gap",
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

    @Test fun inactiveChildrenExplainTheirParentWithoutChangingStoredValues() {
        val values = mutableMapOf<String, Any>("notification_press_tilt_enabled" to false,
            "notification_press_tilt_scale" to .93f)
        val scale = LiteCatalog.entries.first { it.key == "notification_press_tilt_scale" }
        assertNotNull(settingDisabledReason(scale, values))
        assertEquals(.93f, values[scale.key])
        values["notification_press_tilt_enabled"] = true
        assertNull(settingDisabledReason(scale, values))
        LiteCatalog.entries.forEach { settingDisabledReason(it, emptyMap<String, Any>()) }
    }

    @Test fun curveEditorsFollowTheSelectedAnimationMode() {
        val values = mutableMapOf<String, Any>("notification_heads_up_pop_enabled" to true,
            "notification_heads_up_pop_spring" to false, "notification_heads_up_pop_exit_spring" to true)
        fun reason(key: String) = settingDisabledReason(LiteCatalog.entries.first { it.key == key }, values)
        assertNull(reason("notification_heads_up_pop_curve_x1"))
        assertNotNull(reason("notification_heads_up_pop_damping"))
        assertNotNull(reason("notification_heads_up_pop_exit_curve_x1"))
        assertNull(reason("notification_heads_up_pop_exit_damping"))
        values["notification_heads_up_pop_enabled"] = false
        assertNotNull(reason("notification_heads_up_pop_curve_x1"))
        assertNotNull(reason("notification_heads_up_pop_exit_spring"))
    }

    @Test fun independentMaterialRangesAndCornersDoNotRequireTheSystemTuningSwitch() {
        val values = mapOf<String, Any>("glass_enabled" to false, "glass_assistant" to true,
            "cc_radius_enabled" to true, "glass_shade" to false)
        fun reason(key: String) = settingDisabledReason(LiteCatalog.entries.first { it.key == key }, values)
        assertNull(reason("glass_refraction_blur"))
        assertNull(reason("cc_card_radius"))
        assertNull(reason("glass_unify_notifications"))
        assertNotNull(reason("glass_background_blur"))
        assertNull(settingDisabledReason(LiteCatalog.entries.first { it.key == "glass_background_blur" },
            values + ("glass_enabled" to true)))
    }

    @Test fun headsUpAdvancedControlsHaveExplicitSectionsInsteadOfArbitraryTruncation() {
        fun section(key: String) = settingSectionTitle(LiteCatalog.entries.first { it.key == key })
        assertEquals(section("notification_heads_up_pop_enabled"), section("notification_heads_up_pop_exit_scale"))
        assertEquals(section("notification_heads_up_pop_exit_spring"), section("notification_heads_up_pop_exit_curve_y2"))
        assertEquals(section("notification_heads_up_pop_spring"), section("notification_heads_up_pop_curve_x1"))
        assertEquals(section("notification_heads_up_pop_blur"), section("notification_heads_up_pop_flip"))
        assertTrue(section("notification_heads_up_pop_enabled") != section("notification_heads_up_pop_curve_x1"))
    }

}
