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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** All preview gestures and animations remain within this Activity. */
class GlassPlayground(context: Context, private val preferences: SharedPreferences) : FrameLayout(context) {
    enum class Scene(val title: String) {
        NOTIFICATION("通知"), HEADS_UP("悬浮"), MEDIA("媒体"), CONTROL("控制中心"), VOLUME("音量"), WIDGET("负一屏"), SIDEBAR("侧栏")
    }
    var scene: Scene = Scene.NOTIFICATION
        private set
    private val wallpaper = WallpaperView(context)
    private val content = FrameLayout(context)
    private val caption = TextView(context)
    private val material = NativePreviewMaterial(context)
    private val surfaces = mutableListOf<SampleSurface>()
    private val animations = mutableListOf<ValueAnimator>()
    private val handler = Handler(Looper.getMainLooper())
    private var mainCard: SampleSurface? = null
    private var night = false
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
    private val volumeWidth = VolumeSpring(9f)
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
                val size = dp(volumeWidth.value).roundToInt().coerceAtLeast(1)
                card.layoutParams?.let { params -> if (params.width != size) { params.width = size; card.layoutParams = params } }
                val pop = VolumeMotionModel.popTransform(volumePop.value, number("volume_pop_scale", .2f),
                    dp(number("volume_pop_shift", 5.5f)), number("volume_pop_power", 3f))
                card.scaleX = pop.scale * volumeDragScaleX.value
                card.scaleY = pop.scale * volumeDragScaleY.value
                // OEM placement offsets are integer pixels, unlike the motion distances.
                // Increasing the right margin moves the card left in this local scene.
                card.translationX = dp(volumeNudge.value) + pop.x + volumeDragX.value - number("volume_offset_x", 0f).toInt()
                card.translationY = pop.y + volumeDragY.value + number("volume_offset_y", 0f).toInt()
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
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, 0x65000000))
            contentDescription = "预览状态"
        }
        addView(caption, LayoutParams(-1, dp(35), Gravity.BOTTOM))
        setOnClickListener { if (removed) replay() }
        content.setOnTouchListener { _, event ->
            if (scene != Scene.VOLUME) false else {
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
        when {
            group.contains("悬浮") -> setScene(Scene.HEADS_UP)
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
        if (surfaces.isNotEmpty()) buildScene()
    }

    fun setWallpaperStyle(style: Int) {
        imageGeneration++
        wallpaper.setImage(null)
        wallpaper.style = style
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
                    if (image != null) { wallpaper.setImage(image); refresh() }
                    else Toast.makeText(context, result.exceptionOrNull()?.message ?: "无法读取图片", Toast.LENGTH_SHORT).show()
                } else image?.recycle()
            }
        }.start()
    }

    fun refresh() {
        if (released) return
        surfaces.forEach { it.updateMaterial() }
        layoutScene()
        updateCaption()
    }

    fun replay() {
        cancelAnimations()
        removed = false
        mainCard?.visibility = View.VISIBLE
        when (scene) {
            Scene.HEADS_UP -> playHeadsUp(false)
            Scene.NOTIFICATION -> if (focusGroup.contains("显隐")) playStack(false) else {
                expanded = !expanded
                layoutScene(animated = true)
            }
            Scene.VOLUME -> showVolume()
            Scene.MEDIA -> mainCard?.let { card -> animatePress(card, 1f, 320, .72f, 9f) { animatePress(card, 0f, 420, .48f, 8f) } }
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
        content.removeAllViews()
        surfaces.clear()
        mainCard = null
        when (scene) {
            Scene.NOTIFICATION -> {
                repeat(2) { index -> addCard("稍后提醒", "还有 ${2 - index} 条通知", "●", false) }
                mainCard = addCard("今天的安排", "留一点时间，看看窗外。", "◉", true)
                mainCard?.setOnClickListener {
                    if (focusGroup.contains("显隐")) playStack(!removed)
                    else { expanded = !expanded; layoutScene(animated = true) }
                }
            }
            Scene.HEADS_UP -> {
                mainCard = addCard("新的消息", "轻点卡片，预览离开与再次弹出。", "↗", true)
                mainCard?.setOnClickListener { playHeadsUp(true) }
            }
            Scene.MEDIA -> {
                mainCard = addCard("沿岸漫步", "Studio Lite  ·  02:16 / 04:32\n\n        ◀       Ⅱ       ▶", "♫", true)
                mainCard?.setOnClickListener { replay() }
            }
            Scene.CONTROL -> {
                addCard("无线网络", "已连接", "⌁", false).radiusKey = "cc_card_radius"
                addCard("蓝牙", "已开启", "ᛒ", false).radiusKey = "cc_card_radius"
                addCard("屏幕亮度", "━━━━━━━ ──", "☀", false).radiusKey = "cc_slider_radius"
                addCard("正在播放", "沿岸漫步   Ⅱ", "♫", false).radiusKey = "cc_media_radius"
            }
            Scene.VOLUME -> {
                addCard("", "", "♪", false).radiusKey = "volume_corner"
                addCard("静音定时", "30 分钟", "◷", false).radiusKey = "volume_timer_corner"
            }
            Scene.WIDGET -> {
                addCard("晴朗", "26°\n适合出门走走", "☀", false)
                addCard("今日步数", "6,280\n刚刚更新", "◌", false)
            }
            Scene.SIDEBAR -> {
                addCard("侧边工具箱", "截图    录屏    小窗\n\n        ▣        ◉        ▤", "⋮", false)
            }
        }
        post { layoutScene(); refresh() }
    }

    private fun addCard(title: String, detail: String, icon: String, interactive: Boolean): SampleSurface {
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
        words.addView(TextView(context).apply { text = title; textSize = 13f; setTextColor(foreground); setTypeface(typeface, Typeface.BOLD); maxLines = 1 })
        words.addView(TextView(context).apply {
            text = detail; textSize = 11f; setTextColor(foreground); alpha = .75f
            setPadding(0, dp(5), 0, 0); maxLines = 5
        })
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        if (scene == Scene.VOLUME && title.isEmpty()) {
            card.addView(object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    paint.color = if (night) 0xafffffff.toInt() else 0xbfffffff.toInt()
                    canvas.drawRect(0f, height * .32f, width.toFloat(), height.toFloat(), paint)
                }
            }, LayoutParams(-1, -1))
        } else card.addView(row, LayoutParams(-1, -1))
        card.contentDescription = "$title。$detail。仅本地预览。"
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
        val margin = dp(22)
        val width = (w - margin * 2).coerceAtLeast(dp(160))
        when (scene) {
            Scene.NOTIFICATION -> {
                val cardHeight = if (expanded) ((h - dp(34)) / 3).coerceAtLeast(dp(47)) else dp(82)
                val top = ((h - if (expanded) cardHeight * 3 + dp(12) else cardHeight) / 2).coerceAtLeast(dp(12))
                surfaces.forEachIndexed { i, card ->
                    val depth = 2 - i
                    val frame = NotificationMotion.group(depth, number("notification_group_stack_spacing", 6f), number("notification_group_stack_scale", .95f))
                    val y = if (expanded) top + depth * (cardHeight + dp(6)) else top + dp(frame.offsetDp).roundToInt()
                    place(card, margin, y, width, cardHeight, if (expanded) 1f else frame.scale)
                    card.alpha = if (expanded || depth == 0) 1f else .92f
                }
            }
            Scene.HEADS_UP -> surfaces.firstOrNull()?.let { place(it, margin, dp(24), width, dp(82)) }
            Scene.MEDIA -> surfaces.firstOrNull()?.let { place(it, margin, ((h - dp(128)) / 2).coerceAtLeast(dp(8)), width, dp(128)) }
            Scene.CONTROL -> {
                val gap = dp(10)
                val half = (width - gap) / 2
                val height = ((h - dp(34) - gap) / 2).coerceAtLeast(dp(65))
                surfaces.forEachIndexed { i, card -> place(card, margin + (i % 2) * (half + gap), dp(17) + (i / 2) * (height + gap), half, height) }
            }
            Scene.VOLUME -> {
                val x = w / 2 - dp(38)
                val barWidth = dp(if (volumeScheduled) volumeWidth.value else number("volume_thin_width", 9f)).roundToInt().coerceIn(dp(4), dp(84))
                val barHeight = dp(number("volume_height", 150f)).roundToInt().coerceIn(dp(80), (h - dp(24)).coerceAtLeast(dp(80)))
                surfaces.getOrNull(0)?.let { place(it, x, (h - barHeight) / 2, barWidth, barHeight) }
                surfaces.getOrNull(1)?.let { place(it, x + dp(64), h / 2 - dp(34), (w - x - dp(84)).coerceAtLeast(dp(90)), dp(68)) }
            }
            Scene.WIDGET -> {
                val half = (width - dp(10)) / 2
                surfaces.forEachIndexed { i, card -> place(card, margin + i * (half + dp(10)), ((h - dp(126)) / 2).coerceAtLeast(dp(10)), half, dp(126)) }
            }
            Scene.SIDEBAR -> surfaces.firstOrNull()?.let { place(it, margin, ((h - dp(132)) / 2).coerceAtLeast(dp(10)), width, dp(132)) }
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
                animatePress(card, 1f, 320, .72f, 9f)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                moved = moved || abs(event.x - downX) + abs(event.y - downY) > dp(8)
                pointerX = (event.x / card.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                pointerY = (event.y / card.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                applyPress(card)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                animatePress(card, 0f, 420, .48f, 8f)
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
        val prefix = if (exit) "notification_heads_up_pop_exit" else "notification_heads_up_pop"
        fun duration(key: String, fallback: Float) = number(key, fallback).coerceIn(200f, 1500f).toLong()
        fun delay(key: String, fallback: Float): Long {
            val value = number(key, fallback).coerceIn(0f, 600f).toLong()
            return if (exit) value else value.coerceAtLeast(32L)
        }
        val scaleDuration = duration("${prefix}_duration", 500f)
        val scaleDelay = delay("${prefix}_delay", 150f)
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
        volumeWidth.snap(52f)
        // The source parameter is the initial scale, not an amount subtracted from 1.
        // popTransform maps progress 0..1 to the shared scale/negative-translation curve.
        volumePop.snap(0f)
        val (k, d) = VolumeMotionModel.popCoefficients(number("volume_pop_duration", 1000f), number("volume_pop_damping", 1f))
        volumePop.target(1f, k, d)
        val (nk, nd) = VolumeMotionModel.nudgeCoefficients(number("volume_nudge_response", .8f), number("volume_nudge_damping", .9f))
        volumeNudge.snap(0f)
        volumeNudge.target(-number("volume_nudge", 6.5f), nk, nd)
        volumeReturnAt = SystemClock.uptimeMillis() + 100L
        pressVolume(false, false)
    }

    private fun dragVolume(dx: Float, dy: Float) {
        val card = surfaces.firstOrNull() ?: return
        // Match the stable owner column, not the narrowing visual slider inside it.
        val target = VolumeMotionModel.dragTransform(dx, dy, dp(52f), card.height.toFloat())
        volumeDragX.snap(target.x); volumeDragY.snap(target.y)
        volumeDragScaleX.snap(target.scaleX); volumeDragScaleY.snap(target.scaleY)
        scheduleVolume()
    }

    private fun pressVolume(pressed: Boolean, captureWidth: Boolean = true) {
        if (!volumeScheduled && captureWidth) volumeWidth.snap(surfaces.firstOrNull()?.width?.div(resources.displayMetrics.density) ?: number("volume_thin_width", 9f))
        val (k, d) = VolumeMotionModel.widthCoefficients(number("volume_motion_duration", 600f))
        volumeWidth.target(if (pressed) 52f else number("volume_thin_width", 9f), k, d)
        scheduleVolume()
    }

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
        surfaces.forEach { it.rotationX = 0f; it.rotationY = 0f; it.translationZ = 0f; it.translationX = 0f; it.translationY = 0f; it.setRenderEffect(null) }
    }

    private fun updateCaption() {
        val native = surfaces.isNotEmpty() && surfaces.all { it.nativeApplied }
        val materialLabel = if (native) "原生材质接口" else "材质示意"
        val action = when (scene) {
            Scene.NOTIFICATION -> if (focusGroup.contains("显隐")) "轻点查看显隐" else "轻点展开 · 按住拖动"
            Scene.HEADS_UP -> "轻点重播弹出"
            Scene.MEDIA -> "按住拖动"
            Scene.VOLUME -> "按住拖动 · 松开回弹"
            else -> "实时调参"
        }
        caption.text = "$materialLabel · 本地交互示意\n$action，不启用系统功能"
    }

    override fun onDetachedFromWindow() { cancelAnimations(); super.onDetachedFromWindow() }
    fun release() { released = true; imageGeneration++; cancelAnimations(); wallpaper.setImage(null) }
    private fun number(key: String, default: Float) = (preferences.all[key] as? Number)?.toFloat() ?: default
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun dp(value: Float) = value * resources.displayMetrics.density

    private inner class SampleSurface(context: Context) : FrameLayout(context) {
        var nativeApplied = false
        var radiusKey = "notification_radius"
        private var radius = dp(24).toFloat()
        init {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, radius) }
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateMaterial() }
        }
        fun updateMaterial() {
            radius = dp(number(radiusKey, if (scene == Scene.VOLUME) 40f else 26f)).coerceAtLeast(0f)
            invalidateOutline()
            nativeApplied = material.apply(this, preferences)
            val opacity = number("glass_background_opacity", 100f) / 100f
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                if (night) Color.argb(((if (nativeApplied) 20 else 166) * opacity).roundToInt().coerceIn(0, 240), 26, 37, 51)
                else Color.argb(((if (nativeApplied) 22 else 185) * opacity).roundToInt().coerceIn(0, 240), 255, 255, 255),
                if (night) 0x442b3547 else 0x66ffffff,
            )).apply { cornerRadius = radius; setStroke(dp(1), if (night) 0x28ffffff else 0x80ffffff.toInt()) }
        }
    }

    private class WallpaperView(context: Context) : View(context) {
        var night = false
        var style = 0
        private var image: Bitmap? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val matrix = Matrix()
        fun setImage(value: Bitmap?) { image = value; invalidate() }
        override fun onDraw(canvas: Canvas) {
            val bitmap = image
            if (bitmap != null && !bitmap.isRecycled) {
                val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                matrix.reset(); matrix.setScale(scale, scale)
                matrix.postTranslate((width - bitmap.width * scale) * .5f, (height - bitmap.height * scale) * .5f)
                paint.shader = null
                canvas.drawBitmap(bitmap, matrix, paint)
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
        }
    }
}
