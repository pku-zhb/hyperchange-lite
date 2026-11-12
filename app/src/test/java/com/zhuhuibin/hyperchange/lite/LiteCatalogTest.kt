package com.zhuhuibin.hyperchange.lite

import org.junit.Assert.*
import org.junit.Test

class LiteCatalogTest {
    @Test fun completePresetRoundTripPreservesAllExposedSettings() {
        val specs = LiteCatalog.entries
        assertTrue(specs.isNotEmpty())
        assertEquals(specs.size, specs.map { it.key }.distinct().size)
        val defaults = specs.associate { it.key to it.validate(it.defaultValue) }
        assertEquals(defaults, SettingsCodec.decode(specs, SettingsCodec.encode(specs, defaults)))
    }

    @Test fun freshInstallHasNoAutomaticSystemBehavior() {
        val defaults = LiteCatalog.entries.associate { it.key to it.defaultValue }
        listOf("glass_enabled", "glass_unify_notifications", "glass_assistant", "glass_sidebar",
            "cc_radius_enabled", "volume_motion_enabled", "notification_press_tilt_enabled",
            "notification_heads_up_pop_enabled", "notification_stack_anim_enabled", "notification_group_stack_enabled"
        ).forEach { assertEquals(it, false, defaults[it]) }
    }
}
