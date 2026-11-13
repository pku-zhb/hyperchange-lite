// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import android.content.SharedPreferences
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.zhuhuibin.hyperchange.lite.readField
import java.lang.ref.WeakReference

/** Owns only one column's visual layers, never the native volume gesture listener. */
internal class VolumeAnimator(
    owner: Any,
    private val preferences: SharedPreferences,
    private val onPhaseChanged: () -> Unit,
) : Runnable {
    private val ownerReference = WeakReference(owner)
    private val nativeStateGetter = owner.javaClass.methods.firstOrNull { it.name == "getSs" && it.parameterCount == 0 }
    private val column = WeakReference(readField(owner, "view") as? View)
    private val parts = listOf("slider", "progressView", "progressViewBg").mapNotNull {
        (readField(owner, it) as? View)?.let(::WeakReference)
    }
    private val slider = WeakReference(readField(owner, "slider") as? View)
    private val icon = WeakReference(readField(owner, "icon") as? View)
    private val widthBaseline = VolumeWidthBaseline()
    private val normalWidths get() = widthBaseline.widths
    private var iconAlpha = icon.get()?.alpha ?: 1f
    private var baseX = column.get()?.translationX ?: 0f
    private var baseY = column.get()?.translationY ?: 0f
    private var baseScaleX = column.get()?.scaleX ?: 1f
    private var baseScaleY = column.get()?.scaleY ?: 1f
    private var baseMatrix = Matrix(column.get()?.matrix ?: Matrix())
    private var edgeGap = VolumePlacementModel.DEFAULT_EDGE_GAP
    private var screenRightPx = 0f
    private var rootWidth = 0
    private var rootHeight = 0
    private var displayId = -1
    private var placementDirty = true
    private var edgeTranslation = 0f
    private val screenMetrics = DisplayMetrics()
    private val screenOrigin = IntArray(2)
    private val trackToColumn = Matrix()
    private val placementStep = Matrix()
    private val placementCombined = Matrix()
    private val nativeToRoot = Matrix()
    private val trackBounds = RectF()
    private val session = VolumePanelSession()
    private val interaction get() = session.state
    private var pendingPop = false
    private val width = VolumeSpring(parts.firstOrNull()?.get()?.layoutParams?.width?.toFloat() ?: 0f)
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
    private var dismissalObserver: ViewTreeObserver? = null
    private val dismissalFrame = ViewTreeObserver.OnPreDrawListener {
        val view = column.get()
        if (session.dismissing && (view == null || !view.isAttachedToWindow || !view.isShown)) restore()
        true
    }
    private val touching get() = interaction.touching
    val narrow: Boolean get() = owns && interaction.visible && interaction.narrow && column.get()?.isShown == true
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        placementDirty = true
        if (interaction.visible && !owns) refresh() else if (owns) schedule()
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { if (interaction.visible) refresh() }
        override fun onViewDetachedFromWindow(v: View) { restore() }
    }

    init {
        column.get()?.addOnAttachStateChangeListener(attachListener)
        parts.forEach { it.get()?.addOnLayoutChangeListener(layoutListener) }
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
            if (!widthBaseline.acquire(parts.map { it.get()?.layoutParams?.width ?: 0 })) return false
            baseX = view.translationX; baseY = view.translationY; baseScaleX = view.scaleX; baseScaleY = view.scaleY
            baseMatrix = Matrix(view.matrix)
            placementDirty = true
            iconAlpha = icon.get()?.alpha ?: 1f
            width.snap(normalWidths[0].toFloat()); pop.snap(1f); nudge.snap(0f)
            dragX.snap(0f); dragY.snap(0f); stretchX.snap(1f); stretchY.snap(1f)
            readPlacement()
            owns = true
            if (interaction.narrow) onPhaseChanged()
        }
        return true
    }

    fun refresh() {
        if (!enabled()) { restore(); return }
        if (session.dismissing) return
        readPlacement()
        if (!interaction.visible) return
        if (!acquire()) return
        startPendingPop()
        targetWidth(if (interaction.narrow) thin() else normalWidths[0].toFloat())
    }

    fun show() {
        if (!enabled()) return
        if (session.dismissing) restore()
        val firstShow = !interaction.visible
        session.show(nativeLevel())
        if (firstShow) { pendingPop = true; onPhaseChanged() }
        // Remember show even if native attachment or measured dimensions are not
        // ready; the attach/layout observer retries without losing the session.
        refresh()
    }

    fun dismiss() {
        session.dismiss()
        if (!session.dismissing) return
        column.get()?.removeCallbacks(this)
        scheduled = false
        returnAt = 0L
        val view = column.get()
        if (view == null || !view.isAttachedToWindow || !view.isShown) { restore(); return }
        if (dismissalObserver?.isAlive == true) return
        dismissalObserver = view.viewTreeObserver.also { it.addOnPreDrawListener(dismissalFrame) }
    }

    private fun nativeLevel(): Int? {
        val instance = ownerReference.get() ?: return null
        // Current OEM VolumeColumn stores ss in a delegated property, not a field.
        val state = runCatching { nativeStateGetter?.invoke(instance) }.getOrNull() ?: readField(instance, "ss")
        return (readField(state, "level") as? Number)?.toInt()
    }

    fun sliderUpdated() {
        if (column.get()?.isShown != true) return
        val wasNarrow = interaction.narrow
        if (!session.sliderUpdate(nativeLevel())) return
        if (!wasNarrow) onPhaseChanged()
        refresh()
        nudge()
    }

    fun nativeSizeUpdated(index: Int) {
        if (!owns || session.dismissing) return
        widthBaseline.updateNative(index, parts.getOrNull(index)?.get()?.layoutParams?.width ?: 0)
        placementDirty = true
        refresh()
    }

    private fun startPendingPop() {
        if (!pendingPop) return
        pendingPop = false
        pop.snap(0f)
        val (k, d) = VolumeMotionModel.popCoefficients(number("volume_pop_duration", 1000f), number("volume_pop_damping", 1f))
        pop.target(1f, k, d)
    }

    fun touch(event: MotionEvent) {
        if (session.dismissing) return
        if (!acquire()) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                transition(VolumeInteractionEvent.TOUCH_DOWN)
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
                transition(if (releaseMomentum) VolumeInteractionEvent.TOUCH_UP else VolumeInteractionEvent.TOUCH_CANCEL)
                // Cancellation must not inject release momentum from a lost pointer.
                if (releaseMomentum) {
                    dragX.seedVelocity(velocityX * .45f); dragY.seedVelocity(velocityY * .45f)
                }
                velocityX = 0f; velocityY = 0f
                dragX.target(0f, 380f, 28f); dragY.target(0f, 380f, 28f)
                stretchX.target(1f, 380f, 28f); stretchY.target(1f, 380f, 28f)
                targetWidth(if (interaction.narrow) thin() else normalWidths[0].toFloat())
            }
        }
    }

    fun nudge() {
        // The key that opens the native panel can arrive before its show callback.
        // Its show starts wide; subsequent key animations narrow that live session.
        if (!interaction.visible || session.dismissing) return
        if (!acquire()) return
        transition(VolumeInteractionEvent.HARDWARE_KEY)
        targetWidth(if (interaction.narrow) thin() else normalWidths[0].toFloat())
        val (k, d) = VolumeMotionModel.nudgeCoefficients(number("volume_nudge_response", .8f), number("volume_nudge_damping", .9f))
        val density = column.get()?.resources?.displayMetrics?.density ?: 1f
        nudge.target(-number("volume_nudge", 6.5f) * density, k, d)
        returnAt = SystemClock.uptimeMillis() + 100L
        schedule()
    }

    private fun transition(event: VolumeInteractionEvent) {
        val previous = interaction
        session.event(event)
        if (interaction == previous) return
        onPhaseChanged()
    }

    private fun readPlacement() {
        edgeGap = VolumePlacementModel.resolveEdgeGap(
            if (preferences.contains("volume_edge_gap")) number("volume_edge_gap", 6f) else null,
            if (preferences.contains("volume_offset_x")) number("volume_offset_x", 0f) else null,
        )
        placementDirty = true
        column.get()?.let(::readScreenBounds)
    }

    @Suppress("DEPRECATION")
    private fun readScreenBounds(view: View) {
        val root = view.rootView
        val currentDisplayId = view.display?.displayId ?: -1
        if (screenRightPx > 0f && root.width == rootWidth && root.height == rootHeight && currentDisplayId == displayId) return
        rootWidth = root.width
        rootHeight = root.height
        displayId = currentDisplayId
        view.display?.getRealMetrics(screenMetrics)
        screenRightPx = (screenMetrics.widthPixels.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels).toFloat()
        placementDirty = true
    }

    /** Recalculate resting geometry only after layout; per-frame motion uses this cache. */
    private fun placementTranslation(view: View): Float {
        val track = slider.get() ?: return 0f
        if (track.width <= 0) return 0f
        readScreenBounds(view)
        if (placementDirty) {
            trackToColumn.reset()
            var child = track
            while (child !== view) {
                val parent = child.parent as? View ?: return 0f
                placementStep.set(child.matrix)
                placementStep.postTranslate((child.left - parent.scrollX).toFloat(), (child.top - parent.scrollY).toFloat())
                placementCombined.setConcat(placementStep, trackToColumn)
                trackToColumn.set(placementCombined)
                child = parent
            }
            nativeToRoot.set(baseMatrix)
            nativeToRoot.preConcat(trackToColumn)
            val root = view.rootView
            child = view
            while (child !== root) {
                val parent = child.parent as? View ?: return 0f
                nativeToRoot.postTranslate((child.left - parent.scrollX).toFloat(), (child.top - parent.scrollY).toFloat())
                child = parent
            }
            trackBounds.set(0f, 0f, track.width.toFloat(), track.height.toFloat())
            nativeToRoot.mapRect(trackBounds)
            // Resting ancestor positions preserve OEM panel show/hide transforms.
            root.getLocationOnScreen(screenOrigin)
            edgeTranslation = VolumePlacementModel.edgeTranslation(screenRightPx, trackBounds.right + screenOrigin[0], edgeGap)
            placementDirty = false
        }
        return edgeTranslation * VolumePlacementModel.narrowFraction(track.width.toFloat(), thin(), normalWidths[0].toFloat())
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
        if (session.dismissing) { scheduled = false; return }
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
        view.translationX = baseX + placementTranslation(view) + nudge.value + transform.x + dragX.value
        view.translationY = baseY + transform.y + dragY.value
        if (moving || returnAt != 0L) view.postOnAnimation(this) else scheduled = false
    }

    fun restore() {
        dismissalObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(dismissalFrame)
        dismissalObserver = null
        column.get()?.removeCallbacks(this)
        scheduled = false; returnAt = 0L
        pendingPop = false
        transition(VolumeInteractionEvent.HIDE)
        if (!owns) return
        val originalWidths = widthBaseline.release() ?: return
        parts.forEachIndexed { i, weak -> weak.get()?.let { part ->
            part.layoutParams?.let { params -> params.width = originalWidths[i]; part.layoutParams = params }
        } }
        column.get()?.let { it.translationX = baseX; it.translationY = baseY; it.scaleX = baseScaleX; it.scaleY = baseScaleY }
        icon.get()?.alpha = iconAlpha
        owns = false
    }

    fun dispose() {
        restore()
        column.get()?.removeOnAttachStateChangeListener(attachListener)
        parts.forEach { it.get()?.removeOnLayoutChangeListener(layoutListener) }
    }
}
