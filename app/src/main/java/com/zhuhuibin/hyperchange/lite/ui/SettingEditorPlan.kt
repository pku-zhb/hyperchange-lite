// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting

/** Presentation only: mirroring a control never changes catalog ownership or saved keys. */
internal sealed interface EditorItem {
    data class Setting(val key: String) : EditorItem
    data class Note(val text: String) : EditorItem
    data class Group(
        val id: String,
        val title: String = "",
        val children: List<EditorItem>,
        val gate: List<String> = emptyList(),
        val expected: Boolean = true,
        val collapsible: Boolean = false,
    ) : EditorItem
}

internal fun editorGateOpen(group: EditorItem.Group, values: Map<String, *>): Boolean =
    group.gate.isEmpty() || group.gate.any { key ->
        (values[key] as? Boolean ?: LiteCatalog.entries.first { it.key == key }.defaultValue as Boolean) == group.expected
    }

internal fun editorItems(category: MaterialCategory?, specs: List<LiteSetting>): List<EditorItem> {
    fun rows(vararg keys: String) = keys.map(EditorItem::Setting)
    fun group(id: String, title: String = "", keys: List<String>, gate: String? = null,
              collapsible: Boolean = false, expected: Boolean = true) =
        EditorItem.Group(id, title, keys.map(EditorItem::Setting), listOfNotNull(gate), expected, collapsible)
    val keys = specs.map { it.key }
    return when (category) {
        MaterialCategory.SCOPES -> listOf(
            group("card-scopes", "卡片材质生效范围", listOf("glass_enabled", "glass_notifications", "glass_heads_up", "glass_control_center", "glass_volume", "glass_unify_notifications")),
            group("backdrop-scope", "面板展开时的全屏背景", listOf("glass_shade_enabled")),
            group("independent-scopes", "负一屏与侧边栏", listOf("glass_assistant", "glass_sidebar", "glass_sidebar_unify")),
            EditorItem.Group("shared-scopes", "与其他模块共用", rows("glass_media", "notification_lockscreen_effects", "notification_lockscreen_animations") +
                EditorItem.Note("其他模块已接管音乐卡片或锁屏通知时，请关闭对应项，避免重复修改。")),
        )
        MaterialCategory.SURFACE -> {
            val basic = listOf("glass_refraction_blur", "glass_alpha", "glass_darker", "glass_light")
            val gate = listOf("glass_enabled", "glass_assistant", "glass_sidebar")
            listOf(EditorItem.Setting("glass_enabled"),
                EditorItem.Group("surface-basic", "基础外观", basic.map(EditorItem::Setting), gate),
                EditorItem.Group("surface-advanced", "进阶：光学质感与调色", listOf(
                    group("surface-edges", "边缘与立体感", listOf("glass_refraction", "glass_thickness", "glass_edge", "glass_edge_feather", "glass_reflection_offset")),
                    group("surface-colors", "玻璃表面色彩", listOf("glass_luminance", "glass_saturation", "glass_brightness")),
                    group("background-colors", "透过玻璃的背景色", listOf("glass_background_saturation", "glass_background_brightness", "glass_burn")),
                ), gate, collapsible = true))
        }
        MaterialCategory.BACKGROUND -> rows("glass_enabled") + group("card-background", keys = keys, gate = "glass_enabled")
        MaterialCategory.SHADE -> rows("glass_shade_enabled") + group("shade-background", keys = keys.filterNot { it == "glass_shade_enabled" }, gate = "glass_shade_enabled")
        MaterialCategory.NOTIFICATION -> rows("notification_radius_enabled") + group("notification-radius", keys = listOf("notification_radius"), gate = "notification_radius_enabled")
        MaterialCategory.CONTROL -> rows("cc_radius_enabled") + group("control-radius", keys = keys.filterNot { it == "cc_radius_enabled" }, gate = "cc_radius_enabled")
        MaterialCategory.VOLUME -> rows("volume_corner_enabled") + group("volume-corners", keys = listOf("volume_corner"), gate = "volume_corner_enabled") +
            rows("volume_timer_corner_enabled") + group("volume-timer-corners", keys = listOf("volume_timer_corner"), gate = "volume_timer_corner_enabled")
        MaterialCategory.PRESETS -> emptyList()
        null -> when {
            "notification_heads_up_pop_enabled" in keys -> {
                val prefix = "notification_heads_up_pop"
                val enter = listOf("${prefix}_scale", "${prefix}_duration", "${prefix}_delay", "${prefix}_spring")
                val exit = listOf("${prefix}_exit_scale", "${prefix}_exit_duration", "${prefix}_exit_delay", "${prefix}_exit_spring")
                fun phase(id: String, title: String, phasePrefix: String, phaseKeys: List<String>) = EditorItem.Group(id, title,
                    phaseKeys.map(EditorItem::Setting) + listOf(
                        group("$id-spring", keys = listOf("${phasePrefix}_damping", "${phasePrefix}_frequency"), gate = "${phasePrefix}_spring"),
                        group("$id-curve", "进阶：自定义过渡曲线", listOf("${phasePrefix}_curve_x1", "${phasePrefix}_curve_y1", "${phasePrefix}_curve_x2", "${phasePrefix}_curve_y2"),
                            gate = "${phasePrefix}_spring", expected = false, collapsible = true),
                    ))
                rows("${prefix}_enabled") + EditorItem.Group("heads-up-details", children = listOf(
                    phase("entry", "出现动画", prefix, enter), phase("exit", "收起动画", "${prefix}_exit", exit),
                    group("heads-up-advanced", "进阶：翻转与模糊过渡", listOf("${prefix}_flip", "${prefix}_flip_delay", "${prefix}_flip_duration", "${prefix}_flip_damping",
                        "${prefix}_blur", "${prefix}_blur_delay", "${prefix}_blur_duration", "${prefix}_blur_damping"), collapsible = true),
                ), gate = listOf("${prefix}_enabled"))
            }
            "notification_press_tilt_enabled" in keys -> rows("notification_press_tilt_enabled") +
                group("press-details", keys = keys.filterNot { it == "notification_press_tilt_enabled" }, gate = "notification_press_tilt_enabled")
            "notification_stack_anim_enabled" in keys -> rows("notification_stack_anim_enabled") + EditorItem.Group("stack-details", children = listOf(
                group("stack-add", "新通知出现时", keys.filter { it.startsWith("notification_stack_anim_add_") }),
                group("stack-remove", "通知消失时", keys.filter { it.startsWith("notification_stack_anim_remove_") }),
                group("stack-spring", "列表弹性", listOf("notification_stack_anim_damping", "notification_stack_anim_frequency")),
            ), gate = listOf("notification_stack_anim_enabled"))
            "volume_motion_enabled" in keys -> rows("volume_motion_enabled") + EditorItem.Group("volume-motion-details", children = listOf(
                group("volume-size", "尺寸与贴边位置", listOf("volume_thin_width", "volume_height", "volume_edge_gap", "volume_offset_y")),
                group("volume-nudge", "宽窄切换与按键轻推", listOf("volume_motion_duration", "volume_nudge", "volume_nudge_response", "volume_nudge_damping")),
                group("volume-pop", "进阶：弹出动画", listOf("volume_pop_scale", "volume_pop_shift", "volume_pop_duration", "volume_pop_damping", "volume_pop_power"), collapsible = true),
            ), gate = listOf("volume_motion_enabled"))
            else -> keys.map(EditorItem::Setting)
        }
    }
}
