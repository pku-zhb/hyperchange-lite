// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import java.lang.reflect.Method
import java.util.WeakHashMap

internal class NotificationHeadsUp {
    private val sessions = WeakHashMap<View, Session>()
    private lateinit var prefs: SharedPreferences
    private lateinit var runtime: HookRuntime
    @Volatile private var configurationRevision = 0L
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun install(runtime: HookRuntime) {
        this.runtime = runtime
        prefs = runtime.preferences
        val controller = runtime.type("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController") ?: return
        controller.declaredMethods.filter { it.name == "generateHeadsUpAnimation" && it.parameterCount == 2 && it.parameterTypes[1] == Boolean::class.javaPrimitiveType }
            .forEach { method -> runtime.hook(method, "notification-motion:heads-up") { chain ->
                val result = chain.proceed()
                runCatching {
                    val row = chain.getArg(0)?.read("row") as? View
                    val layout = chain.thisObject?.read("mView")
                    val expanded = layout?.call("getIsExpanded") == true || layout?.read("mWillExpand") == true
                    if (row != null) {
                        if (expanded || !prefs.flag("notification_heads_up_pop_enabled") || !NotificationScope.animationAllows(row)) sessions.remove(row)?.finish(true)
                        else runCatching { start(row, chain.getArg(1) == true) }.onFailure {
                            sessions.remove(row)?.finish(true)
                            throw it
                        }
                    }
                }.onFailure { runtime.log("Heads-up overlay unavailable; original animation retained", it) }
                result
            } }
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "notification_heads_up_pop_enabled") {
                configurationRevision++
                Handler(Looper.getMainLooper()).post {
                    sessions.values.toList().filter { it.stale() }.forEach { it.finish(true) }
                }
            }
        }.also(prefs::registerOnSharedPreferenceChangeListener)
        NotificationScope.addListener { sessions.values.toList().filter { it.stale() }.forEach { it.finish(true) } }
    }

    private fun start(view: View, entering: Boolean) {
        sessions.remove(view)?.finish(true)
        val channel = Channel(view) // Resolve every required OEM member before writing anything.
        val session = Session(view, channel)
        sessions[view] = session
        view.addOnAttachStateChangeListener(session)
        val prefix = if (entering) "notification_heads_up_pop" else "notification_heads_up_pop_exit"
        val scale = prefs.number("${prefix}_scale", .6f, .5f, 1f)
        val flip = prefs.number("notification_heads_up_pop_flip", 20f, -80f, 80f)
        val blur = prefs.number("notification_heads_up_pop_blur", 50f, 0f, 150f)
        val initial = if (entering) 1f else 0f
        channel.scale(1f - (1f - scale) * initial)
        channel.rotation(flip * initial)
        session.blur.apply(blur * initial)
        fun animation(durationKey: String, delayKey: String, defaultDuration: Float, defaultDelay: Float,
                      phase: String, update: (Float) -> Unit): ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = prefs.number(durationKey, defaultDuration, 200f, 1500f).toLong()
            startDelay = NotificationMotion.headsUpDelay(
                prefs.number(delayKey, defaultDelay, 0f, 600f).toLong(), entering, phase == "scale",
            )
            setInterpolator { t ->
                val spring = prefs.flag("${if (phase == "scale") prefix else "notification_heads_up_pop"}_spring")
                if (spring) NotificationMotion.spring(t,
                    prefs.number(if (phase == "scale") "${prefix}_damping" else "notification_heads_up_pop_${phase}_damping", if (phase == "flip") .85f else .5f, .2f, 1.2f),
                    if (phase == "scale") prefs.number("${prefix}_frequency", 8f, 2f, 24f) else 8f)
                else {
                    val curve = if (phase == "scale") prefix else "notification_heads_up_pop"
                    NotificationMotion.bezier(t, prefs.number("${curve}_curve_x1", .22f, 0f, 1f),
                        prefs.number("${curve}_curve_y1", 1f, -.5f, 2f), prefs.number("${curve}_curve_x2", .36f, 0f, 1f),
                        prefs.number("${curve}_curve_y2", 1f, -.5f, 2f))
                }
            }
            addUpdateListener {
                if (!session.valid()) { session.finish(true); return@addUpdateListener }
                val fraction = it.animatedValue as Float
                runCatching { update(if (entering) 1f - fraction else fraction) }
                    .onFailure { session.finish(true) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { session.ended() }
            })
        }
        session.animators += animation("${prefix}_duration", "${prefix}_delay", 500f, 150f, "scale") { channel.scale(1f - (1f - scale) * it) }
        session.animators += animation("notification_heads_up_pop_flip_duration", "notification_heads_up_pop_flip_delay", 500f, 100f, "flip") { channel.rotation(flip * it) }
        session.animators += animation("notification_heads_up_pop_blur_duration", "notification_heads_up_pop_blur_delay", 1000f, 0f, "blur") { session.blur.apply(blur * it) }
        session.animators.forEach(ValueAnimator::start)
    }

    private class Channel(private val view: View) {
        private val state = requireNotNull(view.call("getViewState"))
        private val injector = requireNotNull(state.read("mInjector"))
        private val x: Method = requireNotNull(injector.javaClass.methods.firstOrNull { it.name == "setFolmeScaleXForType" && it.parameterCount == 3 })
        private val y: Method = requireNotNull(injector.javaClass.methods.firstOrNull { it.name == "setFolmeScaleYForType" && it.parameterCount == 3 })
        private val values = requireNotNull(injector.javaClass.fieldOrNull("folmeStateMap") ?: injector.javaClass.fieldOrNull("folmeValueMap"))
        private val updateOwner = requireNotNull(view.call("getInjector"))
        private val update = requireNotNull(updateOwner.javaClass.methodOrNull("scheduleUpdateProperties"))
        private val listener = requireNotNull(injector.read("folmeStateListener"))
        private val complete = requireNotNull(listener.javaClass.methodOrNull("onComplete", Int::class.javaPrimitiveType!!))
        private fun data(): Any? = (values.get(injector) as? SparseArray<*>)?.get(11071)
        private fun read(name: String, fallback: Float): Float {
            val data = data() ?: return fallback
            return requireNotNull(data.javaClass.fieldOrNull(name)).getFloat(data)
        }
        private val ownedX = OwnedValue(read("scaleX", 1f))
        private val ownedY = OwnedValue(read("scaleY", 1f))
        private val ownedRotation = OwnedValue(read("rotationX", 0f))
        fun scale(value: Float) {
            x.invoke(injector, view, value, 11071); ownedX.record(value)
            y.invoke(injector, view, value, 11071); ownedY.record(value)
        }
        fun rotation(value: Float) {
            val data = data() ?: return
            requireNotNull(data.javaClass.fieldOrNull("rotationX")).setFloat(data, value)
            ownedRotation.record(value)
            update.invoke(updateOwner)
        }
        fun finish(notifyNative: Boolean) {
            val currentX = read("scaleX", 1f)
            val currentY = read("scaleY", 1f)
            val currentRotation = read("rotationX", 0f)
            val stillOwned = ownedX.owns(currentX) && ownedY.owns(currentY) && ownedRotation.owns(currentRotation)
            ownedX.release(currentX) { x.invoke(injector, view, it, 11071) }
            ownedY.release(currentY) { y.invoke(injector, view, it, 11071) }
            ownedRotation.release(currentRotation) { value ->
                data()?.let { requireNotNull(it.javaClass.fieldOrNull("rotationX")).setFloat(it, value) }
                update.invoke(updateOwner)
            }
            // Cancelling an overlay is not completion of the new native heads-up event.
            if (notifyNative && stillOwned) complete.invoke(listener, 11071)
        }
    }

    private inner class Session(private val view: View, private val channel: Channel) : View.OnAttachStateChangeListener {
        val animators = mutableListOf<ValueAnimator>()
        val blur = SurfaceBlur(view, minUpdateIntervalMs = 16L) { runtime.log(it) }
        private val token = NotificationScope.animationToken(view)
        private val revision = configurationRevision
        private var finished = false
        fun stale() = revision != configurationRevision || !NotificationScope.isAnimationCurrent(view, token)
        fun valid() = !finished && !stale() && prefs.flag("notification_heads_up_pop_enabled")
        private var outstanding = 3
        private var cancelled = false
        private val completion = Completion {
            sessions.remove(view, this)
            view.removeOnAttachStateChangeListener(this)
            runCatching { channel.finish(!cancelled) }
            runCatching { blur.restore() }
        }
        fun ended() { if (--outstanding == 0) finish(false) }
        fun finish(cancel: Boolean) {
            if (finished) return
            finished = true
            if (cancel) { cancelled = true; animators.forEach { it.removeAllUpdateListeners(); it.removeAllListeners(); it.cancel() } }
            completion.finish()
        }
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { finish(true) }
    }
}
