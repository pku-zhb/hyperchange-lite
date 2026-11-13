package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.ui.Feature
import com.zhuhuibin.hyperchange.lite.ui.MaterialCategory
import com.zhuhuibin.hyperchange.lite.ui.settingDisabledReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class FeatureCatalogTest {
    @Test fun everyExistingSettingIsReachableThroughExactlyOneFeature() {
        val destinations = Feature.entries.flatMap { it.settings }
        assertTrue(Feature.entries.all { it.settings.isNotEmpty() })
        assertEquals(LiteCatalog.entries.map { it.key }.toSet(), destinations.map { it.key }.toSet())
        assertEquals(LiteCatalog.entries.size, destinations.size)
    }

    @Test fun deferredGroupingKeysStayImportableWithoutAnyVisibleDestination() {
        val deferred = DeferredSettings.hiddenKeys
        assertTrue(LiteCatalog.entries.map { it.key }.containsAll(deferred))
        val visible = Feature.entries.flatMap { it.visibleSettings }.map { it.key }
        assertEquals(LiteCatalog.entries.map { it.key }.toSet() - deferred, visible.toSet())
        assertEquals(visible.distinct().size, visible.size)
        assertFalse(Feature.NOTIFICATIONS.visibleSettings.any { it.key in deferred })
        assertFalse(Feature.NOTIFICATIONS.visibleSettings.any { it.group == "通知分组" })
        assertFalse(Feature.SCOPES.visibleSettings.any { it.key == "glass_shade" })
        assertTrue(Feature.BACKDROP.visibleSettings.any { it.key == "glass_shade_enabled" })
    }

    @Test(expected = IllegalStateException::class)
    fun newUnassignedSettingsCannotDisappearSilentlyFromTheApp() {
        Feature.owner(LiteSetting.toggle("unassigned_feature", "New setting", "New group"))
    }

    @Test fun materialParametersHaveOneEditorDestination() {
        val editorSettings = MaterialCategory.entries.flatMap { it.settings }
        val materialKeys = editorSettings.map { it.key }
        val destinations = listOf(Feature.SCOPES, Feature.MATERIAL, Feature.BACKDROP, Feature.CORNERS).flatMap { it.settings }
        assertEquals(destinations.map { it.key }.toSet(), materialKeys.toSet())
        assertEquals(materialKeys.distinct().size, materialKeys.size)
        assertTrue(MaterialCategory.entries.filter { it != MaterialCategory.PRESETS }.all { it.settings.isNotEmpty() })
        assertTrue(LiteCatalog.entries.filter { it.key.startsWith("glass_") }.all {
            Feature.owner(it) == when {
                it.key.startsWith("glass_shade_") -> Feature.BACKDROP
                it.isToggle -> Feature.SCOPES
                else -> Feature.MATERIAL
            }
        })
        assertEquals(listOf("启用与范围", "卡片材质", "下拉背景", "圆角", "通知动画", "音量面板"), Feature.entries.map { it.title })
        assertEquals(listOf(MaterialCategory.SCOPES), Feature.SCOPES.categories)
        assertEquals(listOf(MaterialCategory.SURFACE, MaterialCategory.BACKGROUND, MaterialCategory.PRESETS),
            Feature.MATERIAL.categories)
        assertEquals(listOf(MaterialCategory.NOTIFICATION, MaterialCategory.CONTROL, MaterialCategory.VOLUME), Feature.CORNERS.categories)
        assertTrue(Feature.SCOPES.settings.all { it.isToggle })
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

    @Test fun lockscreenScopeAndRadiusOverridesHaveSeparateHomeDestinations() {
        val materialKeys = setOf(
            SettingsMigration.LOCKSCREEN_KEY, "notification_radius_enabled", "notification_radius",
            "cc_radius_enabled", "cc_device_radius", "cc_secondary_radius",
            "volume_corner_enabled", "volume_corner", "volume_timer_corner_enabled", "volume_timer_corner",
        )
        val materialSettings = LiteCatalog.entries.filter { it.key in materialKeys }
        assertEquals(materialKeys, materialSettings.map { it.key }.toSet())
        materialSettings.forEach { setting ->
            assertEquals(if (setting.key == SettingsMigration.LOCKSCREEN_KEY) Feature.SCOPES else Feature.CORNERS,
                Feature.owner(setting))
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

    @Test fun bannerAndNotificationListNamesKeepAllAdvancedControlsInTheirOwnTabs() {
        val banner = Feature.NOTIFICATIONS.visibleSettings.filter { it.key.startsWith("notification_heads_up_") }
        val list = Feature.NOTIFICATIONS.visibleSettings.filter { it.key.startsWith("notification_stack_anim_") }
        assertTrue(banner.isNotEmpty())
        assertTrue(list.isNotEmpty())
        assertEquals(setOf("顶部横幅动画"), banner.map { it.group }.toSet())
        assertEquals(setOf("通知列表动画"), list.map { it.group }.toSet())
        assertEquals("自定义顶部横幅动画", banner.first { it.key == "notification_heads_up_pop_enabled" }.title)
        assertEquals("通知出现与消失动画", list.first { it.key == "notification_stack_anim_enabled" }.title)
        assertTrue(banner.any { it.key == "notification_heads_up_pop_exit_curve_y2" })
    }

    @Test fun cardAndWholeBackgroundParametersNeverShareAnEditorCategory() {
        val card = MaterialCategory.BACKGROUND.settings
        val whole = MaterialCategory.SHADE.settings.filter { !it.isToggle && it.key !in DeferredSettings.hiddenKeys }
        assertEquals(3, card.size)
        assertEquals(setOf("glass_shade_blur", "glass_shade_opacity"), whole.map { it.key }.toSet())
        assertEquals(MaterialCategory.SHADE,
            MaterialCategory.ownerOrNull(LiteCatalog.entries.first { it.key == "glass_shade_enabled" }))
        card.forEach { assertEquals(MaterialCategory.BACKGROUND, MaterialCategory.ownerOrNull(it)) }
        whole.forEach { assertEquals(MaterialCategory.SHADE, MaterialCategory.ownerOrNull(it)) }
        val enabled = mapOf("glass_enabled" to true, "glass_shade_enabled" to false)
        card.forEach { assertNull(settingDisabledReason(it, enabled)) }
        whole.forEach { assertNotNull(settingDisabledReason(it, enabled)) }
        val onlyWhole = mapOf("glass_enabled" to false, "glass_shade_enabled" to true)
        whole.forEach { assertNull(settingDisabledReason(it, onlyWhole)) }
        card.forEach { assertNotNull(settingDisabledReason(it, onlyWhole)) }
        assertEquals(MaterialCategory.SURFACE,
            MaterialCategory.ownerOrNull(LiteCatalog.entries.first { it.key == "glass_background_brightness" }))
    }

}
