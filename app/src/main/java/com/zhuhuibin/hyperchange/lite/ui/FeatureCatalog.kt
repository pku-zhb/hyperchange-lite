// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting

/** Navigation owns presentation only; persisted setting names remain unchanged. */
enum class Feature(val title: String, val summary: String) {
    MATERIAL("材质编辑", "统一预览、预设、材质与圆角"),
    NOTIFICATIONS("通知行为", "按压、悬浮、显隐与分组"),
    VOLUME("音量行为", "长按展开、位置、尺寸与回弹");

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
