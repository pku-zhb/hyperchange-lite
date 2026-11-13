// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

object NotificationSettingsSpec {
    val entries = buildList {
        fun toggle(key: String, title: String, group: String, default: Boolean = false, summary: String = "") =
            add(LiteSetting.toggle(key, title, group, default, summary))
        fun number(key: String, title: String, group: String, default: Float, min: Float, max: Float, step: Float = 1f, summary: String = "") =
            add(LiteSetting.number(key, title, group, default, min, max, step, summary))
        toggle(SettingsMigration.LOCKSCREEN_KEY, "锁屏通知材质与动画", "通知作用范围", summary = "注意兼容：与 HMC 共用锁屏美化时关闭；不影响已解锁通知栏")
        val tilt = "通知按压"
        toggle("notification_press_tilt_enabled", "按压倾斜反馈", tilt, summary = "按住卡片时随落点倾斜并轻微缩小；开启后可调下方参数")
        toggle("notification_press_tilt_row_enabled", "对普通通知生效", tilt, true)
        toggle("notification_press_tilt_media_enabled", "对音乐播放器生效", tilt, true)
        number("notification_press_tilt_max_deg", "最大倾斜角度", tilt, 4f, 2f, 20f, .1f, summary = "数值越大，按在卡片边角时倾斜越明显")
        number("notification_press_tilt_scale", "按下时大小", tilt, .96f, .85f, 1f, .01f, summary = "0.96× 表示缩小 4%；1× 保持原大小")

        val heads = "顶部横幅动画"
        toggle("notification_heads_up_pop_enabled", "自定义顶部横幅动画", heads, summary = "保留原生位置；调整弹出、收起、翻转和表面模糊")
        number("notification_heads_up_pop_scale", "弹出起始大小", heads, .6f, .5f, 1f, .01f, summary = "相对正常大小的倍数；0.6× 表示从六成大小开始")
        number("notification_heads_up_pop_duration", "弹出耗时", heads, 500f, 200f, 1500f, 10f)
        number("notification_heads_up_pop_delay", "弹出延迟", heads, 150f, 0f, 600f, 10f, summary = "开始放大前的等待时间")
        number("notification_heads_up_pop_flip", "弹出翻转角度", heads, 20f, -80f, 80f, summary = "正负改变翻转方向；0° 不翻转")
        number("notification_heads_up_pop_flip_delay", "翻转延迟", heads, 100f, 0f, 600f, 10f)
        number("notification_heads_up_pop_flip_duration", "翻转耗时", heads, 500f, 200f, 1500f, 10f)
        number("notification_heads_up_pop_blur", "弹出初始模糊", heads, 50f, 0f, 150f, 2f, summary = "0 为不模糊；动画结束时恢复清晰")
        number("notification_heads_up_pop_blur_delay", "模糊消散延迟", heads, 0f, 0f, 600f, 10f)
        number("notification_heads_up_pop_blur_duration", "模糊消散耗时", heads, 1000f, 200f, 1500f, 10f)
        toggle("notification_heads_up_pop_spring", "弹出回弹", heads, summary = "开启使用回弹；关闭后可调平滑曲线")
        number("notification_heads_up_pop_damping", "弹出回弹抑制", heads, .5f, .2f, 1.2f, .01f, summary = "越小弹跳越多；1 及以上通常无明显回弹")
        number("notification_heads_up_pop_frequency", "弹出回弹快慢", heads, 8f, 2f, 24f, .1f, summary = "越大回弹越快")
        number("notification_heads_up_pop_flip_damping", "翻转回弹抑制", heads, .85f, .2f, 1.2f, .01f, summary = "越小晃动越多，越大越平稳")
        number("notification_heads_up_pop_blur_damping", "模糊过渡抑制", heads, .5f, .2f, 1.2f, .01f, summary = "越小波动越多，越大越平稳")
        number("notification_heads_up_pop_exit_scale", "收起目标大小", heads, .6f, .5f, 1f, .01f, summary = "相对正常大小的倍数；数值越小收得越小")
        number("notification_heads_up_pop_exit_duration", "收起耗时", heads, 500f, 200f, 1500f, 10f)
        number("notification_heads_up_pop_exit_delay", "收起延迟", heads, 150f, 0f, 600f, 10f)
        toggle("notification_heads_up_pop_exit_spring", "收起回弹", heads, summary = "开启使用回弹；关闭后可调平滑曲线")
        number("notification_heads_up_pop_exit_damping", "收起回弹抑制", heads, .5f, .2f, 1.2f, .01f, summary = "越小弹跳越多；1 及以上通常无明显回弹")
        number("notification_heads_up_pop_exit_frequency", "收起回弹快慢", heads, 8f, 2f, 24f, .1f, summary = "越大回弹越快")
        number("notification_heads_up_pop_curve_x1", "弹出控制点1横坐标", heads, .22f, 0f, 1f, .01f, summary = "横坐标控制时间，纵坐标控制进度；仅关闭对应回弹时生效")
        number("notification_heads_up_pop_curve_y1", "弹出控制点1纵坐标", heads, 1f, -.5f, 2f, .01f, summary = "横坐标控制时间，纵坐标控制进度；仅关闭对应回弹时生效")
        number("notification_heads_up_pop_curve_x2", "弹出控制点2横坐标", heads, .36f, 0f, 1f, .01f, summary = "横坐标控制时间，纵坐标控制进度；仅关闭对应回弹时生效")
        number("notification_heads_up_pop_curve_y2", "弹出控制点2纵坐标", heads, 1f, -.5f, 2f, .01f, summary = "横坐标控制时间，纵坐标控制进度；仅关闭对应回弹时生效")
        number("notification_heads_up_pop_exit_curve_x1", "收起控制点1横坐标", heads, .22f, 0f, 1f, .01f, summary = "横坐标控制时间，纵坐标控制进度；仅关闭对应回弹时生效")
        number("notification_heads_up_pop_exit_curve_y1", "收起控制点1纵坐标", heads, 1f, -.5f, 2f, .01f, summary = "横坐标控制时间，纵坐标控制进度；仅关闭对应回弹时生效")
        number("notification_heads_up_pop_exit_curve_x2", "收起控制点2横坐标", heads, .36f, 0f, 1f, .01f, summary = "横坐标控制时间，纵坐标控制进度；仅关闭对应回弹时生效")
        number("notification_heads_up_pop_exit_curve_y2", "收起控制点2纵坐标", heads, 1f, -.5f, 2f, .01f, summary = "横坐标控制时间，纵坐标控制进度；仅关闭对应回弹时生效")

        val rows = "通知列表动画"
        toggle("notification_stack_anim_enabled", "通知出现与消失动画", rows, summary = "仅改变列表条目的出现与消失；不改左右滑动和顶部横幅")
        number("notification_stack_anim_add_scale", "出现起始大小", rows, .5f, .4f, 1f, .01f, summary = "相对正常大小的倍数；1× 保持原大小")
        number("notification_stack_anim_add_alpha", "出现起始不透明度", rows, 0f, 0f, 1f, .01f, summary = "0 为完全透明，1 为完全不透明")
        number("notification_stack_anim_add_blur", "出现起始模糊", rows, 50f, 0f, 100f, 2f, summary = "0 为不模糊")
        number("notification_stack_anim_add_duration", "出现耗时", rows, 700f, 0f, 1500f, 10f)
        number("notification_stack_anim_remove_scale", "消失目标大小", rows, .5f, .4f, 1f, .01f, summary = "相对正常大小的倍数；1× 保持原大小")
        number("notification_stack_anim_remove_alpha", "消失目标不透明度", rows, 0f, 0f, 1f, .01f, summary = "0 为完全透明，1 为完全不透明")
        number("notification_stack_anim_remove_blur", "消失目标模糊", rows, 20f, 0f, 100f, 2f, summary = "0 为不模糊")
        number("notification_stack_anim_remove_duration", "消失耗时", rows, 500f, 0f, 1500f, 10f)
        number("notification_stack_anim_damping", "列表回弹抑制", rows, .6f, .2f, 1.2f, .01f, summary = "越小弹跳越多；1 及以上通常无明显回弹")
        number("notification_stack_anim_frequency", "列表回弹快慢", rows, 8f, 2f, 24f, .1f, summary = "越大动画越快")

        val groups = "通知分组"
        toggle("notification_group_stack_enabled", "折叠通知组堆叠", groups, summary = "最多三张卡片；展开、拖拽和悬浮状态保留原生布局。")
        number("notification_group_stack_spacing", "卡片间距（dp）", groups, 6f, 0f, 32f)
        number("notification_group_stack_scale", "后一张卡片的缩放", groups, .95f, .5f, 1f, .01f)
        toggle("notification_disable_fold_history_enabled", "保留通知在当前列表", groups, summary = "启用或关闭后需重新启动相关作用域。")
    }
}
