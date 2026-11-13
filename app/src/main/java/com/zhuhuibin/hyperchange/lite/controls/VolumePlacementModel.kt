// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

/** Physical-pixel placement of the visible track, independent of the OEM wrapper width. */
object VolumePlacementModel {
    const val DEFAULT_EDGE_GAP = 6f
    const val MAX_EDGE_GAP = 200f

    fun resolveEdgeGap(newGap: Float?, legacyOffset: Float? = null): Float {
        val gap = newGap ?: legacyOffset?.let { DEFAULT_EDGE_GAP + it } ?: DEFAULT_EDGE_GAP
        return if (gap.isFinite()) gap.coerceIn(0f, MAX_EDGE_GAP) else DEFAULT_EDGE_GAP
    }

    /** Return the added translation; callers retain the native translation for restoration. */
    fun edgeTranslation(screenRightPx: Float, nativeTrackRightPx: Float, edgeGapPx: Float): Float =
        if (screenRightPx.isFinite() && nativeTrackRightPx.isFinite())
            screenRightPx - resolveEdgeGap(edgeGapPx) - nativeTrackRightPx else 0f

    /** Keep the native wide layout, moving toward the edge as the track narrows. */
    fun narrowFraction(widthPx: Float, thinWidthPx: Float, normalWidthPx: Float): Float =
        if (normalWidthPx > thinWidthPx) ((normalWidthPx - widthPx) / (normalWidthPx - thinWidthPx)).coerceIn(0f, 1f) else 0f
}
