// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting

/** Presentation dependencies only; the runtime policies remain the authority. */
internal fun settingDisabledReason(spec: LiteSetting, values: Map<String, *>): String? {
    fun enabled(key: String): Boolean = values[key] as? Boolean
        ?: LiteCatalog.entries.first { it.key == key }.defaultValue as Boolean
    fun require(key: String, expected: Boolean = true): String? = if (enabled(key) == expected) null else {
        val title = LiteCatalog.entries.first { it.key == key }.title
        "先${if (expected) "开启" else "关闭"}“$title”后调节；原数值会保留。"
    }
    val key = spec.key
    return when {
        key.startsWith("notification_heads_up_pop_") && key != "notification_heads_up_pop_enabled" -> {
            require("notification_heads_up_pop_enabled") ?: when {
                key.contains("_exit_curve_") -> require("notification_heads_up_pop_exit_spring", false)
                key.contains("_curve_") -> require("notification_heads_up_pop_spring", false)
                key.endsWith("_exit_damping") || key.endsWith("_exit_frequency") -> require("notification_heads_up_pop_exit_spring")
                key.endsWith("_damping") || key.endsWith("_frequency") -> require("notification_heads_up_pop_spring")
                else -> null
            }
        }
        key.startsWith("notification_press_tilt_") && key != "notification_press_tilt_enabled" -> require("notification_press_tilt_enabled")
        key.startsWith("notification_stack_anim_") && key != "notification_stack_anim_enabled" -> require("notification_stack_anim_enabled")
        key.startsWith("notification_group_stack_") && key != "notification_group_stack_enabled" -> require("notification_group_stack_enabled")
        key == "notification_radius" -> require("notification_radius_enabled")
        key.startsWith("cc_") && key != "cc_radius_enabled" -> require("cc_radius_enabled")
        key == "volume_corner" -> require("volume_corner_enabled")
        key == "volume_timer_corner" -> require("volume_timer_corner_enabled")
        key.startsWith("volume_") && !spec.isToggle -> require("volume_motion_enabled")
        key.startsWith("glass_background_") -> require("glass_enabled")
        key.startsWith("glass_") && !spec.isToggle -> if (
            enabled("glass_enabled") || enabled("glass_assistant") || enabled("glass_sidebar")
        ) null else "先开启系统材质调节、负一屏或侧边栏调节；原数值会保留。"
        else -> null
    }
}

/** Keep long animation families understandable without hiding an arbitrary first six rows. */
internal fun settingSectionTitle(spec: LiteSetting): String {
    val key = spec.key
    if (!key.startsWith("notification_heads_up_pop")) return spec.group
    return when {
        key.contains("_exit_curve_") || key in setOf("notification_heads_up_pop_exit_spring", "notification_heads_up_pop_exit_damping", "notification_heads_up_pop_exit_frequency") -> "悬浮通知：收起曲线"
        key.contains("_curve_") || key.endsWith("_damping") || key.endsWith("_frequency") || key == "notification_heads_up_pop_spring" -> "悬浮通知：弹出曲线"
        key.contains("_flip") || key.contains("_blur") -> "悬浮通知：翻转与模糊"
        else -> "悬浮通知：弹出与收起"
    }
}
