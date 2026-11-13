// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.DeferredSettings

/** Navigation owns presentation only; persisted setting names remain unchanged. */
enum class Feature(val title: String, val summary: String) {
    SCOPES("启用与范围", "选择材质和通知效果的生效位置"),
    MATERIAL("材质", "分别调节卡片材质与整屏背景"),
    CORNERS("圆角", "通知、控制中心与音量条的圆角"),
    NOTIFICATIONS("通知动画", "按压反馈、顶部横幅与通知列表动画"),
    VOLUME("音量面板", "长按展开、音量条尺寸与弹出动画");

    val settings: List<LiteSetting> get() = LiteCatalog.entries.filter { owner(it) == this }
    val visibleSettings: List<LiteSetting> get() = settings.filter(::settingVisible)
    val categories: List<MaterialCategory> get() = MaterialCategory.entries.filter { it.feature == this }

    companion object {
        fun owner(setting: LiteSetting): Feature = MaterialCategory.ownerOrNull(setting)?.feature ?: when {
            setting.key.startsWith("notification_") -> NOTIFICATIONS
            setting.key.startsWith("volume_") -> VOLUME
            else -> error("Setting has no feature destination: ${setting.key}")
        }
    }
}

/** Deferred settings retain their catalog/export identity but have no visible editor. */
internal fun settingVisible(setting: LiteSetting): Boolean =
    setting.key !in DeferredSettings.hiddenKeys
