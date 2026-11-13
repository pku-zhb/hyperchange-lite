// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Observes dispatch without replacing touch listeners or consuming any gesture. */
internal class NotificationTilt {
    private val sessions = WeakHashMap<View, Session>()
    private val ownWrite = ThreadLocal<Boolean>()
    private lateinit var runtime: HookRuntime
    private lateinit var prefs: SharedPreferences
    @Volatile private var configurationRevision = 0L
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun install(runtime: HookRuntime) {
        this.runtime = runtime
        prefs = runtime.preferences
        val row = runtime.type("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow")
        val media = runtime.type("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView")
        val methods = listOfNotNull(row, media).mapNotNull {
            it.methodOrNull("dispatchTouchEvent", MotionEvent::class.java)
        }.distinct()
        for ((index, method) in methods.withIndex()) runtime.hook(method, "notification-tilt:dispatch:$index") { chain ->
            val result = chain.proceed()
            val view = chain.thisObject as? View
            val event = chain.getArg(0) as? MotionEvent
            if (view != null && event != null) {
                val isMedia = media?.isInstance(view) == true
                if (isMedia || row?.isInstance(view) == true) {
                    runCatching {
                        if (eligible(view, isMedia)) {
                            sessions.getOrPut(view) { Session(view, isMedia) }.touch(event)
                        } else sessions.remove(view)?.close()
                    }.onFailure { runtime.log("Notification press feedback failed", it) }
                }
            }
            result
        }
        val stack = runtime.type("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout")
        stack?.methodOrNull("onInterceptTouchEvent", MotionEvent::class.java)?.let { method ->
            runtime.hook(method, "notification-tilt:interception") { chain ->
                val result = chain.proceed()
                if (result == true) sessions.values.toList().forEach { it.release(false) }
                result
            }
        }
        row?.methodOrNull("setChildrenExpanded", Boolean::class.javaPrimitiveType!!)?.let { method ->
            runtime.hook(method, "notification-tilt:group-reset") { chain ->
                (chain.thisObject as? View)?.let { root ->
                    sessions.keys.toList().filter { it === root || descendantOf(it, root) }
                        .forEach { sessions.remove(it)?.close() }
                }
                chain.proceed()
            }
        }
        val hooked = mutableSetOf<Method>()
        for (type in listOfNotNull(row, media)) for (name in arrayOf("setExtRotationX", "setExtRotationY", "setExtScaleX", "setExtScaleY")) {
            val method = type.methodOrNull(name, Float::class.javaPrimitiveType!!) ?: continue
            if (!hooked.add(method)) continue
            runtime.hook(method, "notification-tilt:compose:$name:${hooked.size}") { chain ->
                if (ownWrite.get() == true) return@hook chain.proceed()
                val view = chain.thisObject as? View
                var session = sessions[view]
                if (session != null && !session.valid()) { sessions.remove(view)?.close(); session = null }
                if (session == null || !session.canCompose(name) || session.progress == 0f) chain.proceed()
                else {
                    val value = chain.getArg(0) as Float
                    session.nativeValues[name] = value
                    session.rebase(name, value)
                    val frame = session.frame()
                    val composed = when (name) {
                        "setExtRotationX" -> value + frame.rotationX
                        "setExtRotationY" -> value + frame.rotationY
                        else -> value * frame.scale
                    }
                    chain.proceed(arrayOf(composed)).also { session.record(name, composed) }
                }
            }
        }
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("notification_press_tilt_")) {
                configurationRevision++
                Handler(Looper.getMainLooper()).post {
                    sessions.values.toList().filter { it.stale() }.forEach { it.close() }
                }
            }
        }.also(prefs::registerOnSharedPreferenceChangeListener)
        NotificationScope.addListener {
            sessions.values.toList().filter { it.stale() }.forEach { it.close() }
        }
        runtime.log("Notification tilt dispatch targets=${methods.size}; native gestures retained")
    }

    private fun eligible(view: View, media: Boolean): Boolean =
        NotificationScope.animationAllows(view) && prefs.flag("notification_press_tilt_enabled") &&
            prefs.flag(if (media) "notification_press_tilt_media_enabled" else "notification_press_tilt_row_enabled", true)

    private inner class Session(view: View, private val media: Boolean) : View.OnAttachStateChangeListener {
        private val target = WeakReference(view)
        private val token = NotificationScope.animationToken(view)
        private val revision = configurationRevision
        private var closed = false
        private val ownership = mutableMapOf<String, OwnedValue<Float>>()
        private var animator: ValueAnimator? = null
        var touching = false
            private set
        private var destination = 0f
        private var downX = 0f
        private var downY = 0f
        private var x = .5f
        private var y = .5f
        var progress = 0f
        val nativeValues = mutableMapOf<String, Float>()
        private val methods = arrayOf("setExtRotationX", "setExtRotationY", "setExtScaleX", "setExtScaleY")
            .associateWith { name ->
                view.javaClass.methodOrNull(name, Float::class.javaPrimitiveType!!)?.takeIf {
                    view.javaClass.methodOrNull(name.replaceFirst("set", "get")) != null
                }
            }
        private var originalX = view.rotationX
        private var originalY = view.rotationY
        private var originalScaleX = view.scaleX
        private var originalScaleY = view.scaleY
        private var originalZ = view.translationZ
        private var camera = view.cameraDistance

        init { view.addOnAttachStateChangeListener(this) }

        fun frame(): NotificationMotion.TiltFrame = NotificationMotion.tilt(x, y, progress,
            prefs.number("notification_press_tilt_max_deg", 4f, 2f, 20f),
            prefs.number("notification_press_tilt_scale", .96f, .85f, 1f))

        fun touch(event: MotionEvent) {
            val view = target.get() ?: return
            if (!valid()) { close(); return }
            if (event.pointerCount > 1) { release(false); return }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (hasPressedChild(view)) return
                    if (progress == 0f) capture(view)
                    touching = true
                    downX = event.rawX; downY = event.rawY
                    updatePoint(view, event)
                    writeBase("camera", view.cameraDistance, 1280f * view.resources.displayMetrics.density) { view.cameraDistance = it }
                    animateTo(1f, 320, .72f, 9f)
                }
                MotionEvent.ACTION_MOVE -> if (touching) {
                    if (NotificationMotion.releasesTilt(event.rawX - downX, event.rawY - downY, media)) release(false)
                    else { updatePoint(view, event); apply() }
                }
                MotionEvent.ACTION_UP -> if (touching) release(event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout())
                MotionEvent.ACTION_CANCEL -> release(false)
            }
        }

        fun stale(): Boolean = revision != configurationRevision || target.get()?.let { !NotificationScope.isAnimationCurrent(it, token) } != false
        fun valid(): Boolean = !closed && target.get()?.let { eligible(it, media) && !closed && !stale() } == true

        fun rebase(name: String, value: Float) { ownership[name]?.rebase(value) }
        fun record(name: String, value: Float) { ownership[name]?.record(value) }
        fun canCompose(name: String): Boolean = methods[name] != null

        private fun writeBase(name: String, original: Float, value: Float, setter: (Float) -> Unit) {
            val owned = ownership.getOrPut(name) { OwnedValue(original) }
            setter(value)
            owned.record(value)
        }

        private fun capture(view: View) {
            ownership.clear()
            originalX = view.rotationX; originalY = view.rotationY
            originalScaleX = view.scaleX; originalScaleY = view.scaleY
            originalZ = view.translationZ; camera = view.cameraDistance
            for (name in methods.keys) {
                nativeValues[name] = (view.call(name.replaceFirst("set", "get")) as? Number)?.toFloat()
                    ?: if (name.contains("Scale")) 1f else 0f
                ownership[name] = OwnedValue(nativeValues.getValue(name))
            }
        }

        private fun updatePoint(view: View, event: MotionEvent) {
            val position = IntArray(2)
            view.getLocationOnScreen(position)
            x = (event.rawX - position[0]) / view.width.coerceAtLeast(1)
            y = (event.rawY - position[1]) / view.height.coerceAtLeast(1)
        }

        fun release(tap: Boolean) {
            if (!valid()) { close(); return }
            touching = false
            if (destination == 0f) return
            animateTo(0f, if (tap) 480 else 420, if (tap) .34f else .48f, if (tap) 7.5f else 8f)
        }

        private fun animateTo(value: Float, duration: Long, damping: Float, frequency: Float) {
            destination = value
            animator?.removeAllUpdateListeners(); animator?.cancel()
            animator = ValueAnimator.ofFloat(progress, value).apply {
                this.duration = duration
                setInterpolator { NotificationMotion.spring(it, damping, frequency) }
                addUpdateListener { progress = it.animatedValue as Float; apply() }
                start()
            }
        }

        private fun apply() {
            val view = target.get() ?: return
            if (!valid()) { close(); return }
            val frame = frame()
            ownWrite.set(true)
            try {
                fun write(name: String, value: Float, fallback: () -> Unit) {
                    val setter = methods[name]
                    if (setter != null) { setter.invoke(view, value); record(name, value) } else fallback()
                }
                write("setExtRotationX", nativeValues.getOrDefault("setExtRotationX", 0f) + frame.rotationX) { writeBase("x", originalX, originalX + frame.rotationX) { view.rotationX = it } }
                write("setExtRotationY", nativeValues.getOrDefault("setExtRotationY", 0f) + frame.rotationY) { writeBase("y", originalY, originalY + frame.rotationY) { view.rotationY = it } }
                write("setExtScaleX", nativeValues.getOrDefault("setExtScaleX", 1f) * frame.scale) { writeBase("sx", originalScaleX, originalScaleX * frame.scale) { view.scaleX = it } }
                write("setExtScaleY", nativeValues.getOrDefault("setExtScaleY", 1f) * frame.scale) { writeBase("sy", originalScaleY, originalScaleY * frame.scale) { view.scaleY = it } }
                writeBase("z", originalZ, originalZ + frame.zDp * view.resources.displayMetrics.density) { view.translationZ = it }
                if (progress == 0f) ownership["camera"]?.release(view.cameraDistance) { view.cameraDistance = it }
            } finally { ownWrite.remove() }
        }

        fun close() {
            if (closed) return
            closed = true
            animator?.removeAllUpdateListeners(); animator?.cancel(); animator = null
            touching = false; progress = 0f; destination = 0f
            val view = target.get() ?: return
            sessions.remove(view, this)
            ownWrite.set(true)
            try {
                for ((name, setter) in methods) if (setter != null) runCatching {
                    val current = (view.call(name.replaceFirst("set", "get")) as? Number)?.toFloat()
                    if (current != null) ownership[name]?.release(current) { setter.invoke(view, it) }
                }
                ownership["x"]?.release(view.rotationX) { view.rotationX = it }
                ownership["y"]?.release(view.rotationY) { view.rotationY = it }
                ownership["sx"]?.release(view.scaleX) { view.scaleX = it }
                ownership["sy"]?.release(view.scaleY) { view.scaleY = it }
                ownership["z"]?.release(view.translationZ) { view.translationZ = it }
                ownership["camera"]?.release(view.cameraDistance) { view.cameraDistance = it }
            } finally { ownWrite.remove(); view.removeOnAttachStateChangeListener(this) }
        }
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { sessions.remove(v); close() }
    }

    private fun hasPressedChild(view: View): Boolean {
        if (view !is ViewGroup) return false
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (sessions[child]?.touching == true || hasPressedChild(child)) return true
        }
        return false
    }

    private fun descendantOf(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }
}
