// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.SettingsMigration

/** Editor categories change presentation only; preference keys and scope semantics stay intact. */
enum class MaterialCategory(val title: String, val summary: String) {
    SCOPES("启用与范围", "先选择哪些位置使用 Lite 材质；圆角和通知动画有各自的开关"),
    PRESETS("材质预设", "一键试用六套玻璃参数，可还原试用前设置"),
    SURFACE("玻璃质感", "微调玻璃模糊、折射和明暗；共用于已开启的位置"),
    BACKGROUND("背景模糊与混色", "调整已开启系统位置的背景模糊、采样比例与混色浓度"),
    NOTIFICATION("通知圆角", "调整普通通知卡片的圆角，不影响音乐卡片"),
    CONTROL("控制中心圆角", "磁贴、滑条与面板的圆角"),
    VOLUME("音量圆角", "侧边音量条与定时条的圆角"),
    ASSISTANT("负一屏与侧边栏", "独立启用这两个位置，玻璃参数与其他位置共用");

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
