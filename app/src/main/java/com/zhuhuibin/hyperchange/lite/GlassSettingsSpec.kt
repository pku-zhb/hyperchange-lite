// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.glass.softGlassParameters

object GlassSettingsSpec {
    val entries: List<LiteSetting> = buildList {
        add(LiteSetting.toggle("glass_enabled", "卡片材质调节", "柔光玻璃", summary = "调节已开启范围的卡片玻璃；关闭后保留数值，不改变整屏背景的独立开关"))
        add(LiteSetting.toggle("glass_notifications", "普通通知材质", "柔光范围", true,
            summary = "调节通知卡片；锁屏通知需另外开启“锁屏通知效果”"))
        add(LiteSetting.toggle(SettingsMigration.MEDIA_KEY, "音乐卡片材质", "柔光范围",
            summary = "调节通知栏和锁屏的播放器背景。使用 HMC 自定义播放器背景时关闭，避免两边同时修改"))
        add(LiteSetting.toggle("glass_control_center", "控制中心", "柔光范围", true))
        add(LiteSetting.toggle("glass_volume", "音量面板", "柔光范围", true))
        add(LiteSetting.toggle("glass_shade", "下拉背景", "柔光范围", true))
        add(LiteSetting.toggle("glass_shade_enabled", "整屏背景调节", "整屏背景", summary = "独立调节已展开通知中心与控制中心的底层背景；不随卡片预设或卡片开关改变"))
        add(LiteSetting.toggle("glass_unify_notifications", "统一通知与音乐材质", "柔光范围", summary = "让实时卡片和音乐卡采用普通通知的玻璃样式。独立于材质调节总开关，仍遵守各自的作用范围；不改变悬浮通知"))
        add(LiteSetting.number("glass_refraction_blur", "玻璃模糊", "柔光玻璃", 100f, 0f, 200f, summary = "100% 保留系统值；向左更清晰，向右更模糊"))
        add(LiteSetting.number("glass_background_blur", "卡片后方模糊", "卡片取样", 100f, 0f, 200f, summary = "只调卡片背后取样；100% 保留系统值，不改变整屏下拉背景"))
        add(LiteSetting.number("glass_background_scale", "卡片取样尺度", "卡片取样", 100f, 0f, 200f, summary = "只改变卡片背后取样尺度；100% 保留系统值"))
        add(LiteSetting.number("glass_background_opacity", "卡片底色浓度", "卡片取样", 100f, 0f, 200f, summary = "只调卡片底色；100% 保留系统值，不改变整屏遮罩"))
        add(LiteSetting.number("glass_shade_blur", "整屏背景模糊", "整屏背景", 100f, 0f, 200f, summary = "只调已展开通知中心和控制中心的底层背景；100% 保留系统值，悬浮通知不生效"))
        add(LiteSetting.number("glass_shade_scale", "整屏额外缩放", "整屏背景", 100f, 0f, 200f, summary = "0% 不额外缩放；100% 保留系统值"))
        add(LiteSetting.number("glass_shade_opacity", "整屏背景色浓度", "整屏背景", 100f, 0f, 200f, summary = "只调已展开面板的整屏遮罩；100% 保留系统值，独立于卡片预设"))
        softGlassParameters.forEach { p ->
            add(LiteSetting.number("glass_${p.key}", materialTitle(p.key, p.title), "材质细调", if (p.key == "darker") -100f else 0f,
                p.adjustmentRange.first.toFloat(), p.adjustmentRange.last.toFloat(), summary = when (p.key) {
                    "darker" -> "默认 −100% 去掉系统压暗；0% 恢复原值"
                    "brightness", "background_brightness" -> "相对系统亮度配方增减，0% 保留原值；正值不一定更亮"
                    "edge_feather" -> "正值让边缘更柔和，负值更锐利；0% 保留系统值"
                    else -> "相对系统配方增减；0% 保留原值，建议先小幅调整"
                }))
        }
        add(LiteSetting.toggle("glass_assistant", "负一屏组件柔光", "负一屏与侧边栏", summary = "独立开关，共用折射和光影参数；背景模糊、采样比例与背景色保留组件原值"))
        add(LiteSetting.toggle("glass_sidebar", "侧边栏柔光", "负一屏与侧边栏", summary = "独立开关，共用折射和光影参数；背景模糊、采样比例与背景色保留组件原值"))
        add(LiteSetting.toggle("glass_sidebar_unify", "侧边栏卡片统一材质", "负一屏与侧边栏", summary = "将侧栏工具卡片和应用面板改用系统侧栏玻璃；独立于侧边栏柔光开关"))
    }

    private fun materialTitle(key: String, fallback: String) = when (key) {
        "luminance" -> "明暗混合"
        "brightness" -> "表面亮度混合"
        "alpha" -> "玻璃不透明度"
        "refraction" -> "折射强度"
        "thickness" -> "玻璃厚度"
        "background_brightness" -> "背景亮度混合"
        "burn" -> "背景压色"
        else -> fallback
    }
}
