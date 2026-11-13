// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.glass.softGlassParameters

object GlassSettingsSpec {
    val entries: List<LiteSetting> = buildList {
        add(LiteSetting.toggle("glass_enabled", "卡片材质调节", "柔光玻璃", summary = "调节已开启范围内的卡片；下拉背景有独立开关"))
        add(LiteSetting.toggle("glass_notifications", "通知栏普通通知", "柔光范围", true, summary = "含顶部横幅；锁屏需另开“锁屏通知材质与动画”"))
        add(LiteSetting.toggle("glass_heads_up", "顶部通知柔光玻璃", "柔光范围", summary = "将顶部横幅改为卡片玻璃；需开启卡片材质调节与通知栏普通通知"))
        add(LiteSetting.toggle(SettingsMigration.MEDIA_KEY, "音乐卡片材质", "柔光范围", summary = "注意兼容：使用 HMC 自定义播放器背景时关闭"))
        add(LiteSetting.toggle("glass_control_center", "控制中心卡片与磁贴", "柔光范围", true))
        add(LiteSetting.toggle("glass_volume", "侧边音量条与展开面板", "柔光范围", true))
        add(LiteSetting.toggle("glass_shade", "下拉背景", "柔光范围", true))
        add(LiteSetting.toggle("glass_shade_enabled", "调节下拉背景", "整屏背景", summary = "独立开关，仅调解锁后的下拉背景；锁屏与顶部横幅不变"))
        add(LiteSetting.toggle("glass_unify_notifications", "实时卡片与音乐沿用通知材质", "柔光范围", summary = "独立于卡片调节开关；仍遵守各自范围，不改变顶部横幅"))
        add(LiteSetting.number("glass_refraction_blur", "玻璃表层模糊", "柔光玻璃", 100f, 0f, 200f, summary = "100% 为系统原样；调低更清晰，调高更磨砂"))
        add(LiteSetting.number("glass_background_blur", "卡片背后模糊", "卡片取样", 100f, 0f, 200f, summary = "100% 为系统原样；只改卡片背后的模糊，不改整片下拉背景"))
        add(LiteSetting.number("glass_background_scale", "卡片背景缩放", "卡片取样", 100f, 0f, 200f, summary = "100% 保留系统比例；0% 关闭额外缩放"))
        add(LiteSetting.number("glass_background_opacity", "卡片底色不透明度", "卡片取样", 100f, 0f, 200f, summary = "100% 为系统原样；0% 去掉底色，调高底色更浓"))
        add(LiteSetting.number("glass_shade_blur", "背景模糊", "整屏背景", 100f, 0f, 200f, summary = "100% 为系统原样；0% 去掉面板后方模糊，顶部横幅不变"))
        add(LiteSetting.number("glass_shade_scale", "整屏额外缩放", "整屏背景", 100f, 0f, 200f, summary = "旧版备份保留项，当前不参与调节"))
        add(LiteSetting.number("glass_shade_opacity", "背景遮罩", "整屏背景", 100f, 0f, 200f, summary = "100% 为系统原样；0% 去掉面板后方遮罩，与背景模糊均为 0% 时完全透明"))
        softGlassParameters.forEach { p ->
            add(LiteSetting.number("glass_${p.key}", materialTitle(p.key, p.title), "材质细调", if (p.key == "darker") -100f else 0f,
                p.adjustmentRange.first.toFloat(), p.adjustmentRange.last.toFloat(), summary = materialSummary(p.key)))
        }
        add(LiteSetting.toggle("glass_assistant", "负一屏小组件材质", "负一屏与侧边栏", summary = "独立开关，共用玻璃外观参数；保留组件自身的背景模糊与底色"))
        add(LiteSetting.toggle("glass_sidebar", "智能侧边栏材质", "负一屏与侧边栏", summary = "独立开关，共用玻璃外观参数；保留侧栏自身的背景模糊与底色"))
        add(LiteSetting.toggle("glass_sidebar_unify", "侧栏小卡片沿用侧栏玻璃", "负一屏与侧边栏", summary = "独立开关，让工具卡与应用列表使用系统侧栏玻璃"))
    }

    private fun materialTitle(key: String, fallback: String) = when (key) {
        "luminance" -> "底图明暗变化"
        "saturation" -> "玻璃色彩浓淡"
        "brightness" -> "表面亮度调节"
        "alpha" -> "玻璃不透明度"
        "refraction" -> "边缘折射强度"
        "thickness" -> "玻璃厚度"
        "background_saturation" -> "背景色彩浓淡"
        "background_brightness" -> "背景亮度调节"
        "burn" -> "背景颜色加深"
        "edge" -> "亮边宽度"
        "edge_feather" -> "亮边柔和度"
        "reflection_offset" -> "反光位置"
        "light" -> "高光强度"
        "darker" -> "系统压暗"
        else -> fallback
    }

    private fun materialSummary(key: String) = when (key) {
        "luminance" -> "0% 保留系统值；调高让材质更多地随底图明暗变化"
        "saturation" -> "0% 保留系统值；调高更鲜艳，调低更接近灰色"
        "brightness" -> "0% 保留系统值；沿当前主题的明暗方向增减，正值不一定更亮"
        "alpha" -> "0% 保留系统值；负值更通透，正值更厚实"
        "refraction" -> "0% 保留系统值；调高让边缘透出的背景弯曲更明显"
        "thickness" -> "0% 保留系统值；调高更厚重，可配合边缘折射调整"
        "background_saturation" -> "0% 保留系统值；只改玻璃透出的背景颜色"
        "background_brightness" -> "0% 保留系统值；沿当前主题的明暗方向增减，正值不一定更亮"
        "burn" -> "0% 保留系统值；调高让透出的背景颜色更深"
        "edge" -> "0% 保留系统值；调高亮边更宽，调低更细"
        "edge_feather" -> "0% 保留系统值；正值更柔和，负值更锐利"
        "reflection_offset" -> "0% 保留系统位置；微调边缘反光的位置"
        "light" -> "0% 保留系统值；调高反光更强，调低更柔和"
        "darker" -> "默认 −100% 去掉系统压暗；0% 恢复系统原样"
        else -> ""
    }
}
