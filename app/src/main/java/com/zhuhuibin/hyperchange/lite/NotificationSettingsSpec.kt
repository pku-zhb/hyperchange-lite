// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

object NotificationSettingsSpec {
    val entries = buildList {
        fun toggle(key: String, title: String, group: String, default: Boolean = false, summary: String = "") =
            add(LiteSetting.toggle(key, title, group, default, summary))
        fun number(key: String, title: String, group: String, default: Float, min: Float, max: Float, step: Float = 1f) =
            add(LiteSetting.number(key, title, group, default, min, max, step))
        toggle(SettingsMigration.LOCKSCREEN_KEY, "锁屏通知效果", "通知作用范围",
            summary = "限制锁屏／AOD 及锁屏上拉列表的普通通知材质、按压、增删动画和分组堆叠。与 HMC 共用时关闭；音乐卡和悬浮通知按各自范围生效")
        val tilt = "通知按压"
        toggle("notification_press_tilt_enabled", "按压倾斜", tilt)
        toggle("notification_press_tilt_row_enabled", "普通通知", tilt, true)
        toggle("notification_press_tilt_media_enabled", "媒体卡片", tilt, true)
        number("notification_press_tilt_max_deg", "最大倾斜角度", tilt, 4f, 2f, 20f, .1f)
        number("notification_press_tilt_scale", "按下后的缩放", tilt, .96f, .85f, 1f, .01f)

        val heads = "悬浮通知"
        toggle("notification_heads_up_pop_enabled", "悬浮弹出动画", heads, summary = "保留原生悬浮位置，调整缩放、翻转与表面模糊。")
        number("notification_heads_up_pop_scale", "进入起始缩放", heads, .6f, .5f, 1f, .01f)
        number("notification_heads_up_pop_duration", "进入时长（毫秒）", heads, 500f, 200f, 1500f, 10f)
        number("notification_heads_up_pop_delay", "进入延迟（毫秒）", heads, 150f, 0f, 600f, 10f)
        number("notification_heads_up_pop_flip", "翻转角度", heads, 20f, -80f, 80f)
        number("notification_heads_up_pop_flip_delay", "翻转延迟（毫秒）", heads, 100f, 0f, 600f, 10f)
        number("notification_heads_up_pop_flip_duration", "翻转时长（毫秒）", heads, 500f, 200f, 1500f, 10f)
        number("notification_heads_up_pop_blur", "起始表面模糊", heads, 50f, 0f, 150f, 2f)
        number("notification_heads_up_pop_blur_delay", "模糊延迟（毫秒）", heads, 0f, 0f, 600f, 10f)
        number("notification_heads_up_pop_blur_duration", "模糊时长（毫秒）", heads, 1000f, 200f, 1500f, 10f)
        toggle("notification_heads_up_pop_spring", "进入使用弹簧曲线", heads)
        number("notification_heads_up_pop_damping", "进入阻尼", heads, .5f, .2f, 1.2f, .01f)
        number("notification_heads_up_pop_frequency", "进入频率", heads, 8f, 2f, 24f, .1f)
        number("notification_heads_up_pop_flip_damping", "翻转阻尼", heads, .85f, .2f, 1.2f, .01f)
        number("notification_heads_up_pop_blur_damping", "模糊阻尼", heads, .5f, .2f, 1.2f, .01f)
        number("notification_heads_up_pop_exit_scale", "退出目标缩放", heads, .6f, .5f, 1f, .01f)
        number("notification_heads_up_pop_exit_duration", "退出时长（毫秒）", heads, 500f, 200f, 1500f, 10f)
        number("notification_heads_up_pop_exit_delay", "退出延迟（毫秒）", heads, 150f, 0f, 600f, 10f)
        toggle("notification_heads_up_pop_exit_spring", "退出使用弹簧曲线", heads)
        number("notification_heads_up_pop_exit_damping", "退出阻尼", heads, .5f, .2f, 1.2f, .01f)
        number("notification_heads_up_pop_exit_frequency", "退出频率", heads, 8f, 2f, 24f, .1f)
        for ((prefix, label) in listOf("notification_heads_up_pop" to "进入", "notification_heads_up_pop_exit" to "退出")) {
            number("${prefix}_curve_x1", "$label 曲线 X1", heads, .22f, 0f, 1f, .01f)
            number("${prefix}_curve_y1", "$label 曲线 Y1", heads, 1f, -.5f, 2f, .01f)
            number("${prefix}_curve_x2", "$label 曲线 X2", heads, .36f, 0f, 1f, .01f)
            number("${prefix}_curve_y2", "$label 曲线 Y2", heads, 1f, -.5f, 2f, .01f)
        }

        val rows = "通知显隐"
        toggle("notification_stack_anim_enabled", "显示与消失动画", rows, summary = "不替换横滑删除和悬浮通知动画。")
        for ((phase, title, duration, blur) in listOf(listOf("add", "显示", "700", "50"), listOf("remove", "消失", "500", "20"))) {
            number("notification_stack_anim_${phase}_scale", "$title 缩放", rows, .5f, .4f, 1f, .01f)
            number("notification_stack_anim_${phase}_alpha", "$title 透明度", rows, 0f, 0f, 1f, .01f)
            number("notification_stack_anim_${phase}_blur", "$title 模糊", rows, blur.toFloat(), 0f, 100f, 2f)
            number("notification_stack_anim_${phase}_duration", "$title 时长（毫秒）", rows, duration.toFloat(), 0f, 1500f, 10f)
        }
        number("notification_stack_anim_damping", "弹簧阻尼", rows, .6f, .2f, 1.2f, .01f)
        number("notification_stack_anim_frequency", "弹簧频率", rows, 8f, 2f, 24f, .1f)

        val groups = "通知分组"
        toggle("notification_group_stack_enabled", "折叠通知组堆叠", groups, summary = "最多三张卡片；展开、拖拽和悬浮状态保留原生布局。")
        number("notification_group_stack_spacing", "卡片间距（dp）", groups, 6f, 0f, 32f)
        number("notification_group_stack_scale", "后一张卡片的缩放", groups, .95f, .5f, 1f, .01f)
        toggle("notification_disable_fold_history_enabled", "禁止折叠为历史通知", groups, summary = "启用或关闭后需重新启动相关作用域。")
    }
}
