// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

object ControlVolumeSettingsSpec {
    val entries = listOf(
        LiteSetting.toggle("notification_radius_enabled", "通知圆角", "通知外观", summary = "尺寸在系统重新创建通知视图后生效；不会自动重启"),
        LiteSetting.number("notification_radius", "通知圆角大小", "通知外观", 26f, 0f, 64f, .5f, "单位 dp"),
        LiteSetting.toggle("cc_radius_enabled", "控制中心圆角", "控制中心"),
        LiteSetting.number("cc_card_radius", "大磁贴", "控制中心", 40f, 0f, 100f),
        LiteSetting.number("cc_media_radius", "媒体卡片", "控制中心", 26f, 0f, 100f),
        LiteSetting.number("cc_slider_radius", "亮度与音量滑条", "控制中心", 40f, 0f, 100f),
        LiteSetting.number("cc_tile_radius", "小磁贴", "控制中心", 24f, 0f, 100f, 1f, "减小圆角可得到圆角矩形"),
        LiteSetting.number("cc_device_radius", "设备中心", "控制中心", 26f, 0f, 100f),
        LiteSetting.number("cc_secondary_radius", "二级面板", "控制中心", 26f, 0f, 100f),
        LiteSetting.toggle("volume_corner_enabled", "侧边音量条圆角", "音量条", summary = "重新打开面板后生效；部分系统需重建系统界面"),
        LiteSetting.number("volume_corner", "音量条圆角", "音量条", 40f, 0f, 100f),
        LiteSetting.toggle("volume_timer_corner_enabled", "静音与勿扰定时条圆角", "音量条", summary = "重新打开面板后生效；部分系统需重建系统界面"),
        LiteSetting.number("volume_timer_corner", "定时条圆角", "音量条", 40f, 0f, 100f),
        LiteSetting.toggle("volume_hide_ringer", "隐藏侧边静音与勿扰按钮", "音量条", summary = "展开的音量面板仍保留系统按钮"),
        LiteSetting.toggle("volume_long_press", "长按音量条展开", "音量条", summary = "拖动调整音量时不会触发"),
        LiteSetting.toggle("volume_motion_enabled", "窄音量条与弹动", "音量交互", summary = "按压展开，松开收窄；不改变系统音量手势"),
        LiteSetting.number("volume_thin_width", "收起宽度", "音量交互", 9f, 4f, 32f, .5f),
        LiteSetting.number("volume_height", "音量条高度", "音量交互", 150f, 80f, 280f, summary = "系统重新创建音量条后生效"),
        LiteSetting.number("volume_offset_x", "右边距微调", "音量交互", 0f, -50f, 50f, summary = "单位 px；负值更靠右，重新打开面板后生效"),
        LiteSetting.number("volume_offset_y", "上边距微调", "音量交互", 0f, -200f, 200f, summary = "单位 px；负值更靠上，重新打开面板后生效"),
        LiteSetting.number("volume_motion_duration", "宽度过渡时长", "音量交互", 600f, 120f, 2000f, 10f, "单位毫秒"),
        LiteSetting.number("volume_nudge", "按键轻推距离", "音量交互", 6.5f, 0f, 30f, .5f),
        LiteSetting.number("volume_nudge_response", "轻推响应", "音量交互", .8f, .05f, 4f, .05f),
        LiteSetting.number("volume_nudge_damping", "轻推阻尼", "音量交互", .9f, .1f, 2f, .05f),
        LiteSetting.number("volume_pop_scale", "弹出起始大小", "音量交互", .2f, 0f, 1f, .01f, "0.2 表示从正常大小的 20% 弹出"),
        LiteSetting.number("volume_pop_shift", "弹出位移", "音量交互", 5.5f, 0f, 30f, .5f),
        LiteSetting.number("volume_pop_duration", "弹出时长", "音量交互", 1000f, 60f, 2000f, 10f),
        LiteSetting.number("volume_pop_damping", "弹出阻尼", "音量交互", 1f, .1f, 2f, .05f),
        LiteSetting.number("volume_pop_power", "弹出位移曲线", "音量交互", 3f, 1f, 4f, .1f),
    )
}
