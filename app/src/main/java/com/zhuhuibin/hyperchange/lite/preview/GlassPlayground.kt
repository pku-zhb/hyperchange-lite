// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.preview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.zhuhuibin.hyperchange.lite.notifications.NotificationMotion
import com.zhuhuibin.hyperchange.lite.controls.VolumeMotionModel
import com.zhuhuibin.hyperchange.lite.controls.VolumeSpring
import com.zhuhuibin.hyperchange.lite.controls.VolumePanelAppearance
import com.zhuhuibin.hyperchange.lite.controls.VolumeInteractionEvent
import com.zhuhuibin.hyperchange.lite.controls.VolumeInteractionModel
import com.zhuhuibin.hyperchange.lite.controls.VolumeInteractionState
import com.zhuhuibin.hyperchange.lite.controls.VolumePlacementModel
import com.zhuhuibin.hyperchange.lite.controls.VolumePreviewLayout
import com.zhuhuibin.hyperchange.lite.glass.readSoftGlassSettings
import com.zhuhuibin.hyperchange.lite.ui.localizedText
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** All preview gestures and animations remain within this Activity. */
class GlassPlayground(context: Context, private val preferences: SharedPreferences) : FrameLayout(context) {
    enum class Scene(val title: String) {
        NOTIFICATION("通知"), HEADS_UP("顶部横幅"), MEDIA("媒体"), CONTROL("控制中心"), VOLUME("音量"), WIDGET("负一屏"), SIDEBAR("侧栏"), SHADE("整体背景")
    }
    var scene: Scene = Scene.NOTIFICATION
        private set
    /** API configuration status only; this does not verify the SystemUI hooks. */
    var nativeMaterialActive: Boolean? = null
        private set
    var onMaterialStatusChanged: ((Boolean) -> Unit)? = null
    private val wallpaper = WallpaperView(context)
    private val content = FrameLayout(context)
    private val caption = TextView(context)
    private val material = NativePreviewMaterial(context)
    private val surfaces = mutableListOf<SampleSurface>()
    private var shadePreview: ShadeBackdropPreview? = null
    private val animations = mutableListOf<ValueAnimator>()
    private val handler = Handler(Looper.getMainLooper())
    private var mainCard: SampleSurface? = null
    private var night = false
    private var materialComparison = false
    private var renderOnly = false
    private var focusedSetting = ""
    private var ccSampleKey = ""
    private var volumePreviewScale = 1f
    private var volumePreviewInset = 0f
    private var volumeArtwork: VolumeArtwork? = null
    private val focusedPreview = Runnable {
        if (isAttachedToWindow && !released) replayFocused()
    }
    private var captionConfigured = false
    private var behavior = previewBehavior(preferences.all)
    private var expanded = false
    private var removed = false
    private var focusGroup = ""
    private var imageGeneration = 0
    private var released = false
    private var pointerX = .5f
    private var pointerY = .5f
    private var pressProgress = 0f
    private var pressAnimator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val volumeWidth = VolumeSpring(52f)
    private var volumeState = VolumeInteractionState(visible = true)
    private var volumeEdgeGapPx = VolumePlacementModel.DEFAULT_EDGE_GAP
    private val volumePop = VolumeSpring(1f)
    private val volumeNudge = VolumeSpring(0f)
    private val volumeDragX = VolumeSpring(0f)
    private val volumeDragY = VolumeSpring(0f)
    private val volumeDragScaleX = VolumeSpring(1f)
    private val volumeDragScaleY = VolumeSpring(1f)
    private var volumeDownX = 0f
    private var volumeDownY = 0f
    private var volumeLastX = 0f
    private var volumeLastY = 0f
    private var volumeVelocityX = 0f
    private var volumeVelocityY = 0f
    private var volumeLastMotionTime = 0L
    private var volumeTouching = false
    private var volumeScheduled = false
    private var volumePreviousNanos = 0L
    private var volumeReturnAt = 0L
    private val volumeFrame = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || released || scene != Scene.VOLUME) { volumeScheduled = false; return }
            val now = System.nanoTime()
            val dt = ((now - volumePreviousNanos) / 1_000_000_000f).coerceIn(0f, .064f)
            volumePreviousNanos = now
            if (volumeReturnAt != 0L && SystemClock.uptimeMillis() >= volumeReturnAt) {
                volumeReturnAt = 0L
                volumeNudge.target(0f, 400f, 40f)
            }
            val moving = volumeWidth.advance(dt) or volumePop.advance(dt) or volumeNudge.advance(dt) or
                volumeDragX.advance(dt) or volumeDragY.advance(dt) or volumeDragScaleX.advance(dt) or volumeDragScaleY.advance(dt)
            surfaces.firstOrNull()?.let { card ->
                val size = (dp(volumeWidth.value) * volumePreviewScale).roundToInt().coerceAtLeast(1)
                card.layoutParams?.let { params -> if (params.width != size) { params.width = size; card.layoutParams = params } }
                val pop = VolumeMotionModel.popTransform(volumePop.value, number("volume_pop_scale", .2f),
                    dp(number("volume_pop_shift", 5.5f)), number("volume_pop_power", 3f))
                card.scaleX = pop.scale * volumeDragScaleX.value
                card.scaleY = pop.scale * volumeDragScaleY.value
                // The preview's right edge represents the screen edge, with the same
                // physical-pixel gap as the runtime visible track.
                card.translationX = volumeEdgeTranslation() + dp(volumeNudge.value) + pop.x + volumeDragX.value
                card.translationY = pop.y + volumeDragY.value + number("volume_offset_y", 0f).toInt()
                volumeArtwork?.iconOpacity = VolumeMotionModel.iconOpacity(volumeWidth.value, number("volume_thin_width", 9f), 52f)
            }
            if (moving || volumeReturnAt != 0L) postOnAnimation(this) else volumeScheduled = false
        }
    }

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, dp(26).toFloat()) }
        }
        addView(wallpaper, LayoutParams(-1, -1))
        addView(content, LayoutParams(-1, -1).apply { bottomMargin = dp(35) })
        caption.apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, 0x65000000))
        }
        addView(caption, LayoutParams(-1, dp(35), Gravity.BOTTOM))
        setOnClickListener { if (removed) replay() }
        content.setOnTouchListener { _, event ->
            if (scene != Scene.VOLUME || !behavior.volume) false else {
                if (event.actionMasked == MotionEvent.ACTION_DOWN && !volumeHit(event.x, event.y)) return@setOnTouchListener false
                if (event.actionMasked != MotionEvent.ACTION_DOWN && !volumeTouching) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        volumeDownX = event.rawX; volumeDownY = event.rawY
                        volumeLastX = volumeDownX; volumeLastY = volumeDownY
                        volumeVelocityX = 0f; volumeVelocityY = 0f
                        volumeLastMotionTime = event.eventTime
                        volumeTouching = true
                        pressVolume(true)
                    }
                    MotionEvent.ACTION_MOVE -> if (volumeTouching && event.pointerCount == 1) {
                        val elapsed = event.eventTime - volumeLastMotionTime
                        if (elapsed > 0) {
                            volumeVelocityX += (((event.rawX - volumeLastX) * 1000f / elapsed) - volumeVelocityX) * .4f
                            volumeVelocityY += (((event.rawY - volumeLastY) * 1000f / elapsed) - volumeVelocityY) * .4f
                        }
                        volumeLastX = event.rawX; volumeLastY = event.rawY; volumeLastMotionTime = event.eventTime
                        dragVolume(event.rawX - volumeDownX, event.rawY - volumeDownY)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(false)
                        val releaseMomentum = event.actionMasked == MotionEvent.ACTION_UP && volumeTouching
                        volumeTouching = false
                        if (releaseMomentum) {
                            volumeDragX.seedVelocity(volumeVelocityX * .45f)
                            volumeDragY.seedVelocity(volumeVelocityY * .45f)
                        } else {
                            volumeVelocityX = 0f; volumeVelocityY = 0f
                        }
                        volumeDragX.target(0f, 380f, 28f); volumeDragY.target(0f, 380f, 28f)
                        volumeDragScaleX.target(1f, 380f, 28f); volumeDragScaleY.target(1f, 380f, 28f)
                        pressVolume(false)
                    }
                }
                true
            }
        }
        content.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) layoutScene()
        }
        buildScene()
    }

    fun setScene(value: Scene) {
        if (scene == value) return
        scene = value
        expanded = false
        removed = false
        buildScene()
    }

    fun setFocusGroup(group: String) {
        focusGroup = group
        if (renderOnly) return
        when {
            group.contains("下拉") -> setScene(Scene.SHADE)
            group.contains("顶部横幅") || group.contains("悬浮") -> setScene(Scene.HEADS_UP)
            group.contains("通知") -> setScene(Scene.NOTIFICATION)
            group.contains("音量") -> setScene(Scene.VOLUME)
            group.contains("侧栏") || group.contains("侧边") -> setScene(Scene.SIDEBAR)
            group.contains("负一") || group.contains("组件") -> setScene(Scene.WIDGET)
            group.contains("控制") || group.contains("圆角") -> setScene(Scene.CONTROL)
        }
    }

    fun setNight(value: Boolean) {
        night = value
        material.setNight(value)
        wallpaper.night = value
        wallpaper.invalidate()
        if (surfaces.isNotEmpty()) buildScene() else if (shadePreview != null) updateCaption()
    }

    /** The material editor respects runtime scope and geometry switches. */
    fun setMaterialComparison(value: Boolean) {
        if (materialComparison == value && captionConfigured) return
        captionConfigured = true
        materialComparison = value
        updateCaptionLayout()
        refresh()
    }

    /** The pinned area keeps a short action hint; diagnostic metadata stays in accessibility. */
    fun setRenderOnly(value: Boolean) {
        if (renderOnly == value) return
        renderOnly = value
        updateCaptionLayout()
        updateCaption()
    }

    private fun updateCaptionLayout() {
        val lines = if (renderOnly) 2 else if (materialComparison) 3 else 2
        val height = max(dp(if (renderOnly) 30 else if (materialComparison) 44 else 35),
            kotlin.math.ceil(caption.paint.fontSpacing * lines).toInt() + caption.paddingTop + caption.paddingBottom)
        caption.visibility = View.VISIBLE
        caption.maxLines = lines
        caption.ellipsize = android.text.TextUtils.TruncateAt.END
        caption.layoutParams = LayoutParams(-1, height, Gravity.BOTTOM)
        content.layoutParams = LayoutParams(-1, -1).apply { bottomMargin = if (renderOnly) 0 else height }
    }

    fun setFocusedSetting(key: String) {
        if (focusedSetting == key) return
        val old = focusedSetting
        focusedSetting = key
        expanded = false
        val oldSample = sampleVariant(old)
        if (sampleVariant(key) != oldSample && scene == Scene.VOLUME) buildScene()
        else { cancelAnimations(); layoutScene() }
    }

    /** Only an actual edit selects one CC geometry sample; scrolling never calls this. */
    fun focusSetting(key: String) {
        val next = key.takeIf { it in setOf("cc_card_radius", "cc_tile_radius", "cc_media_radius", "cc_slider_radius", "cc_device_radius", "cc_secondary_radius") }.orEmpty()
        if (next == ccSampleKey) return
        ccSampleKey = next
        if (scene == Scene.CONTROL) buildScene()
    }

    /** Coalesce slider writes; play once with the newly persisted configuration. */
    fun previewFocusedEffect() {
        removeCallbacks(focusedPreview)
        postDelayed(focusedPreview, 180L)
    }

    private fun sampleVariant(key: String): String = when {
        key.startsWith("cc_") -> key.removeSuffix("_enabled").removeSuffix("_radius")
        key.startsWith("volume_timer_corner") -> "volume_timer"
        else -> "default"
    }

    private fun replayFocused() {
        cancelAnimations()
        removed = false
        mainCard?.visibility = View.VISIBLE
        when (previewActionFor(focusedSetting)) {
            PreviewAction.PRESS -> if (behavior.tiltFor(scene.name)) mainCard?.let { card ->
                pointerX = .85f; pointerY = .25f
                animatePress(card, 1f, 320, .72f, 9f) { animatePress(card, 0f, 420, .48f, 8f) }
            }
            PreviewAction.HEADS_UP_ENTER -> playHeadsUp(false)
            PreviewAction.HEADS_UP_EXIT -> playHeadsUp(true)
            PreviewAction.ROW_ENTER -> playStack(false)
            PreviewAction.ROW_EXIT -> playStack(true)
            PreviewAction.VOLUME_POP -> showVolume()
            PreviewAction.VOLUME_WIDTH -> if (behavior.volume) {
                showVolume()
                // A second key update is distinct from the initial wide show.
                handler.postDelayed({
                    if (isAttachedToWindow && !released && scene == Scene.VOLUME && !volumeTouching)
                        updateVolumePhase(VolumeInteractionEvent.HARDWARE_KEY)
                }, 600L)
            }
            PreviewAction.VOLUME_NUDGE -> if (behavior.volume) {
                volumeState = VolumeInteractionModel.transition(VolumeInteractionState(visible = true), VolumeInteractionEvent.HARDWARE_KEY)
                volumeWidth.snap(number("volume_thin_width", 9f))
                layoutScene()
                val (k, d) = VolumeMotionModel.nudgeCoefficients(number("volume_nudge_response", .8f), number("volume_nudge_damping", .9f))
                volumeNudge.target(-number("volume_nudge", 6.5f), k, d)
                volumeReturnAt = SystemClock.uptimeMillis() + 100L
                scheduleVolume()
            }
            PreviewAction.GROUP -> { expanded = false; layoutScene() }
            PreviewAction.STATIC -> {
                if (scene == Scene.VOLUME && focusedSetting == "volume_edge_gap" && behavior.volume) {
                    volumeState = VolumeInteractionModel.transition(VolumeInteractionState(visible = true), VolumeInteractionEvent.HARDWARE_KEY)
                    volumeWidth.snap(number("volume_thin_width", 9f))
                }
                layoutScene()
            }
        }
    }

    fun setWallpaperStyle(style: Int) {
        imageGeneration++
        wallpaper.setImage(null)
        wallpaper.style = style
        shadePreview?.setBackdrop(null, style)
        wallpaper.invalidate()
    }

    fun loadWallpaper(uri: Uri) {
        val generation = ++imageGeneration
        Thread {
            val result = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("图片解码失败")
            }
            post {
                val image = result.getOrNull()
                if (!released && generation == imageGeneration) {
                    if (image != null) { wallpaper.setImage(image); shadePreview?.setBackdrop(image, wallpaper.style); refresh() }
                    else Toast.makeText(context, sampleText(result.exceptionOrNull()?.message ?: "无法读取图片"), Toast.LENGTH_SHORT).show()
                } else image?.recycle()
            }
        }.start()
    }

    fun refresh() {
        if (released) return
        // Numeric animation inputs are captured when playback starts. Retire an
        // old playback before applying a new configuration rather than resetting
        // layout underneath an animator that still writes its previous values.
        cancelAnimations()
        behavior = previewBehavior(preferences.all)
        readVolumePlacement()
        removed = false
        mainCard?.visibility = View.VISIBLE
        surfaces.forEach { it.updateMaterial() }
        layoutScene()
        updateCaption()
    }

    fun replay() {
        if (focusedSetting.isNotEmpty()) { replayFocused(); return }
        cancelAnimations()
        removed = false
        mainCard?.visibility = View.VISIBLE
        when (scene) {
            Scene.HEADS_UP -> playHeadsUp(false)
            Scene.NOTIFICATION -> if (listAnimationFocus()) playStack(false) else {
                expanded = !expanded
                layoutScene(animated = true)
            }
            Scene.VOLUME -> showVolume()
            Scene.MEDIA -> if (behavior.mediaTilt) mainCard?.let { card -> animatePress(card, 1f, 320, .72f, 9f) { animatePress(card, 0f, 420, .48f, 8f) } }
            else -> {
                surfaces.forEach { card ->
                    animate(450) { t ->
                        val p = NotificationMotion.spring(t, .65f, 9f)
                        card.scaleX = .94f + .06f * p
                        card.scaleY = .94f + .06f * p
                    }
                }
            }
        }
    }

    private fun buildScene() {
        cancelAnimations()
        nativeMaterialActive = null
        surfaces.forEach { material.clear(it) }
        content.removeAllViews()
        surfaces.clear()
        mainCard = null
        shadePreview = null
        volumeArtwork = null
        when (scene) {
            Scene.NOTIFICATION -> {
                repeat(2) { index -> addCard("稍后提醒", sampleText("还有 %d 条通知", 2 - index), "●", false) }
                mainCard = addCard("今天的安排", "留一点时间，看看窗外。", "◉", true)
                mainCard?.setOnClickListener {
                    if (renderOnly && focusedSetting.isNotEmpty() && previewActionFor(focusedSetting) != PreviewAction.GROUP) replayFocused()
                    else if (listAnimationFocus()) playStack(!removed)
                    else { expanded = !expanded; layoutScene(animated = true) }
                }
            }
            Scene.HEADS_UP -> {
                mainCard = addCard("新的消息", "晚餐改到七点，稍后见。", "↗", true)
                mainCard?.setOnClickListener {
                    if (renderOnly && focusedSetting.isNotEmpty()) replayFocused() else playHeadsUp(true)
                }
            }
            Scene.MEDIA -> {
                mainCard = addCard("沿岸漫步", "Studio Lite  ·  02:16 / 04:32\n\n        ◀       Ⅱ       ▶", "♫", true)
                mainCard?.setOnClickListener { replay() }
            }
            Scene.CONTROL -> {
                val keys = if (ccSampleKey.isEmpty()) listOf("cc_card_radius", "cc_tile_radius", "cc_slider_radius", "cc_media_radius", "cc_device_radius", "cc_secondary_radius") else listOf(ccSampleKey)
                keys.forEach { key ->
                    val sample = when (key) {
                        "cc_tile_radius" -> Triple("蓝牙", "已开启", "ᛒ")
                        "cc_slider_radius" -> Triple("屏幕亮度", "━━━━━━━ ──", "☀")
                        "cc_media_radius" -> Triple("正在播放", "沿岸漫步   Ⅱ", "♫")
                        "cc_device_radius" -> Triple("设备中心", "附近的设备", "▣")
                        "cc_secondary_radius" -> Triple("更多控制", "无线网络", "⋮")
                        else -> Triple("无线网络", "已连接", "⌁")
                    }
                    addCard(sample.first, sample.second, sample.third, false).radiusKey = key
                }
            }
            Scene.VOLUME -> {
                addCard("", "", "♪", false).radiusKey = "volume_corner"
                addCard("静音定时", "30 分钟", "◷", false).radiusKey = "volume_timer_corner"
                for (kind in listOf(1, 2)) {
                    val button = SampleSurface(context).apply {
                        radiusKey = ""
                        pill = true
                        contentDescription = sampleText(if (kind == 1) "静音按钮" else "勿扰按钮")
                        addView(VolumeArtwork(context, kind), LayoutParams(-1, -1))
                    }
                    surfaces += button
                    content.addView(button, LayoutParams(dp(52), dp(34)))
                }
            }
            Scene.WIDGET -> {
                addCard("晴朗", "26°\n适合出门走走", "☀", false)
                addCard("今日步数", "6,280\n刚刚更新", "◌", false)
            }
            Scene.SIDEBAR -> {
                addCard("侧边工具箱", "截图    录屏    小窗\n\n        ▣        ◉        ▤", "⋮", false)
            }
            Scene.SHADE -> {
                shadePreview = ShadeBackdropPreview(context).also {
                    it.setBackdrop(wallpaper.currentImage, wallpaper.style)
                    content.addView(it, LayoutParams(-1, -1))
                }
            }
        }
        post { if (!released) { layoutScene(); refresh() } }
    }

    private fun addCard(title: String, detail: String, icon: String, interactive: Boolean): SampleSurface {
        val displayTitle = sampleText(title)
        val displayDetail = sampleText(detail)
        val card = SampleSurface(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val foreground = if (night) Color.WHITE else Color.rgb(30, 41, 49)
        val badge = TextView(context).apply {
            text = icon
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(foreground)
            background = GradientDrawable().apply { setColor(if (night) 0x18ffffff else 0x15ffffff); cornerRadius = dp(13).toFloat() }
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(12) })
        val words = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        words.addView(TextView(context).apply { text = displayTitle; textSize = 13f; setTextColor(foreground); setTypeface(typeface, Typeface.BOLD); maxLines = 1 })
        val detailText = TextView(context).apply {
            text = displayDetail; textSize = 11f; setTextColor(foreground); alpha = .75f
            setPadding(0, dp(5), 0, 0); maxLines = 5
        }
        words.addView(detailText)
        card.detailText = detailText
        card.badgeView = badge
        card.labelRow = row
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        if (scene == Scene.VOLUME && title.isEmpty()) {
            volumeArtwork = VolumeArtwork(context, 0)
            card.addView(volumeArtwork, LayoutParams(-1, -1))
        } else card.addView(row, LayoutParams(-1, -1))
        card.contentDescription = sampleText("%s。%s。仅本地预览。", displayTitle, displayDetail)
        card.cameraDistance = dp(1400).toFloat()
        if (interactive) {
            card.isClickable = true
            card.isFocusable = true
            card.setOnTouchListener { _, event -> touchCard(card, event) }
        }
        surfaces += card
        content.addView(card, LayoutParams(-1, dp(78)))
        return card
    }

    private fun layoutScene(animated: Boolean = false) {
        val w = content.width
        val h = content.height
        if (w <= 0 || h <= 0) return
        fun place(card: SampleSurface, x: Int, y: Int, width: Int, height: Int, scale: Float = 1f) {
            val previousTop = card.top.toFloat() + card.translationY
            card.layoutParams = LayoutParams(width.coerceAtLeast(1), height.coerceAtLeast(1)).apply { leftMargin = x; topMargin = y }
            card.pivotX = width * .5f
            card.pivotY = height * .5f
            card.setCompact(height < dp(58), height < dp(38))
            if (animated) {
                val oldX = card.scaleX; val oldY = card.scaleY
                val travel = previousTop - y
                card.translationY = travel
                animate(400) { t ->
                    val p = NotificationMotion.spring(t, .7f, 8f)
                    card.scaleX = oldX + (scale - oldX) * p
                    card.scaleY = oldY + (scale - oldY) * p
                    card.translationY = travel * (1f - p)
                }
            } else if (pressAnimator?.isRunning != true) { card.scaleX = scale; card.scaleY = scale; card.translationY = 0f }
            card.updateMaterial()
        }
        val margin = dp(if (h < dp(150)) 10 else 22).coerceAtMost(w / 8)
        val inset = dp(if (h < dp(150)) 6 else 12).coerceAtMost(h / 8)
        val availableHeight = (h - inset * 2).coerceAtLeast(1)
        val width = (w - margin * 2).coerceAtLeast(1)
        when (scene) {
            Scene.NOTIFICATION -> {
                val gap = dp(6).coerceAtMost(availableHeight / 8)
                val cardHeight = if (expanded) ((availableHeight - gap * 2) / 3).coerceAtLeast(1)
                    else dp(82).coerceAtMost((availableHeight - dp(12)).coerceAtLeast(1))
                val top = ((h - if (expanded) cardHeight * 3 + gap * 2 else cardHeight) / 2).coerceAtLeast(inset)
                surfaces.forEachIndexed { i, card ->
                    val depth = 2 - i
                    if (card !== mainCard) card.visibility = if (expanded || behavior.group) View.VISIBLE else View.INVISIBLE
                    val frame = NotificationMotion.group(depth, 0f, number("notification_group_stack_scale", .95f))
                    val offset = depth * NotificationMotion.groupSpacingPx(number("notification_group_stack_spacing", 6f), resources.displayMetrics.density)
                    val y = if (expanded) top + depth * (cardHeight + gap) else top + offset.roundToInt().coerceAtMost((h - top - cardHeight).coerceAtLeast(0))
                    place(card, margin, y, width, cardHeight, if (expanded) 1f else frame.scale)
                    card.alpha = if (expanded || depth == 0) 1f else .92f
                }
            }
            Scene.HEADS_UP -> surfaces.firstOrNull()?.let { place(it, margin, inset, width, dp(82).coerceAtMost(availableHeight)) }
            Scene.MEDIA -> surfaces.firstOrNull()?.let {
                val height = dp(128).coerceAtMost(availableHeight)
                place(it, margin, (h - height) / 2, width, height)
            }
            Scene.CONTROL -> {
                if (surfaces.size == 1) {
                    val card = surfaces.first()
                    val sampleWidth = when (card.radiusKey) {
                        "cc_tile_radius" -> dp(108).coerceAtMost(width)
                        "cc_card_radius" -> dp(230).coerceAtMost(width)
                        else -> width
                    }
                    val sampleHeight = when (card.radiusKey) {
                        "cc_tile_radius" -> sampleWidth
                        "cc_slider_radius" -> dp(64)
                        "cc_secondary_radius" -> dp(150)
                        "cc_media_radius" -> dp(120)
                        else -> dp(96)
                    }.coerceAtMost(availableHeight)
                    place(card, (w - sampleWidth) / 2, (h - sampleHeight) / 2, sampleWidth, sampleHeight)
                    updateCaption()
                    return
                }
                val columns = if (width >= dp(420) || h < dp(110)) 3 else 2
                val rows = (surfaces.size + columns - 1) / columns
                val gap = dp(if (h < dp(150)) 5 else 10).coerceAtMost(availableHeight / 12)
                val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(1)
                val cellHeight = ((availableHeight - gap * (rows - 1)) / rows).coerceAtLeast(1)
                surfaces.forEachIndexed { i, card ->
                    place(card, margin + (i % columns) * (cellWidth + gap), inset + (i / columns) * (cellHeight + gap), cellWidth, cellHeight)
                }
            }
            Scene.VOLUME -> {
                if (sampleVariant(focusedSetting) == "volume_timer") {
                    surfaces.getOrNull(0)?.visibility = View.GONE
                    surfaces.getOrNull(2)?.visibility = View.GONE
                    surfaces.getOrNull(3)?.visibility = View.GONE
                    surfaces.getOrNull(1)?.let {
                        it.visibility = View.VISIBLE
                        val height = dp(68).coerceAtMost(availableHeight)
                        place(it, margin, (h - height) / 2, width, height)
                    }
                    updateCaption()
                    return
                }
                surfaces.getOrNull(0)?.visibility = View.VISIBLE
                surfaces.getOrNull(1)?.visibility = View.GONE
                val hideRinger = VolumePanelAppearance.hideRinger(false, false, behavior.volume && volumeState.narrow,
                    preferences.getBoolean("volume_hide_ringer", false))
                val layout = VolumePreviewLayout.fit(w.toFloat(), availableHeight.toFloat(), resources.displayMetrics.density,
                    if (behavior.volume) volumeWidth.value else 52f, if (behavior.volume) number("volume_height", 150f) else 150f)
                volumePreviewScale = layout.scale
                volumePreviewInset = w - layout.barLeft - layout.barWidth
                val barWidth = layout.barWidth.roundToInt().coerceAtLeast(1)
                val barHeight = layout.barHeight.roundToInt().coerceAtLeast(1)
                surfaces.getOrNull(0)?.let {
                    place(it, layout.barLeft.roundToInt(), inset + layout.barTop.roundToInt(), barWidth, barHeight)
                    // Keep the visual right edge fixed while its width spring runs.
                    // The native wide session stays at its ordinary inset; narrowing
                    // moves its visible edge to the configured screen gap.
                    (it.layoutParams as LayoutParams).apply {
                        gravity = Gravity.TOP or Gravity.RIGHT
                        leftMargin = 0
                        rightMargin = volumePreviewInset.roundToInt()
                    }
                    it.requestLayout()
                    it.translationX = if (behavior.volume) volumeEdgeTranslation() else 0f
                    it.translationY = if (behavior.volume) number("volume_offset_y", 0f).toInt().toFloat() else 0f
                    volumeArtwork?.iconOpacity = if (behavior.volume) VolumeMotionModel.iconOpacity(volumeWidth.value, number("volume_thin_width", 9f), 52f) else 1f
                }
                for ((index, top) in listOf(2 to layout.muteTop, 3 to layout.dndTop)) {
                    surfaces.getOrNull(index)?.let {
                        it.visibility = if (hideRinger) View.GONE else View.VISIBLE
                        if (!hideRinger) place(it, layout.buttonLeft.roundToInt(), inset + top.roundToInt(),
                            layout.buttonWidth.roundToInt(), layout.buttonHeight.roundToInt())
                    }
                }
            }
            Scene.WIDGET -> {
                val half = (width - dp(10)) / 2
                val height = dp(126).coerceAtMost(availableHeight)
                surfaces.forEachIndexed { i, card -> place(card, margin + i * (half + dp(10)), (h - height) / 2, half, height) }
            }
            Scene.SIDEBAR -> surfaces.firstOrNull()?.let {
                val height = dp(132).coerceAtMost(availableHeight)
                place(it, margin, (h - height) / 2, width, height)
            }
            Scene.SHADE -> shadePreview?.let { it.layout(0, 0, content.width, content.height) }
        }
        updateCaption()
    }

    private fun touchCard(card: SampleSurface, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAnimations()
                downX = event.x; downY = event.y; moved = false
                pointerX = (event.x / card.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                pointerY = (event.y / card.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                parent?.requestDisallowInterceptTouchEvent(true)
                if (behavior.tiltFor(scene.name)) animatePress(card, 1f, 320, .72f, 9f)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                moved = moved || abs(event.x - downX) + abs(event.y - downY) > dp(8)
                pointerX = (event.x / card.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                pointerY = (event.y / card.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                if (behavior.tiltFor(scene.name)) applyPress(card)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (behavior.tiltFor(scene.name)) animatePress(card, 0f, 420, .48f, 8f)
                if (event.actionMasked == MotionEvent.ACTION_UP && !moved) card.performClick()
                return true
            }
        }
        return true
    }

    private fun animatePress(card: SampleSurface, target: Float, duration: Int, damping: Float, frequency: Float, end: (() -> Unit)? = null) {
        pressAnimator?.cancel()
        val from = pressProgress
        pressAnimator = animate(duration.toLong(), end = end) { t ->
            pressProgress = from + (target - from) * NotificationMotion.spring(t, damping, frequency)
            applyPress(card)
        }
    }

    private fun applyPress(card: SampleSurface) {
        val frame = NotificationMotion.tilt(pointerX, pointerY, pressProgress, number("notification_press_tilt_max_deg", 4f), number("notification_press_tilt_scale", .96f))
        card.rotationX = frame.rotationX
        card.rotationY = frame.rotationY
        card.scaleX = frame.scale
        card.scaleY = frame.scale
        card.translationZ = dp(frame.zDp)
    }

    private fun playHeadsUp(exit: Boolean) {
        val card = mainCard ?: return
        cancelAnimations()
        if (!behavior.headsUp) { card.visibility = View.VISIBLE; layoutScene(); return }
        val prefix = if (exit) "notification_heads_up_pop_exit" else "notification_heads_up_pop"
        fun duration(key: String, fallback: Float) = number(key, fallback).coerceIn(200f, 1500f).toLong()
        fun delay(key: String, fallback: Float, scale: Boolean = false): Long =
            NotificationMotion.headsUpDelay(number(key, fallback).coerceIn(0f, 600f).toLong(), entering = !exit, scale = scale)
        val scaleDuration = duration("${prefix}_duration", 500f)
        val scaleDelay = delay("${prefix}_delay", 150f, scale = true)
        val flipDuration = duration("notification_heads_up_pop_flip_duration", 500f)
        val flipDelay = delay("notification_heads_up_pop_flip_delay", 100f)
        val blurDuration = duration("notification_heads_up_pop_blur_duration", 1000f)
        val blurDelay = delay("notification_heads_up_pop_blur_delay", 0f)
        val scale = number("${prefix}_scale", .6f).coerceIn(.5f, 1f)
        val flip = number("notification_heads_up_pop_flip", 20f).coerceIn(-80f, 80f)
        val blur = number("notification_heads_up_pop_blur", 50f).coerceIn(0f, 150f)
        val initial = if (exit) 0f else 1f
        card.visibility = View.VISIBLE
        card.scaleX = 1f - (1f - scale) * initial
        card.scaleY = card.scaleX
        card.rotationX = flip * initial
        card.alpha = 1f // Native heads-up visibility belongs to SystemUI, not this overlay.
        setBlur(card, blur * initial)
        val total = maxOf(scaleDuration + scaleDelay, flipDuration + flipDelay, blurDuration + blurDelay)
        animate(total, end = {
            if (exit) card.visibility = View.INVISIBLE
            card.setRenderEffect(null)
            card.rotationX = 0f
            card.scaleX = 1f; card.scaleY = 1f
            if (exit) handler.postDelayed({ if (isAttachedToWindow && !released) playHeadsUp(false) }, 450)
        }) { t ->
            val time = t * total
            fun phase(amount: Long, wait: Long, name: String): Float {
                val fraction = headCurve(((time - wait) / amount).coerceIn(0f, 1f), prefix, name)
                return if (exit) fraction else 1f - fraction
            }
            card.scaleX = 1f - (1f - scale) * phase(scaleDuration, scaleDelay, "scale")
            card.scaleY = card.scaleX
            card.rotationX = flip * phase(flipDuration, flipDelay, "flip")
            // Local RenderEffect illustrates timing; the system hook owns its OEM
            // surface-blur channel, so this is not a claim of identical raster output.
            setBlur(card, blur * phase(blurDuration, blurDelay, "blur"))
        }
    }

    private fun headCurve(t: Float, scalePrefix: String, phase: String): Float {
        val prefix = if (phase == "scale") scalePrefix else "notification_heads_up_pop"
        return if (preferences.getBoolean("${prefix}_spring", false)) {
            val dampingKey = if (phase == "scale") "${prefix}_damping" else "notification_heads_up_pop_${phase}_damping"
            NotificationMotion.spring(t, number(dampingKey, if (phase == "flip") .85f else .5f).coerceIn(.2f, 1.2f),
                if (phase == "scale") number("${prefix}_frequency", 8f).coerceIn(2f, 24f) else 8f)
        } else NotificationMotion.bezier(t,
            number("${prefix}_curve_x1", .22f).coerceIn(0f, 1f),
            number("${prefix}_curve_y1", 1f).coerceIn(-.5f, 2f),
            number("${prefix}_curve_x2", .36f).coerceIn(0f, 1f),
            number("${prefix}_curve_y2", 1f).coerceIn(-.5f, 2f))
    }

    private fun playStack(exit: Boolean) {
        val card = mainCard ?: return
        cancelAnimations()
        if (!behavior.stack) { card.visibility = View.VISIBLE; layoutScene(); return }
        val prefix = if (exit) "notification_stack_anim_remove" else "notification_stack_anim_add"
        val duration = number("${prefix}_duration", if (exit) 500f else 700f).toLong()
        val startScale = number("${prefix}_scale", .5f)
        val startAlpha = number("${prefix}_alpha", 0f)
        val blur = number("${prefix}_blur", if (exit) 20f else 50f)
        card.visibility = View.VISIBLE
        animate(duration.coerceAtLeast(1), end = {
            card.setRenderEffect(null)
            removed = exit
            if (exit) {
                card.visibility = View.INVISIBLE
                handler.postDelayed({ if (isAttachedToWindow && !released) playStack(false) }, 550)
            } else { card.scaleX = 1f; card.scaleY = 1f; card.alpha = 1f }
        }) { t ->
            val p = NotificationMotion.spring(t, number("notification_stack_anim_damping", .6f), number("notification_stack_anim_frequency", 8f))
            val fraction = if (exit) p else 1f - p
            card.scaleX = 1f - (1f - startScale) * fraction
            card.scaleY = card.scaleX
            card.alpha = (1f - (1f - startAlpha) * fraction).coerceIn(0f, 1f)
            setBlur(card, blur * fraction.coerceAtLeast(0f))
        }
    }

    private fun showVolume() {
        if (!behavior.volume) { layoutScene(); return }
        readVolumePlacement()
        volumeState = VolumeInteractionModel.transition(VolumeInteractionState(), VolumeInteractionEvent.SHOW)
        volumeWidth.snap(52f)
        layoutScene()
        // The source parameter is the initial scale, not an amount subtracted from 1.
        // popTransform maps progress 0..1 to the shared scale/negative-translation curve.
        volumePop.snap(0f)
        val (k, d) = VolumeMotionModel.popCoefficients(number("volume_pop_duration", 1000f), number("volume_pop_damping", 1f))
        volumePop.target(1f, k, d)
        volumeNudge.snap(0f)
        volumeReturnAt = 0L
        scheduleVolume()
    }

    private fun dragVolume(dx: Float, dy: Float) {
        val card = surfaces.firstOrNull() ?: return
        // Match the stable owner column, not the narrowing visual slider inside it.
        val target = VolumeMotionModel.dragTransform(dx, dy, dp(52f), card.height.toFloat())
        volumeDragX.snap(target.x); volumeDragY.snap(target.y)
        volumeDragScaleX.snap(target.scaleX); volumeDragScaleY.snap(target.scaleY)
        scheduleVolume()
    }

    private fun pressVolume(pressed: Boolean) = updateVolumePhase(
        if (pressed) VolumeInteractionEvent.TOUCH_DOWN else VolumeInteractionEvent.TOUCH_UP)

    private fun updateVolumePhase(event: VolumeInteractionEvent) {
        volumeState = VolumeInteractionModel.transition(volumeState, event)
        val (k, d) = VolumeMotionModel.widthCoefficients(number("volume_motion_duration", 600f))
        volumeWidth.target(if (volumeState.narrow) number("volume_thin_width", 9f) else 52f, k, d)
        layoutScene()
        scheduleVolume()
    }

    private fun readVolumePlacement() {
        val values = preferences.all
        volumeEdgeGapPx = VolumePlacementModel.resolveEdgeGap(
            (values["volume_edge_gap"] as? Number)?.toFloat(),
            (values["volume_offset_x"] as? Number)?.toFloat())
    }

    private fun volumeEdgeGap(): Float = volumeEdgeGapPx

    private fun volumeEdgeTranslation(): Float = VolumePlacementModel.edgeTranslation(
        content.width.toFloat(), content.width - volumePreviewInset, volumeEdgeGap()) *
        VolumePlacementModel.narrowFraction(volumeWidth.value, number("volume_thin_width", 9f), 52f)

    private fun volumeHit(x: Float, y: Float): Boolean {
        val bar = surfaces.firstOrNull()?.takeIf { it.visibility == View.VISIBLE } ?: return false
        val bounds = RectF(0f, 0f, bar.width.toFloat(), bar.height.toFloat())
        bar.matrix.mapRect(bounds)
        bounds.offset(bar.left.toFloat(), bar.top.toFloat())
        return bounds.contains(x, y)
    }

    private fun listAnimationFocus(): Boolean = focusGroup.contains("显隐") || focusGroup.contains("列表动画")

    private fun scheduleVolume() {
        if (!volumeScheduled) {
            volumeScheduled = true
            volumePreviousNanos = System.nanoTime()
            postOnAnimation(volumeFrame)
        }
    }

    private fun setBlur(view: View, value: Float) {
        val radius = NotificationMotion.blurRadius(value)
        view.setRenderEffect(if (radius < .1f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }

    private fun animate(duration: Long, end: (() -> Unit)? = null, frame: (Float) -> Unit): ValueAnimator {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = duration.coerceAtLeast(1)
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { frame(it.animatedValue as Float) }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) { animations.remove(animator); if (!cancelled) end?.invoke() }
        })
        animations += animator
        animator.start()
        return animator
    }

    private fun cancelAnimations() {
        parent?.requestDisallowInterceptTouchEvent(false)
        animations.toList().forEach { it.cancel() }
        animations.clear()
        handler.removeCallbacksAndMessages(null)
        pressAnimator = null
        pressProgress = 0f
        removeCallbacks(volumeFrame)
        volumeScheduled = false
        volumeReturnAt = 0L
        volumeTouching = false
        volumePop.snap(1f)
        volumeNudge.snap(0f)
        volumeDragX.snap(0f); volumeDragY.snap(0f)
        volumeDragScaleX.snap(1f); volumeDragScaleY.snap(1f)
        surfaces.forEach {
            it.rotationX = 0f; it.rotationY = 0f; it.translationZ = 0f
            it.translationX = 0f; it.translationY = 0f
            it.scaleX = 1f; it.scaleY = 1f; it.alpha = 1f
            it.setRenderEffect(null)
        }
    }

    private fun updateCaption() {
        if (scene == Scene.SHADE) {
            val state = currentMaterialState().settings
            shadePreview?.update(state.enabled, state.backgroundBlur, state.backgroundOpacity, night)
            wallpaper.setSamplingFallback(false, 100, 100)
            nativeMaterialActive = false
            contentDescription = sampleText("示意预览，系统材质以实际面板为准")
            caption.text = sampleText("只调整后方背景，前方卡片保持不变")
            return
        }
        val visible = surfaces.filter { it.visibility == View.VISIBLE }
        val ready = visible.isNotEmpty() && visible.all { it.width > 0 && it.height > 0 }
        val native = visible.all { it.nativeApplied }
        if (ready && nativeMaterialActive != native) {
            nativeMaterialActive = native
            onMaterialStatusChanged?.invoke(native)
        }
        if (renderOnly) {
            contentDescription = "${sampleText(scene.title)} · " + sampleText(when {
                !ready -> "正在准备预览"
                native -> "App 内样例，原生材质接口已配置"
                else -> "示意预览，系统材质以实际面板为准"
            })
            val state = currentMaterialState()
            wallpaper.setSamplingFallback(scene == Scene.SHADE && ready && !native,
                if (state.settings.enabled) state.settings.backgroundBlur else 100,
                if (state.settings.enabled) state.settings.backgroundScale else 100)
            caption.text = sampleText(when {
                focusGroup in setOf("卡片材质", "材质与预设", "玻璃质感", "卡片背景", "卡片预设") || focusedSetting.startsWith("glass_") ->
                    "实时预览，实际质感以系统面板为准"
                scene == Scene.VOLUME && behavior.volume -> "连按图标试收窄，按住试变宽"
                scene == Scene.VOLUME -> "音量条样例"
                scene == Scene.HEADS_UP -> "轻点卡片重播出现与收起"
                scene == Scene.NOTIFICATION && listAnimationFocus() -> "轻点卡片查看出现与消失"
                scene == Scene.NOTIFICATION || scene == Scene.MEDIA -> "按住卡片查看按压效果"
                else -> "实时预览，实际质感以系统面板为准"
            })
            return
        }
        val materialLabel = when {
            !ready -> "正在准备材质"
            native -> "原生接口已调用"
            else -> "示意预览，非原生材质"
        }
        val action = when (scene) {
            Scene.NOTIFICATION -> if (listAnimationFocus()) {
                if (behavior.stack) "轻点查看出现与消失" else "通知出现与消失动画已关闭"
            } else if (behavior.rowTilt) "轻点展开 · 按住查看倾斜" else "轻点展开；按压效果已关闭"
            Scene.HEADS_UP -> if (behavior.headsUp) "轻点重播顶部横幅" else "顶部横幅动画已关闭"
            Scene.MEDIA -> if (behavior.mediaTilt) "按住查看倾斜" else "音乐按压效果已关闭"
            Scene.VOLUME -> if (behavior.volume) "首次宽条 · 连按收窄 · 按住变宽" else "窄音量条已关闭，显示普通宽度"
            else -> "实时调参"
        }
        val state = currentMaterialState()
        val shortMaterialLabel = when {
            !ready -> "准备中"
            native -> "原生接口已调用"
            else -> "示意预览"
        }
        val sampleLabel = sampleText(if (scene == Scene.SHADE) "整屏背景样例" else "通知配方样例")
        val label = if (materialComparison)
            "${sampleText(scene.title)} · ${sampleText(shortMaterialLabel)} · $sampleLabel\n${sampleText(state.scopeStatus)}\n${sampleText(state.scopeNote)}"
        else "${sampleText(materialLabel)} · ${sampleText("本地交互示意")}\n${sampleText("%s，不启用系统功能", sampleText(action))}"
        if (caption.text.toString() != label) caption.text = label
        // Only the shade fallback approximates background sampling. Never label
        // this RenderEffect as the unavailable OEM glass/refraction renderer.
        val fallbackBackground = scene == Scene.SHADE && ready && !native
        val blur = if (state.settings.enabled) state.settings.backgroundBlur else 100
        val scale = if (state.settings.enabled) state.settings.backgroundScale else 100
        wallpaper.setSamplingFallback(fallbackBackground, blur, scale)
    }

    override fun onDetachedFromWindow() { removeCallbacks(focusedPreview); cancelAnimations(); super.onDetachedFromWindow() }
    fun release() {
        released = true; imageGeneration++; removeCallbacks(focusedPreview); cancelAnimations(); wallpaper.setImage(null)
        onMaterialStatusChanged = null
        surfaces.forEach { material.clear(it) }
    }
    private fun number(key: String, default: Float) = (preferences.all[key] as? Number)?.toFloat() ?: default
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun sampleText(source: String, vararg arguments: Any): String {
        val template = localizedText(context, source)
        return if (arguments.isEmpty()) template else String.format(
            resources.configuration.locales[0] ?: Locale.getDefault(), template, *arguments,
        )
    }
    private fun currentMaterialState() = previewMaterialState(
        scene.name, preferences.all, readSoftGlassSettings(preferences), materialComparison,
    )

    /** Original local vector content: a speaker, muted bell and crescent. */
    private class VolumeArtwork(context: Context, private val kind: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shape = Path()
        private val cutout = Path()
        var iconOpacity = 1f
            set(value) { field = value; invalidate() }

        override fun onDraw(canvas: Canvas) {
            if (kind == 0) {
                paint.style = Paint.Style.FILL
                paint.color = 0xe8ffffff.toInt()
                canvas.drawRect(0f, height * .58f, width.toFloat(), height.toFloat(), paint)
            }
            val size = if (kind == 0) minOf(width * .56f, height * .16f) else minOf(width * .52f, height * .7f)
            if (size <= 0f) return
            val save = canvas.save()
            canvas.translate((width - size) / 2f, if (kind == 0) height * .84f - size / 2f else (height - size) / 2f)
            canvas.scale(size / 24f, size / 24f)
            paint.color = when (kind) { 0 -> Color.rgb(31, 132, 247); 1 -> Color.rgb(249, 99, 102); else -> Color.WHITE }
            paint.alpha = if (kind == 0) (255 * iconOpacity.coerceIn(0f, 1f)).roundToInt() else 255
            paint.strokeWidth = 2f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            shape.reset()
            when (kind) {
                0 -> {
                    paint.style = Paint.Style.FILL
                    shape.moveTo(2f, 8f); shape.lineTo(7f, 8f); shape.lineTo(13f, 3f)
                    shape.lineTo(13f, 21f); shape.lineTo(7f, 16f); shape.lineTo(2f, 16f); shape.close()
                    canvas.drawPath(shape, paint)
                    paint.style = Paint.Style.STROKE
                    canvas.drawArc(10f, 5f, 21f, 19f, -65f, 130f, false, paint)
                    canvas.drawArc(8f, 1f, 27f, 23f, -58f, 116f, false, paint)
                }
                1 -> {
                    paint.style = Paint.Style.STROKE
                    shape.moveTo(5f, 17f); shape.lineTo(7f, 13f); shape.lineTo(7f, 9f)
                    shape.cubicTo(7f, 2f, 17f, 2f, 17f, 9f); shape.lineTo(17f, 13f); shape.lineTo(19f, 17f); shape.close()
                    canvas.drawPath(shape, paint)
                    canvas.drawArc(9f, 17f, 15f, 23f, 25f, 130f, false, paint)
                    canvas.drawLine(3f, 2f, 21f, 22f, paint)
                }
                else -> {
                    paint.style = Paint.Style.FILL
                    shape.addCircle(12f, 12f, 10f, Path.Direction.CW)
                    cutout.reset(); cutout.addCircle(17f, 7f, 10f, Path.Direction.CW)
                    shape.op(cutout, Path.Op.DIFFERENCE)
                    canvas.drawPath(shape, paint)
                }
            }
            canvas.restoreToCount(save)
        }
    }

    private inner class SampleSurface(context: Context) : FrameLayout(context) {
        var nativeApplied = false
        var pill = false
        var radiusKey = if (scene == Scene.NOTIFICATION || scene == Scene.HEADS_UP || scene == Scene.MEDIA) "notification_radius" else ""
        var detailText: TextView? = null
        var badgeView: TextView? = null
        var labelRow: LinearLayout? = null
        private var radius = dp(24).toFloat()
        init {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, radius) }
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateMaterial() }
        }
        fun setCompact(compact: Boolean, tiny: Boolean) {
            detailText?.visibility = if (compact) View.GONE else View.VISIBLE
            val badgeSize = dp(if (tiny) 20 else if (compact) 26 else 34)
            badgeView?.apply {
                visibility = if (tiny) View.GONE else View.VISIBLE
                textSize = if (tiny) 15f else if (compact) 18f else 22f
                val params = layoutParams as LinearLayout.LayoutParams
                if (params.width != badgeSize) {
                    params.width = badgeSize; params.height = badgeSize
                    params.rightMargin = dp(if (compact) 7 else 12)
                    layoutParams = params
                }
            }
            labelRow?.setPadding(dp(if (compact) 8 else 12), dp(if (tiny) 2 else 6), dp(if (compact) 8 else 12), dp(if (tiny) 2 else 6))
        }
        fun updateMaterial() {
            val baseline = when (radiusKey) {
                "cc_card_radius", "cc_slider_radius", "volume_corner", "volume_timer_corner" -> 40f
                "cc_tile_radius" -> 24f
                else -> 26f
            }
            radius = if (pill && height > 0) height / 2f else dp(previewRadius(radiusKey, number(radiusKey, baseline), baseline, preferences.all, materialComparison))
            invalidateOutline()
            val state = currentMaterialState()
            nativeApplied = material.apply(this, state.settings, scene.name, state.headsUpGlass)
            val opacity = if (state.settings.enabled) state.settings.backgroundOpacity / 100f else 1f
            // Native glass owns tint/opacity through its OEM blend colors. A second
            // painted veil would make the editor look cloudier than its system recipe.
            background = if (nativeApplied) null else GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                if (night) Color.argb((166 * opacity).roundToInt().coerceIn(0, 240), 26, 37, 51)
                else Color.argb((185 * opacity).roundToInt().coerceIn(0, 240), 255, 255, 255),
                if (night) Color.argb((68 * opacity).roundToInt().coerceIn(0, 240), 43, 53, 71)
                else Color.argb((102 * opacity).roundToInt().coerceIn(0, 240), 255, 255, 255),
            )).apply { cornerRadius = radius; setStroke(dp(1), if (night) 0x28ffffff else 0x80ffffff.toInt()) }
            // Layout can finish after the Compose update that requested the sample.
            // Publish the actual configured result rather than leaving its label stale.
            updateCaption()
        }
    }

    private class WallpaperView(context: Context) : View(context) {
        var night = false
        var style = 0
        var currentImage: Bitmap? = null
            private set
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val matrix = Matrix()
        private var fallbackSampling = false
        private var fallbackBlur = 100
        private var fallbackScale = 100
        fun setSamplingFallback(enabled: Boolean, blur: Int, scale: Int) {
            if (fallbackSampling == enabled && fallbackBlur == blur && fallbackScale == scale) return
            fallbackSampling = enabled; fallbackBlur = blur; fallbackScale = scale
            val radius = if (enabled) 20f * blur / 100f * resources.displayMetrics.density else 0f
            setRenderEffect(if (radius > .1f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
            invalidate()
        }
        fun setImage(value: Bitmap?) { currentImage = value; invalidate() }
        override fun onDraw(canvas: Canvas) {
            val save = canvas.save()
            // OEM background-scale ratio is not image zoom. A local fallback must
            // not enlarge the artwork to suggest a relationship the API does not promise.
            val bitmap = currentImage
            if (bitmap != null && !bitmap.isRecycled) {
                val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                matrix.reset(); matrix.setScale(scale, scale)
                matrix.postTranslate((width - bitmap.width * scale) * .5f, (height - bitmap.height * scale) * .5f)
                paint.shader = null
                canvas.drawBitmap(bitmap, matrix, paint)
            } else if (style == 3) {
                drawContrastChart(canvas)
            } else {
                val colors = when (style) {
                    1 -> intArrayOf(0xff594173.toInt(), 0xffc47a87.toInt(), 0xffe4b898.toInt())
                    2 -> intArrayOf(0xffe9c189.toInt(), 0xffe4d3bc.toInt(), 0xff9ac8bf.toInt())
                    else -> intArrayOf(0xff396575.toInt(), 0xff72a3a2.toInt(), 0xffd2dcbd.toInt())
                }
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = RadialGradient(width * .82f, height * .12f, width * .55f, 0x80ffeac1.toInt(), 0x00ffeac1, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                val path = Path().apply {
                    moveTo(-width * .1f, height * .9f)
                    cubicTo(width * .15f, height * .2f, width * .7f, height * 1.4f, width * 1.1f, height * .45f)
                    lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close()
                }
                paint.shader = null; paint.color = 0x427dd9cd
                canvas.drawPath(path, paint)
            }
            if (night) { paint.shader = null; paint.color = 0x55091020; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint) }
            canvas.restoreToCount(save)
        }

        private fun drawContrastChart(canvas: Canvas) {
            paint.shader = null
            val cell = 24f * resources.displayMetrics.density
            val colors = intArrayOf(0xff17283e.toInt(), 0xffedf4ed.toInt())
            val columns = (width / cell).toInt() + 1
            val rows = (height / cell).toInt() + 1
            for (row in 0 until rows) for (column in 0 until columns) {
                paint.color = colors[(row + column) and 1]
                canvas.drawRect(column * cell, row * cell, (column + 1) * cell, (row + 1) * cell, paint)
            }
            // Fine lines reveal blur; saturated hard edges reveal refraction and tint.
            val stripeWidth = 10f * resources.displayMetrics.density
            val stripeColors = intArrayOf(0xffef5b55.toInt(), 0xffffd66b.toInt(), 0xff53c7ae.toInt(), 0xff6299ec.toInt())
            stripeColors.forEachIndexed { index, color ->
                paint.color = color
                val x = width * .2f + index * stripeWidth
                canvas.drawRect(x, 0f, x + stripeWidth, height.toFloat(), paint)
            }
            paint.color = Color.WHITE
            canvas.drawCircle(width * .77f, height * .32f, height * .17f, paint)
            paint.color = Color.BLACK
            paint.strokeWidth = 2f * resources.displayMetrics.density
            for (line in 0..5) {
                val y = height * .63f + line * 6f * resources.displayMetrics.density
                canvas.drawLine(width * .55f, y, width * .94f, y, paint)
            }
        }
    }
}
