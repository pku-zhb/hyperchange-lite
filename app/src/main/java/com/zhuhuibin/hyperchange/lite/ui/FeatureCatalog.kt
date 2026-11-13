// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.DeferredSettings

/** Navigation owns presentation only; persisted setting names remain unchanged. */
enum class Feature(val title: String, val summary: String) {
    SCOPES("启用与范围", "分别开启卡片材质、锁屏效果与侧栏调节"),
    MATERIAL("卡片材质", "套用预设，或微调玻璃通透、模糊与底色"),
    BACKDROP("下拉背景", "单独调节下拉面板后方背景，不改卡片材质参数"),
    CORNERS("圆角", "调整通知、控制中心与音量条的圆角"),
    NOTIFICATIONS("通知动画", "调节按压反馈、顶部横幅与列表出现消失"),
    VOLUME("音量面板", "设置侧边音量条宽窄、位置与动画");

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
