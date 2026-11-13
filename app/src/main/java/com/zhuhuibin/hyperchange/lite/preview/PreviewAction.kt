// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.preview

/** Select the effect being edited, independently of the surface used to render it. */
internal enum class PreviewAction {
    STATIC, PRESS, HEADS_UP_ENTER, HEADS_UP_EXIT, ROW_ENTER, ROW_EXIT,
    GROUP, VOLUME_WIDTH, VOLUME_POP, VOLUME_NUDGE,
}

internal fun previewActionFor(key: String): PreviewAction = when {
    key.startsWith("notification_heads_up_pop_exit") -> PreviewAction.HEADS_UP_EXIT
    key.startsWith("notification_heads_up_pop") -> PreviewAction.HEADS_UP_ENTER
    key.startsWith("notification_press_tilt") -> PreviewAction.PRESS
    key.startsWith("notification_stack_anim_remove") -> PreviewAction.ROW_EXIT
    key.startsWith("notification_stack_anim") -> PreviewAction.ROW_ENTER
    key.startsWith("notification_group_stack") -> PreviewAction.GROUP
    key.startsWith("volume_nudge") -> PreviewAction.VOLUME_NUDGE
    key.startsWith("volume_pop") -> PreviewAction.VOLUME_POP
    key == "volume_motion_enabled" || key == "volume_motion_duration" || key == "volume_thin_width" -> PreviewAction.VOLUME_WIDTH
    else -> PreviewAction.STATIC
}
