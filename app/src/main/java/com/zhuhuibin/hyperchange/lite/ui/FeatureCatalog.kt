// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting

/** Navigation owns presentation only; persisted setting names remain unchanged. */
enum class Feature(val title: String, val summary: String) {
    GLASS("柔光玻璃", "材质、采样与下拉背景"),
    NOTIFICATIONS("通知", "圆角、按压、悬浮与分组"),
    CONTROL("控制中心", "磁贴、滑条与面板圆角"),
    VOLUME("音量", "外观、长按展开与回弹"),
    ASSISTANT("负一屏与侧边栏", "组件与侧栏卡片材质");

    val settings: List<LiteSetting> get() = LiteCatalog.entries.filter { owner(it) == this }

    companion object {
        fun owner(setting: LiteSetting): Feature = when {
            setting.key.startsWith("glass_assistant") || setting.key.startsWith("glass_sidebar") -> ASSISTANT
            setting.key.startsWith("glass_") -> GLASS
            setting.key.startsWith("notification_") -> NOTIFICATIONS
            setting.key.startsWith("cc_") -> CONTROL
            setting.key.startsWith("volume_") -> VOLUME
            else -> error("Setting has no feature destination: ${setting.key}")
        }
    }
}
