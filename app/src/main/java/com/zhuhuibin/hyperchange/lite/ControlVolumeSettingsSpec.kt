// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

object ControlVolumeSettingsSpec {
    val entries = listOf(
        LiteSetting.toggle("notification_radius_enabled", "自定义通知圆角", "通知外观", summary = "新生成或重新创建的通知生效；不会自动重启系统界面"),
        LiteSetting.number("notification_radius", "通知圆角半径", "通知外观", 26f, 0f, 64f, .5f),
        LiteSetting.toggle("cc_radius_enabled", "自定义控制中心圆角", "控制中心"),
        LiteSetting.number("cc_card_radius", "顶部大磁贴圆角", "控制中心", 40f, 0f, 100f),
        LiteSetting.number("cc_media_radius", "音乐卡片圆角", "控制中心", 26f, 0f, 100f),
        LiteSetting.number("cc_slider_radius", "亮度与音量滑条圆角", "控制中心", 40f, 0f, 100f),
        LiteSetting.number("cc_tile_radius", "快捷开关圆角", "控制中心", 24f, 0f, 100f, 1f, summary = "调小更方正，调大更圆润"),
        LiteSetting.number("cc_device_radius", "设备中心圆角", "控制中心", 26f, 0f, 100f),
        LiteSetting.number("cc_secondary_radius", "二级面板圆角", "控制中心", 26f, 0f, 100f),
        LiteSetting.toggle("volume_corner_enabled", "自定义音量条圆角", "音量条", summary = "重新打开面板后生效；部分系统需重新加载系统界面"),
        LiteSetting.number("volume_corner", "音量条圆角半径", "音量条", 40f, 0f, 100f),
        LiteSetting.toggle("volume_timer_corner_enabled", "自定义定时条圆角", "音量条", summary = "仅调整静音与勿扰定时条；重新打开面板后生效"),
        LiteSetting.number("volume_timer_corner", "定时条圆角半径", "音量条", 40f, 0f, 100f),
        LiteSetting.toggle("volume_hide_ringer", "始终隐藏静音与勿扰按钮", "音量条", summary = "默认仅在窄条时隐藏；开启后宽条也隐藏，完整面板仍保留"),
        LiteSetting.toggle("volume_long_press", "长按音量条展开完整面板", "音量条", summary = "上下拖动调节音量时不会误触展开"),
        LiteSetting.toggle("volume_motion_enabled", "窄音量条与位置动画", "音量交互", summary = "首次宽条，继续按键后收窄；触摸变宽，松开收窄"),
        LiteSetting.number("volume_thin_width", "收窄后宽度", "音量交互", 9f, 4f, 32f, .5f),
        LiteSetting.number("volume_height", "音量条高度", "音量交互", 150f, 80f, 280f, summary = "单位 dp；系统重新创建音量条后生效"),
        LiteSetting.number("volume_edge_gap", "距右边缘距离", "音量交互", 6f, 0f, 200f, summary = "单位 px；0 为贴边，重新打开面板后生效"),
        LiteSetting.number("volume_offset_y", "上下位置微调", "音量交互", 0f, -200f, 200f, summary = "单位 px；负数向上、正数向下，重新打开面板后生效"),
        LiteSetting.number("volume_motion_duration", "宽窄切换耗时", "音量交互", 600f, 120f, 2000f, 10f),
        LiteSetting.number("volume_nudge", "按键轻推距离", "音量交互", 6.5f, 0f, 30f, .5f, summary = "单位 dp；0 为不轻推"),
        LiteSetting.number("volume_nudge_response", "按键轻推快慢", "音量交互", .8f, .05f, 4f, .05f, summary = "越大轻推越快"),
        LiteSetting.number("volume_nudge_damping", "轻推回弹抑制", "音量交互", .9f, .1f, 2f, .05f, summary = "越小晃动越多，越大越平稳"),
        LiteSetting.number("volume_pop_scale", "弹出起始大小", "音量交互", .2f, 0f, 1f, .01f, summary = "0.2× 表示从正常大小的两成开始"),
        LiteSetting.number("volume_pop_shift", "弹出滑入距离", "音量交互", 5.5f, 0f, 30f, .5f),
        LiteSetting.number("volume_pop_duration", "弹出耗时", "音量交互", 1000f, 60f, 2000f, 10f),
        LiteSetting.number("volume_pop_damping", "弹出回弹抑制", "音量交互", 1f, .1f, 2f, .05f, summary = "越小弹跳越多；1 及以上通常无明显回弹"),
        LiteSetting.number("volume_pop_power", "弹出末段舒缓", "音量交互", 3f, 1f, 4f, .1f, summary = "调高后起步更快，接近终点时更舒缓"),
    )
}
