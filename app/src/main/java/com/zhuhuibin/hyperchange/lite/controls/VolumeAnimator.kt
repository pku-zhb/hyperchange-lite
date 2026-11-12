// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import android.content.SharedPreferences
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.zhuhuibin.hyperchange.lite.readField
import java.lang.ref.WeakReference

/** Owns only one column's visual layers, never the native volume gesture listener. */
internal class VolumeAnimator(owner: Any, private val preferences: SharedPreferences) : Runnable {
    private val column = WeakReference(readField(owner, "view") as? View)
    private val parts = listOf("slider", "progressView", "progressViewBg").mapNotNull {
        (readField(owner, it) as? View)?.let(::WeakReference)
    }
    private val slider = WeakReference(readField(owner, "slider") as? View)
    private val icon = WeakReference(readField(owner, "icon") as? View)
    private var normalWidths = parts.map { it.get()?.layoutParams?.width ?: 0 }
    private var iconAlpha = icon.get()?.alpha ?: 1f
    private var baseX = column.get()?.translationX ?: 0f
    private var baseY = column.get()?.translationY ?: 0f
    private var baseScaleX = column.get()?.scaleX ?: 1f
    private var baseScaleY = column.get()?.scaleY ?: 1f
    private val width = VolumeSpring(normalWidths.firstOrNull()?.toFloat() ?: 0f)
    private val pop = VolumeSpring(1f)
    private val nudge = VolumeSpring(0f)
    private val dragX = VolumeSpring(0f)
    private val dragY = VolumeSpring(0f)
    private val stretchX = VolumeSpring(1f)
    private val stretchY = VolumeSpring(1f)
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastMotionTime = 0L
    private var previousNanos = 0L
    private var returnAt = 0L
    private var scheduled = false
    private var owns = false
    private var touching = false
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { restore() }
    }

    init {
        column.get()?.addOnAttachStateChangeListener(attachListener)
    }

    fun matches(view: View) = slider.get() === view
    fun matchesSlideAnimation(instance: Any?) = instance != null && readField(slider.get(), "mSlideAnim") === instance

    private fun panel(): View? {
        var parent = column.get()?.parent
        while (parent is View) {
            if (parent.javaClass.name.endsWith("MiuiVolumeDialogView")) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    private fun enabled(): Boolean {
        if (!preferences.getBoolean("volume_motion_enabled", false)) return false
        val owner = panel() ?: return false
        if (readField(owner, "isControlCenterPanel") != false) return false
        return runCatching { owner.javaClass.getMethod("isExpanded").invoke(owner) == false }.getOrDefault(false)
    }

    fun belongsToPanel(view: View): Boolean = panel() === view

    /** Motion controllers may coexist; never broadcast one panel's animation to another. */
    fun belongsToMotion(motion: Any?): Boolean {
        val owner = panel() ?: return false
        motion ?: return false
        var type: Class<*>? = motion.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (!View::class.java.isAssignableFrom(field.type)) continue
                val view = runCatching { field.isAccessible = true; field.get(motion) as? View }.getOrNull() ?: continue
                if (view === owner || view.isAttachedToWindow && owner.isAttachedToWindow && view.rootView === owner.rootView) return true
            }
            type = type.superclass
        }
        return false
    }

    private fun number(key: String, fallback: Float) = preferences.getFloat(key, fallback)
    private fun thin(): Float = number("volume_thin_width", 9f) * (column.get()?.resources?.displayMetrics?.density ?: 1f)

    private fun acquire(): Boolean {
        val view = column.get() ?: return false
        if (!enabled() || !view.isAttachedToWindow || parts.size != 3) return false
        if (!owns) {
            normalWidths = parts.map { it.get()?.layoutParams?.width ?: 0 }
            if (normalWidths.any { it <= 0 }) return false
            baseX = view.translationX; baseY = view.translationY; baseScaleX = view.scaleX; baseScaleY = view.scaleY
            iconAlpha = icon.get()?.alpha ?: 1f
            width.snap(normalWidths[0].toFloat()); pop.snap(1f); nudge.snap(0f)
            dragX.snap(0f); dragY.snap(0f); stretchX.snap(1f); stretchY.snap(1f)
            owns = true
        }
        return true
    }

    fun refresh() {
        if (!enabled()) { restore(); return }
        if (!acquire()) return
        targetWidth(if (touching) normalWidths[0].toFloat() else thin())
    }

    fun show() {
        if (!acquire()) return
        touching = false
        pop.snap(0f)
        val (k, d) = VolumeMotionModel.popCoefficients(number("volume_pop_duration", 1000f), number("volume_pop_damping", 1f))
        pop.target(1f, k, d)
        targetWidth(thin())
    }

    fun touch(event: MotionEvent) {
        if (!acquire()) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                downX = event.rawX; downY = event.rawY; lastX = downX; lastY = downY
                velocityX = 0f; velocityY = 0f; lastMotionTime = event.eventTime
                targetWidth(normalWidths[0].toFloat())
            }
            MotionEvent.ACTION_MOVE -> if (touching && event.pointerCount == 1) {
                val elapsed = event.eventTime - lastMotionTime
                if (elapsed > 0) {
                    velocityX += (((event.rawX - lastX) * 1000f / elapsed) - velocityX) * .4f
                    velocityY += (((event.rawY - lastY) * 1000f / elapsed) - velocityY) * .4f
                }
                lastX = event.rawX; lastY = event.rawY; lastMotionTime = event.eventTime
                val view = column.get() ?: return
                val shape = VolumeMotionModel.dragTransform(event.rawX - downX, event.rawY - downY, view.width.toFloat(), view.height.toFloat())
                dragX.snap(shape.x); dragY.snap(shape.y); stretchX.snap(shape.scaleX); stretchY.snap(shape.scaleY)
                schedule()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                val releaseMomentum = event.actionMasked == MotionEvent.ACTION_UP && touching
                touching = false
                // Cancellation must not inject release momentum from a lost pointer.
                if (releaseMomentum) {
                    dragX.seedVelocity(velocityX * .45f); dragY.seedVelocity(velocityY * .45f)
                }
                velocityX = 0f; velocityY = 0f
                dragX.target(0f, 380f, 28f); dragY.target(0f, 380f, 28f)
                stretchX.target(1f, 380f, 28f); stretchY.target(1f, 380f, 28f)
                targetWidth(thin())
            }
        }
    }

    fun nudge() {
        if (!acquire()) return
        val (k, d) = VolumeMotionModel.nudgeCoefficients(number("volume_nudge_response", .8f), number("volume_nudge_damping", .9f))
        val density = column.get()?.resources?.displayMetrics?.density ?: 1f
        nudge.target(-number("volume_nudge", 6.5f) * density, k, d)
        returnAt = SystemClock.uptimeMillis() + 100L
        schedule()
    }

    private fun targetWidth(value: Float) {
        val (k, d) = VolumeMotionModel.widthCoefficients(number("volume_motion_duration", 600f))
        width.target(value, k, d)
        schedule()
    }

    private fun schedule() {
        if (scheduled) return
        val view = column.get() ?: return
        previousNanos = System.nanoTime()
        scheduled = true
        view.postOnAnimation(this)
    }

    override fun run() {
        val view = column.get()
        if (view == null || !view.isAttachedToWindow || !enabled()) { restore(); return }
        val now = System.nanoTime()
        val dt = ((now - previousNanos) / 1_000_000_000f).coerceIn(0f, .064f)
        previousNanos = now
        if (returnAt != 0L && SystemClock.uptimeMillis() >= returnAt) {
            returnAt = 0L; nudge.target(0f, 400f, 40f)
        }
        val moving = width.advance(dt) or pop.advance(dt) or nudge.advance(dt) or
            dragX.advance(dt) or dragY.advance(dt) or stretchX.advance(dt) or stretchY.advance(dt)
        parts.forEach { weak -> weak.get()?.let { part ->
            val size = width.value.toInt().coerceAtLeast(1)
            part.layoutParams?.let { params -> if (params.width != size) { params.width = size; part.layoutParams = params } }
        } }
        icon.get()?.alpha = iconAlpha * VolumeMotionModel.iconOpacity(width.value, thin(), normalWidths[0].toFloat())
        val density = view.resources.displayMetrics.density
        val transform = VolumeMotionModel.popTransform(pop.value, number("volume_pop_scale", .2f), number("volume_pop_shift", 5.5f) * density, number("volume_pop_power", 3f))
        view.scaleX = baseScaleX * transform.scale * stretchX.value
        view.scaleY = baseScaleY * transform.scale * stretchY.value
        view.translationX = baseX + nudge.value + transform.x + dragX.value
        view.translationY = baseY + transform.y + dragY.value
        if (moving || returnAt != 0L) view.postOnAnimation(this) else scheduled = false
    }

    fun restore() {
        column.get()?.removeCallbacks(this)
        scheduled = false; returnAt = 0L; touching = false
        if (!owns) return
        parts.forEachIndexed { i, weak -> weak.get()?.let { part ->
            part.layoutParams?.let { params -> params.width = normalWidths[i]; part.layoutParams = params }
        } }
        column.get()?.let { it.translationX = baseX; it.translationY = baseY; it.scaleX = baseScaleX; it.scaleY = baseScaleY }
        icon.get()?.alpha = iconAlpha
        owns = false
    }

    fun dispose() { restore(); column.get()?.removeOnAttachStateChangeListener(attachListener) }
}
