// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.LiteSetting

/** Presentation dependencies only; the runtime policies remain the authority. */
internal fun settingDisabledReason(spec: LiteSetting, values: Map<String, *>, translate: (String) -> String = { it }): String? {
    fun enabled(key: String): Boolean = values[key] as? Boolean
        ?: LiteCatalog.entries.first { it.key == key }.defaultValue as Boolean
    fun require(key: String, expected: Boolean = true): String? = if (enabled(key) == expected) null else {
        val title = LiteCatalog.entries.first { it.key == key }.title
        translate(if (expected) "需先开启“%s”（原数值已保留）" else "需先关闭“%s”（原数值已保留）").replace("%s", translate(title))
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
        key.startsWith("glass_shade_") && key != "glass_shade_enabled" -> require("glass_shade_enabled")
        key in cardBackgroundKeys -> require("glass_enabled")
        key.startsWith("glass_") && !spec.isToggle -> if (
            enabled("glass_enabled") || enabled("glass_assistant") || enabled("glass_sidebar")
        ) null else translate("请先开启卡片、负一屏或侧边栏材质调节，原数值已保留。")
        else -> null
    }
}
