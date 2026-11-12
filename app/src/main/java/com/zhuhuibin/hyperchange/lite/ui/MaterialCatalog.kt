// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.SettingsMigration

/** Editor categories change presentation only; preference keys and scope semantics stay intact. */
enum class MaterialCategory(val title: String, val summary: String) {
    PRESETS("预设", "试用原版材质，随时还原"),
    SCOPES("作用范围", "柔光开关、接管范围与统一材质"),
    SURFACE("材质细调", "采样、折射、光照与表面混色"),
    BACKGROUND("下拉背景", "背景模糊、采样与混色"),
    NOTIFICATION("通知圆角", "普通通知卡片的圆角"),
    CONTROL("控制中心圆角", "磁贴、滑条与面板的圆角"),
    VOLUME("音量圆角", "侧边音量条与定时条的圆角"),
    ASSISTANT("负一屏与侧边栏", "组件与侧栏卡片的材质范围");

    val settings: List<LiteSetting>
        get() = LiteCatalog.entries.filter { ownerOrNull(it) == this }

    companion object {
        private val sharedScopeKeys = setOf(
            "glass_enabled", "glass_notifications", SettingsMigration.MEDIA_KEY,
            "glass_control_center", "glass_volume", "glass_shade", "glass_unify_notifications",
            SettingsMigration.LOCKSCREEN_KEY,
        )
        private val notificationKeys = setOf("notification_radius_enabled", "notification_radius")
        private val volumeKeys = setOf(
            "volume_corner_enabled", "volume_corner", "volume_timer_corner_enabled", "volume_timer_corner",
        )

        /** Shared lockscreen scope appears once here, while notification behavior stays independent. */
        fun ownerOrNull(setting: LiteSetting): MaterialCategory? = when {
            setting.key in sharedScopeKeys -> SCOPES
            setting.key.startsWith("glass_assistant") || setting.key.startsWith("glass_sidebar") -> ASSISTANT
            setting.key.startsWith("glass_background_") -> BACKGROUND
            setting.key.startsWith("glass_") -> SURFACE
            setting.key in notificationKeys -> NOTIFICATION
            setting.key.startsWith("cc_") -> CONTROL
            setting.key in volumeKeys -> VOLUME
            else -> null
        }
    }
}
