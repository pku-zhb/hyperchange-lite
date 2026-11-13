// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import kotlin.math.min

internal data class VolumePreviewLayout(
    val scale: Float, val barLeft: Float, val barTop: Float, val barWidth: Float, val barHeight: Float,
    val buttonLeft: Float, val muteTop: Float, val dndTop: Float, val buttonWidth: Float, val buttonHeight: Float,
) {
    companion object {
        /** The two independent ringer pills remain below the bar, even in a short preview. */
        fun fit(width: Float, height: Float, density: Float, currentWidthDp: Float, heightDp: Float): VolumePreviewLayout {
            val inset = min(24f * density, width * .15f)
            val fullHeight = (heightDp + 12f + 34f + 8f + 34f) * density
            val scale = min(1f, min(height / fullHeight, (width - inset) / (52f * density))).coerceAtLeast(.001f)
            val unit = density * scale
            val barHeight = heightDp * unit
            val top = (height - fullHeight * scale) / 2f
            val buttonLeft = width - inset - 52f * unit
            return VolumePreviewLayout(scale, width - inset - currentWidthDp * unit, top, currentWidthDp * unit, barHeight,
                buttonLeft, top + barHeight + 12f * unit, top + barHeight + 54f * unit, 52f * unit, 34f * unit)
        }
    }
}
