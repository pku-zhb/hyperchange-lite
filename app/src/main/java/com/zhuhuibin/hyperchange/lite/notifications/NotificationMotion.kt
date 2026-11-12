// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Independent behavioral reconstruction; shared by system hooks and local previews. */
object NotificationMotion {
    data class TiltFrame(val rotationX: Float, val rotationY: Float, val scale: Float, val zDp: Float)
    data class GroupFrame(val offsetDp: Float, val scale: Float)

    fun spring(t: Float, damping: Float, frequency: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val d = damping.coerceIn(.2f, 1.2f)
        val w = frequency.coerceIn(2f, 24f)
        val time = t * w
        if (d >= 1f) return 1f - (1f + time) * exp(-time)
        val wd = w * sqrt(1f - d * d)
        return 1f - exp(-d * time) * (cos(wd * t) + d * w / wd * sin(wd * t))
    }

    fun bezier(t: Float, x1: Float = .22f, y1: Float = 1f, x2: Float = .36f, y2: Float = 1f): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        fun curve(v: Float, a: Float, b: Float): Float =
            3f * (1f - v) * (1f - v) * v * a + 3f * (1f - v) * v * v * b + v * v * v
        var lo = 0f
        var hi = 1f
        repeat(22) {
            val mid = (lo + hi) * .5f
            if (curve(mid, x1.coerceIn(0f, 1f), x2.coerceIn(0f, 1f)) < t) lo = mid else hi = mid
        }
        return curve((lo + hi) * .5f, y1, y2)
    }

    fun tilt(xNormalized: Float, yNormalized: Float, progress: Float, maxDegrees: Float, pressedScale: Float): TiltFrame {
        val x = xNormalized.coerceIn(0f, 1f) * 2f - 1f
        val y = yNormalized.coerceIn(0f, 1f) * 2f - 1f
        val angle = maxDegrees.coerceIn(2f, 20f) * progress
        return TiltFrame(-y * angle, x * angle, 1f - (1f - pressedScale.coerceIn(.85f, 1f)) * progress, -2f * progress)
    }

    fun group(index: Int, spacingDp: Float, scaleStep: Float): GroupFrame {
        val i = index.coerceIn(0, 2)
        return GroupFrame(i * spacingDp.coerceIn(0f, 32f), 1f - i * (1f - scaleStep.coerceIn(.5f, 1f)))
    }

    /** Horizontal dismissal retains ownership from the first meaningful motion. */
    fun releasesTilt(dx: Float, dy: Float, media: Boolean): Boolean =
        (kotlin.math.abs(dx) >= 2f || kotlin.math.abs(dy) >= 2f) &&
            (media || kotlin.math.abs(dy) <= kotlin.math.abs(dx) * .3249197f)

    fun blurRadius(value: Float): Float = if (value <= 0f) 0f else kotlin.math.round(value / 2f) * 2f
}

/** Completion callbacks remain exactly-once across animation end, cancel and detach. */
internal class Completion(private val action: () -> Unit) {
    private var completed = false
    private var deferred = 0
    private var requested = false
    fun defer(block: () -> Unit) {
        deferred++
        try { block() }
        finally { if (--deferred == 0 && requested) finish() }
    }
    fun finish() {
        if (completed) return
        if (deferred > 0) { requested = true; return }
        completed = true
        action()
    }
}
