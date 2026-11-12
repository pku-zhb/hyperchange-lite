// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.glass.softGlassParameters

object GlassSettingsSpec {
    val entries: List<LiteSetting> = buildList {
        add(LiteSetting.toggle("glass_enabled", "启用柔光调节", "柔光玻璃", summary = "使用系统原生材质与 HyperChanger 的相对调节方式"))
        add(LiteSetting.toggle("glass_notifications", "通知与媒体", "柔光范围", true))
        add(LiteSetting.toggle("glass_control_center", "控制中心", "柔光范围", true))
        add(LiteSetting.toggle("glass_volume", "音量面板", "柔光范围", true))
        add(LiteSetting.toggle("glass_shade", "下拉背景", "柔光范围", true))
        add(LiteSetting.toggle("glass_unify_notifications", "统一通知与媒体材质", "柔光范围", summary = "使用普通通知配方；保留悬浮通知的日夜材质"))
        add(LiteSetting.number("glass_refraction_blur", "玻璃采样模糊 %", "柔光玻璃", 100f, 0f, 200f))
        add(LiteSetting.number("glass_background_blur", "背景模糊 %", "下拉背景", 100f, 0f, 200f))
        add(LiteSetting.number("glass_background_scale", "背景采样缩放 %", "下拉背景", 100f, 0f, 200f))
        add(LiteSetting.number("glass_background_opacity", "背景混色不透明度 %", "下拉背景", 100f, 0f, 200f))
        softGlassParameters.forEach { p ->
            add(LiteSetting.number("glass_${p.key}", p.title, "材质细调", if (p.key == "darker") -100f else 0f,
                p.adjustmentRange.first.toFloat(), p.adjustmentRange.last.toFloat(), summary = "相对系统原始材质调节，0 保留原值"))
        }
        add(LiteSetting.toggle("glass_assistant", "负一屏组件柔光", "负一屏与侧边栏", summary = "使用上面的材质参数，独立于系统界面总开关"))
        add(LiteSetting.toggle("glass_sidebar", "侧边栏柔光", "负一屏与侧边栏", summary = "使用上面的材质参数，独立于系统界面总开关"))
        add(LiteSetting.toggle("glass_sidebar_unify", "侧边栏卡片统一材质", "负一屏与侧边栏", summary = "将工具卡片和全部应用面板接入系统侧栏玻璃"))
    }
}
