// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

/** Tracks successful effect writes, so skipped targets never acquire ownership. */
internal class BlurFrameGate(private val minIntervalMs: Long = 0L) {
    private var radius = Float.NaN
    private var writtenAt: Long? = null

    fun shouldApply(nextRadius: Float, nowMs: Long): Boolean = nextRadius != radius &&
        (nextRadius <= 0f || writtenAt?.let { nowMs - it >= minIntervalMs } != false)

    fun committed(nextRadius: Float, nowMs: Long) {
        radius = nextRadius
        writtenAt = nowMs
    }

    fun reset() { radius = Float.NaN; writtenAt = null }
}
