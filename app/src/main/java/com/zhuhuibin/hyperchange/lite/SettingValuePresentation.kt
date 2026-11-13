// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

/** A small editing window, independent of the full persisted/imported value range. */
internal val LiteSetting.sliderRange: ClosedFloatingPointRange<Float>
    get() {
        val preferred = when {
            key == "glass_darker" -> -100f..0f
            key == "glass_refraction_blur" -> 0f..120f
            key == "glass_shade_blur" || key == "glass_shade_opacity" -> 0f..100f
            key == "glass_background_blur" -> 40f..140f
            key == "glass_background_scale" || key == "glass_shade_scale" -> 0f..120f
            key == "glass_background_opacity" -> 40f..140f
            key.startsWith("glass_") -> -30f..30f
            key == "notification_press_tilt_max_deg" -> 2f..8f
            key == "notification_press_tilt_scale" -> .92f..1f
            key.endsWith("_curve_x1") || key.endsWith("_curve_x2") -> 0f..1f
            key.endsWith("_curve_y1") || key.endsWith("_curve_y2") -> 0f..1.2f
            key.endsWith("_damping") -> .45f..1.1f
            key.endsWith("_frequency") -> 4f..12f
            key == "notification_group_stack_spacing" -> 0f..12f
            key == "notification_group_stack_scale" -> .9f..1f
            key == "notification_heads_up_pop_flip" -> -20f..20f
            key.startsWith("notification_") && key.endsWith("_delay") -> 0f..200f
            key.startsWith("notification_") && key.endsWith("_duration") -> 200f..1000f
            key.startsWith("notification_") && key.endsWith("_blur") -> 0f..60f
            key.startsWith("notification_") && key.endsWith("_scale") -> .5f..1f
            key == "notification_radius" -> 12f..40f
            key.startsWith("cc_") && key.endsWith("_radius") -> 12f..56f
            key == "volume_corner" || key == "volume_timer_corner" -> 16f..56f
            key == "volume_thin_width" -> 6f..18f
            key == "volume_height" -> 120f..200f
            key == "volume_edge_gap" -> 0f..32f
            key == "volume_offset_y" -> -40f..40f
            key == "volume_motion_duration" -> 180f..900f
            key == "volume_nudge" -> 0f..12f
            key == "volume_nudge_response" -> .3f..1.5f
            key == "volume_pop_scale" -> .2f..1f
            key == "volume_pop_shift" -> 0f..12f
            key == "volume_pop_duration" -> 200f..1200f
            key == "volume_pop_power" -> 1f..4f
            else -> min..max
        }
        return preferred.start.coerceIn(min, max)..preferred.endInclusive.coerceIn(min, max)
    }

internal val LiteSetting.valueUnit: String
    get() = when {
        isToggle -> ""
        key.startsWith("glass_") -> "%"
        key.endsWith("_duration") || key.endsWith("_delay") -> "ms"
        key == "volume_edge_gap" || key == "volume_offset_y" -> "px"
        key.endsWith("_radius") || key == "volume_corner" || key == "volume_timer_corner" ||
            key == "volume_thin_width" || key == "volume_height" || key == "volume_nudge" ||
            key == "volume_pop_shift" || key == "notification_group_stack_spacing" -> "dp"
        key == "notification_press_tilt_max_deg" || key == "notification_heads_up_pop_flip" -> "°"
        key.endsWith("_scale") -> "×"
        else -> ""
    }
