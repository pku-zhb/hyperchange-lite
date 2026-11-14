// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.glass.GlassSurfaces
import com.zhuhuibin.hyperchange.lite.glass.MaterialDiagnostics
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import com.zhuhuibin.hyperchange.lite.scope.ShadeScope
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** One native banner at a time. No framebuffer copy, permanent sampler or per-frame text walk. */
internal class BannerReadability {
    private lateinit var runtime: HookRuntime
    private val main = Handler(Looper.getMainLooper())
    private val writing = ThreadLocal<Boolean>()
    private val observing = ThreadLocal<MutableSet<Pair<TextView, String>>>()
    private val leases = Collections.synchronizedMap(WeakHashMap<TextView, BannerTextLease>())
    private var candidate = WeakReference<View>(null)
    @Volatile private var active: Session? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val worker by lazy { Executors.newSingleThreadExecutor { command ->
        Thread(command, "hc-lite-banner-sample").apply { isDaemon = true }
    } }
    private var events = 0L
    private var bursts = 0L
    private var samples = 0L
    private var restorations = 0L
    private var lastLuma: Float? = null
    private var status = "off"
    private var samplerSupported: Boolean? = null
    private var failureLogged = false
    private var textObserversInstalled = false

    private fun enabled() = runtime.preferences.getBoolean(BannerContrastPolicy.KEY, false)
    private fun own(action: () -> Unit) {
        val before = writing.get()
        writing.set(true)
        try { action() } finally { if (before == null) writing.remove() else writing.set(before) }
    }

    fun install(runtime: HookRuntime) {
        this.runtime = runtime
        val row = runtime.type("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") ?: return
        row.declaredMethods.filter { it.name == "setHeadsUp" && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)) }
            .forEach { method -> runtime.hook(method, "banner-readable:heads-up") { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as? View
                if (view != null) {
                    events++
                    if (chain.getArg(0) == true) { candidate = WeakReference(view); reconcile() }
                    else {
                        if (candidate.get() === view) candidate.clear()
                        if (active?.row === view) { active?.close(); active = null }
                    }
                }
                result
            } }
        row.declaredMethods.filter { it.name in setOf("onAttachedToWindow", "onDetachedFromWindow") && it.parameterCount == 0 }
            .forEach { method -> runtime.hook(method, "banner-readable:attachment:${method.name}") { chain ->
                val result = chain.proceed()
                if (chain.thisObject === candidate.get()) main.post { reconcile() }
                result
            } }
        row.declaredMethods.filter { it.name == "onNotificationUpdated" }.forEachIndexed { i, method ->
            runtime.hook(method, "banner-readable:updated:$i") { chain ->
                val session = active?.takeIf { it.row === chain.thisObject }
                session?.releaseText()
                val result = chain.proceed()
                session?.request()
                result
            }
        }
        val content = runtime.type("com.android.systemui.statusbar.notification.row.NotificationContentView")
        content?.declaredMethods?.filter { it.name == "setHeadsUpChild" && it.parameterCount == 1 }?.forEach { method ->
            runtime.hook(method, "banner-readable:content") { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as? View
                val session = active
                if (view != null && session != null && generateSequence(view) { it.parent as? View }
                        .take(12).any { it === session.row }) session.request()
                result
            }
        }
        // Actual App/window transitions are explicit background events. Text writes and
        // ordinary root redraws never create a permanent sampling heartbeat.
        runtime.type("com.android.systemui.statusbar.CommandQueue")?.declaredMethods
            ?.filter { it.name == "appTransitionFinished" }?.forEachIndexed { i, method ->
                runtime.hook(method, "banner-readable:backdrop:$i") { chain ->
                    val result = chain.proceed(); if (enabled()) { events++; active?.request() }; result
                }
            }
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == BannerContrastPolicy.KEY) main.post { reconcile() }
        }.also(runtime.preferences::registerOnSharedPreferenceChangeListener)
        NotificationScope.addListener { reconcile() }
        ShadeScope.addListener { reconcile() }
        MaterialDiagnostics.install(runtime, "hc-lite-banner") { diagnostics() }
    }

    private fun installTextObservers() {
        if (textObserversInstalled) return
        textObserversInstalled = true
        TextView::class.java.declaredMethods.filter { it.name in setOf("setTextColor", "setShadowLayer") ||
            it.name == "setText" && it.parameterCount > 0 && it.parameterTypes[0] == CharSequence::class.java }
            .forEachIndexed { i, method -> runtime.hook(method, "banner-readable:text:$i") { chain ->
                val view = chain.thisObject as? TextView
                val lease = if (view != null && writing.get() != true && leases.isNotEmpty()) leases[view] else null
                if (lease == null || view == null) return@hook chain.proceed()
                val key = view to method.name
                val calls = observing.get() ?: mutableSetOf<Pair<TextView, String>>().also(observing::set)
                if (!calls.add(key)) return@hook chain.proceed()
                try {
                    val result = chain.proceed()
                    fun observe() {
                        if (leases[view] !== lease) return
                        when (method.name) {
                            "setTextColor" -> lease.nativeColors()
                            "setShadowLayer" -> lease.nativeShadow()
                            "setText" -> { lease.nativeText(); active?.request() }
                        }
                    }
                    if (Looper.myLooper() == Looper.getMainLooper()) observe() else main.post { observe() }
                    result
                } finally {
                    calls.remove(key)
                    if (calls.isEmpty()) observing.remove()
                }
            } }
    }

    private fun eligible(row: View): Boolean = enabled() && candidate.get() === row && row.isAttachedToWindow && row.isShown &&
        NotificationScope.confirmedUnlocked() && GlassSurfaces.headsUp(row) && !ShadeScope.allows(row)

    private fun reconcile() {
        // Native lifecycle callbacks and preference listeners may come from a binder thread.
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { reconcile() }; return }
        val before = active
        if (before != null && !eligible(before.row)) { before.close(); active = null }
        if (!enabled()) { status = "off"; return }
        val row = candidate.get()
        if (row != null && eligible(row)) {
            if (active?.row !== row) {
                active?.close()
                active = Session(row).also { it.request() }
            }
        } else if (active == null) status = "no-visible-banner"
    }

    private inner class Session(val row: View) : View.OnAttachStateChangeListener, View.OnLayoutChangeListener {
        private val budget = BannerSampleWindow()
        private val contrast = BannerContrastPolicy()
        private val texts = ArrayList<BannerTextLease>()
        private var sampler: NativeBannerSampler? = null
        private var samplerFailed = false
        private var closed = false
        private var ticket = 0L
        private var posted = false
        private var scannedAt = Long.MIN_VALUE
        @Volatile private var primaryCount = 0
        @Volatile private var secondaryCount = 0
        private val begin = Runnable { posted = false; runCatching { burst() }.onFailure(::samplingFailed) }
        private val finish = Runnable { runCatching { finishSample() }.onFailure(::samplingFailed) }
        init {
            installTextObservers()
            row.addOnAttachStateChangeListener(this); row.addOnLayoutChangeListener(this)
        }

        fun request() {
            if (closed || posted) return
            posted = true
            val now = SystemClock.uptimeMillis()
            val scanDelay = if (scannedAt == Long.MIN_VALUE) 0L
                else (BannerSampleWindow.INTERVAL_MS - (now - scannedAt)).coerceAtLeast(0L)
            main.postDelayed(begin, maxOf(80L, scanDelay, budget.delay(now)))
        }
        private fun content(): View? {
            val layout = row.call("getPrivateLayout") ?: row.read("mPrivateLayout")
            return layout?.call("getHeadsUpChild") as? View ?: layout?.read("mHeadsUpChild") as? View
        }
        private fun glass(): Boolean {
            val injector = row.call("getInjector")
            val bg = injector?.call("getBackgroundNormal") as? View ?: row.read("mBackgroundNormal") as? View
            return bg?.call("getMiViewBlurMode") == 1
        }
        private fun bindText(content: View) {
            val found = ArrayList<Pair<TextView, Boolean>>()
            var visited = 0
            fun walk(view: View, depth: Int) {
                if (++visited > 128 || depth > 10 || found.size >= 16 || !view.isShown) return
                if (view is TextView) {
                    val role = runCatching { BannerContrastPolicy.role(view.resources.getResourcePackageName(view.id),
                        view.resources.getResourceEntryName(view.id)) }.getOrNull()
                    if (role != null && view.text.isNotEmpty()) found += view to role
                } else if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), depth + 1)
            }
            walk(content, 0)
            for (old in texts.toList()) if (found.none { it.first === old.view }) {
                old.restore(); leases.remove(old.view); texts.remove(old); restorations++
            }
            for ((view, primary) in found) if (texts.none { it.view === view }) {
                BannerTextLease(view, primary, ::own).also { texts += it; leases[view] = it }
            }
            primaryCount = texts.count { it.primary }; secondaryCount = texts.size - primaryCount
            applyTone(if (glass()) contrast.tone else null)
        }
        private fun region(content: View): Rect? {
            val visible = Rect()
            if (!content.getGlobalVisibleRect(visible) || visible.isEmpty) return null
            val preferred = texts.firstOrNull { !it.primary }?.view ?: texts.firstOrNull()?.view ?: return null
            val text = Rect()
            if (!preferred.getGlobalVisibleRect(text) || !text.intersect(visible)) return null
            // getGlobalVisibleRect uses the ViewRoot/window coordinate domain. The native
            // sampler expects screen pixels, including a nonzero window origin.
            val screen = IntArray(2); val window = IntArray(2)
            row.rootView.getLocationOnScreen(screen); row.rootView.getLocationInWindow(window)
            text.offset(screen[0] - window[0], screen[1] - window[1])
            val width = minOf(192, text.width()); val height = minOf(48, text.height())
            return Rect(text.centerX() - width / 2, text.centerY() - height / 2,
                text.centerX() - width / 2 + width, text.centerY() - height / 2 + height)
        }
        private fun burst() {
            if (closed || !eligible(row)) { close(); if (active === this) active = null; return }
            scannedAt = SystemClock.uptimeMillis()
            val content = content() ?: run { releaseText(); status = "no-native-banner-content"; return }
            bindText(content)
            if (!glass() || texts.isEmpty()) { status = "conservative-shadow-opaque-or-unknown"; return }
            val region = region(content) ?: return
            if (samplerFailed) return
            try {
                val sampleTicket = budget.start(SystemClock.uptimeMillis())
                ticket = sampleTicket
                sampler?.close()
                sampler = NativeBannerSampler(row, runtime.loader, worker, { luma ->
                    if (!closed && budget.collect(sampleTicket, luma, SystemClock.uptimeMillis())) samples++
                }, { error -> main.post {
                    if (ticket == sampleTicket && !closed) samplingFailed(error)
                } })
                samplerSupported = true
                if (sampler?.begin(region) != true) {
                    budget.cancel(); sampler?.close(); sampler = null
                    status = "conservative-shadow-no-valid-surface"; return
                }
                bursts++
                status = "sampling-underlay"
                main.postDelayed(finish, BannerSampleWindow.BURST_MS)
            } catch (error: Throwable) { samplingFailed(error) }
        }
        private fun finishSample() {
            if (closed) return
            if (!eligible(row)) { close(); if (active === this) active = null; return }
            sampler?.readLuma()?.let { if (budget.collect(ticket, it, SystemClock.uptimeMillis())) samples++ }
            sampler?.close(); sampler = null
            val luma = budget.finish(ticket)
            if (luma != null && glass()) {
                lastLuma = luma
                applyTone(contrast.select(luma, SystemClock.uptimeMillis()))
                status = "sampled-underlay"
            } else status = "conservative-shadow-no-sample"
        }
        private fun applyTone(tone: BannerContrastPolicy.Tone?) { texts.forEach { it.apply(tone) } }
        private fun samplingFailed(error: Throwable) {
            if (Looper.myLooper() != Looper.getMainLooper()) { main.post { samplingFailed(error) }; return }
            if (closed) return
            main.removeCallbacks(finish)
            budget.cancel()
            sampler?.close(); sampler = null; samplerFailed = true; samplerSupported = false
            applyTone(null)
            status = "conservative-shadow-unavailable"
            if (!failureLogged) { failureLogged = true; runtime.log("Banner underlay sampling unavailable; native ink retained", error) }
        }
        fun releaseText() {
            for (lease in texts) {
                runCatching { lease.restore() }.onFailure { runtime.log("Banner native ink restoration deferred to its next native bind", it) }
                leases.remove(lease.view); restorations++
            }
            texts.clear()
            primaryCount = 0; secondaryCount = 0
        }
        fun close() {
            if (closed) return
            closed = true
            main.removeCallbacks(begin); main.removeCallbacks(finish); posted = false
            budget.cancel(); sampler?.close(); sampler = null
            row.removeOnAttachStateChangeListener(this); row.removeOnLayoutChangeListener(this)
            releaseText()
            status = "native-restored"
        }
        fun details() = "samplerActive=${sampler != null} tone=${contrast.tone ?: "native"} " +
            "primary=$primaryCount secondary=$secondaryCount"
        override fun onViewAttachedToWindow(v: View) = request()
        override fun onViewDetachedFromWindow(v: View) { close(); if (active === this) active = null }
        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) { request() }
    }

    private fun diagnostics() = "hc-lite banner readability\n" +
        "enabled=${enabled()} active=${active != null} samplerSupported=$samplerSupported status=$status " +
        "events=$events bursts=$bursts samples=$samples lastLuma=${lastLuma ?: "none"} restorations=$restorations " +
        (active?.details() ?: "samplerActive=false tone=native primary=0 secondary=0")
}
