// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.preview

/** Local previews obey the same feature switches as their unlocked system examples. */
internal data class PreviewBehavior(
    val rowTilt: Boolean,
    val mediaTilt: Boolean,
    val headsUp: Boolean,
    val stack: Boolean,
    val group: Boolean,
    val volume: Boolean,
) {
    fun tiltFor(scene: String): Boolean = when (scene) {
        "NOTIFICATION", "HEADS_UP" -> rowTilt
        "MEDIA" -> mediaTilt
        else -> false
    }
}

internal fun previewBehavior(values: Map<String, *>): PreviewBehavior {
    fun flag(key: String, default: Boolean = false) = values[key] as? Boolean ?: default
    val tilt = flag("notification_press_tilt_enabled")
    return PreviewBehavior(
        rowTilt = tilt && flag("notification_press_tilt_row_enabled", true),
        mediaTilt = tilt && flag("notification_press_tilt_media_enabled", true),
        headsUp = flag("notification_heads_up_pop_enabled"),
        stack = flag("notification_stack_anim_enabled"),
        // Suspended together with runtime grouping, even if an older backup enabled it.
        group = false,
        volume = flag("volume_motion_enabled"),
    )
}
