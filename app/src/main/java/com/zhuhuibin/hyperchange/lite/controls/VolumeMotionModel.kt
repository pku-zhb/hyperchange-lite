// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.sign

/** Damped motion reconstructed from the observed HyperLight volume behavior. */
class VolumeSpring(initial: Float) {
    var value = initial; private set
    var velocity = 0f; private set
    var target = initial; private set
    private var stiffness = 320f
    private var damping = 26f
    val settled: Boolean get() = abs(value - target) < .002f && abs(velocity) < .02f

    fun snap(value: Float) { this.value = value; target = value; velocity = 0f }
    fun seedVelocity(value: Float) { velocity = value.coerceIn(-10000f, 10000f) }
    fun target(value: Float, stiffness: Float, damping: Float) {
        target = value
        this.stiffness = stiffness.coerceIn(1f, 4000f)
        this.damping = damping.coerceIn(1f, 400f)
    }

    fun advance(seconds: Float): Boolean {
        if (settled) { snap(target); return false }
        // Bound stalled frames so a paused UI cannot inject an unstable time step.
        val dt = seconds.coerceIn(0f, .064f)
        // Semi-implicit Euler requires h*h*k + 2*h*d < 4. This bound keeps
        // that expression <= 3, including legal low-damping pop settings.
        val stableStep = 1f / max(sqrt(stiffness), damping)
        val steps = ceil(dt / stableStep).toInt().coerceIn(1, 32)
        repeat(steps) {
            val step = dt / steps
            velocity += (-stiffness * (value - target) - damping * velocity) * step
            value += velocity * step
        }
        if (settled) snap(target)
        return !settled
    }
}

data class VolumePopTransform(val scale: Float, val x: Float, val y: Float)
data class VolumeDragTransform(val x: Float, val y: Float, val scaleX: Float, val scaleY: Float)

object VolumeMotionModel {
    fun popTransform(progress: Float, initialScale: Float, shift: Float, power: Float): VolumePopTransform {
        val remaining = 1f - progress
        val shaped = sign(remaining) * abs(remaining).pow(power.coerceIn(1f, 4f))
        val x = -shift * shaped
        return VolumePopTransform(1f - (1f - initialScale) * shaped, x, x * .35f)
    }

    fun dragTransform(dx: Float, dy: Float, width: Float, height: Float): VolumeDragTransform {
        if (width <= 0f || height <= 0f) return VolumeDragTransform(0f, 0f, 1f, 1f)
        fun rubber(value: Float, size: Float) = sign(value) * size * (1f - 1f / (abs(value) * .18f / size + 1f))
        val x = rubber(dx, width)
        val y = rubber(dy, height)
        val horizontal = abs(x / width) >= abs(y / height)
        val axis = if (horizontal) x / width else y / height
        val deformation = .18f * axis.coerceIn(-1f, 1f).let { it * it }
        return VolumeDragTransform(x, y, if (horizontal) 1f + deformation else 1f - deformation,
            if (horizontal) 1f - deformation else 1f + deformation)
    }

    fun widthCoefficients(durationMs: Float): Pair<Float, Float> {
        val speed = 600f / durationMs.coerceIn(120f, 2000f)
        return 420f * speed * speed to 80f * speed
    }

    fun popCoefficients(durationMs: Float, dampingRatio: Float): Pair<Float, Float> {
        val ratio = dampingRatio.coerceIn(.1f, 2f)
        val omega = 4f / (durationMs.coerceIn(60f, 2000f) * ratio / 1000f)
        return omega * omega to 2f * ratio * omega
    }

    fun nudgeCoefficients(response: Float, damping: Float) =
        response.coerceIn(.05f, 4f) * 400f to damping.coerceIn(.1f, 2f) * 40f

    fun iconOpacity(width: Float, thin: Float, normal: Float): Float {
        if (normal <= thin) return 1f
        val progress = ((width - thin) / (normal - thin)).coerceIn(0f, 1f)
        return ((progress - .8f) / .2f).coerceIn(0f, 1f)
    }
}
