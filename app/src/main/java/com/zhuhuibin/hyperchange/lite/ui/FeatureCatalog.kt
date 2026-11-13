// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting

/** Navigation owns presentation only; persisted setting names remain unchanged. */
enum class Feature(val title: String, val summary: String) {
    MATERIAL("材质与圆角", "选择生效位置，调整玻璃质感与卡片圆角"),
    NOTIFICATIONS("通知动画", "按压反馈、悬浮通知、增删动画与分组"),
    VOLUME("音量面板", "长按展开、音量条尺寸与弹出动画");

    val settings: List<LiteSetting> get() = LiteCatalog.entries.filter { owner(it) == this }

    companion object {
        fun owner(setting: LiteSetting): Feature = when {
            MaterialCategory.ownerOrNull(setting) != null -> MATERIAL
            setting.key.startsWith("notification_") -> NOTIFICATIONS
            setting.key.startsWith("volume_") -> VOLUME
            else -> error("Setting has no feature destination: ${setting.key}")
        }
    }
}
