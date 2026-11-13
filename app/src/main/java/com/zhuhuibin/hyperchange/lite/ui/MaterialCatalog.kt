// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.SettingsMigration

internal val cardBackgroundKeys = setOf("glass_background_blur", "glass_background_scale", "glass_background_opacity")

/** Editor categories change presentation only; preference keys and scope semantics stay intact. */
enum class MaterialCategory(val title: String, val feature: Feature) {
    SCOPES("启用与范围", Feature.SCOPES),
    SURFACE("玻璃质感", Feature.MATERIAL),
    BACKGROUND("卡片背景", Feature.MATERIAL),
    SHADE("整屏背景", Feature.MATERIAL),
    PRESETS("卡片预设", Feature.MATERIAL),
    NOTIFICATION("通知", Feature.CORNERS),
    CONTROL("控制中心", Feature.CORNERS),
    VOLUME("音量", Feature.CORNERS);

    val settings: List<LiteSetting>
        get() = LiteCatalog.entries.filter { ownerOrNull(it) == this }

    companion object {
        private val sharedScopeKeys = setOf(
            "glass_enabled", "glass_notifications", SettingsMigration.MEDIA_KEY,
            "glass_control_center", "glass_volume", "glass_shade", "glass_unify_notifications",
            "glass_shade_enabled", "glass_assistant", "glass_sidebar", "glass_sidebar_unify", SettingsMigration.LOCKSCREEN_KEY,
        )
        private val notificationKeys = setOf("notification_radius_enabled", "notification_radius")
        private val volumeKeys = setOf(
            "volume_corner_enabled", "volume_corner", "volume_timer_corner_enabled", "volume_timer_corner",
        )

        /** Shared lockscreen scope appears once here, while notification behavior stays independent. */
        fun ownerOrNull(setting: LiteSetting): MaterialCategory? = when {
            setting.key in sharedScopeKeys -> SCOPES
            setting.key.startsWith("glass_shade_") -> SHADE
            setting.key in cardBackgroundKeys -> BACKGROUND
            setting.key.startsWith("glass_") -> SURFACE
            setting.key in notificationKeys -> NOTIFICATION
            setting.key.startsWith("cc_") -> CONTROL
            setting.key in volumeKeys -> VOLUME
            else -> null
        }
    }
}
