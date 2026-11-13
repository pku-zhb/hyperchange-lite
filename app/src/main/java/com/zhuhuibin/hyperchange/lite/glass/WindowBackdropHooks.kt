// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.SurfaceControl
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.ShadeScope
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.WeakHashMap

/** Captured before a native write; never holds an owner, View or window alive. */
internal class BackdropWrite(view: View, owner: Any, root: Any, val channel: String,
                            val ticket: MaterialWriteClock.Ticket<View>, control: SurfaceControl? = null) {
    val view = WeakReference(view)
    val owner = WeakReference(owner)
    val root = WeakReference(root)
    val control = control?.let(::WeakReference)
}

/** Exact native background owners, independent of foreground card material and HUD visibility. */
internal class WindowBackdropHooks(private val runtime: HookRuntime,
                                  private val settings: () -> SoftGlassSettings,
                                  private val nativeBackground: (View) -> Unit) {
    private class Source(owner: Any, val order: Int, val view: (Any) -> View?,
                         val fresh: (Any, View, Set<String>, () -> Boolean) -> Unit) {
        val owner = WeakReference(owner)
    }
    private class Owned(val write: BackdropWrite, val lease: BackdropWriteLease<Any, View>,
                        val drawable: WeakReference<Drawable>?, val color: Int?)
    private class Release(val source: Source, val owner: Any, val view: View, val root: Any, val epoch: Long) {
        val claims = mutableMapOf<String, Owned>()
        val channels get() = claims.keys
    }
    private val sources = WeakHashMap<View, MutableMap<String, Source>>()
    private val owned = WeakHashMap<View, MutableMap<String, Owned>>()
    private val clocks = mutableMapOf<String, MaterialWriteClock<View>>()
    private val rootViews = WeakHashMap<Any, WeakReference<View>>()
    private val solidCall = ThreadLocal<Source>()
    private val rebinders = mutableListOf<() -> Unit>()
    private val calls = WindowBackdropCallGuard<View>({ ShadeScope.generation }) { it.isAttachedToWindow && ShadeScope.allows(it) }
    private val handler = Handler(Looper.getMainLooper())
    private val viewRoot = runCatching { View::class.java.getMethod("getViewRootImpl").apply { isAccessible = true } }.getOrNull()
    private val surface = runCatching { viewRoot?.returnType?.getMethod("getSurfaceControl")?.apply { isAccessible = true } }.getOrNull()
    private var solidRefresh: (() -> Unit)? = null
    private var refresh: Runnable? = null
    private var installed = false

    fun install() {
        if (installed) return
        installProvider()
        installBlendRefresh()
        installSolidRefresh()
        val task = runtime.type("com.miui.systemui.shade.blur.ShadeWindowBlurController\$updateBlurRatioRunnable\$1") ?: return
        val blur = runtime.type("com.android.systemui.statusbar.BlurUtils") ?: return
        val owner = runCatching { task.getDeclaredField("this\$0").apply { isAccessible = true } }.getOrNull() ?: return
        val lazyView = runCatching { owner.type.getDeclaredField("windowViewLazy").apply { isAccessible = true } }.getOrNull() ?: return
        val getView = runCatching { lazyView.type.getMethod("get").apply { isAccessible = true } }.getOrNull() ?: return
        val run = runCatching { task.getDeclaredMethod("run") }.getOrNull() ?: return
        val apply = blur.declaredMethods.singleOrNull { it.name == "applyBlur" && it.returnType == Void.TYPE &&
            it.parameterTypes.size == 2 && it.parameterTypes[0].name == "android.view.ViewRootImpl" &&
            it.parameterTypes[1] == Int::class.javaPrimitiveType } ?: return
        val rootMethod = viewRoot ?: return
        runtime.hook(run, "window-backdrop:native-task") { chain ->
            val runnable = chain.thisObject as? Runnable ?: return@hook chain.proceed()
            val read: (Any) -> View? = { getView.invoke(lazyView.get(owner.get(it))) as? View }
            val view = runCatching { read(runnable) }.getOrNull()
            val root = view?.let { runCatching { rootMethod.invoke(it) }.getOrNull() }
            if (view != null && root != null) {
                synchronized(rootViews) { rootViews[root] = WeakReference(view) }
                register(view, setOf("window-blur"), Source(runnable, 0, read, { nativeOwner, currentView, _, current ->
                    if (current() && validSurface(currentView)) (nativeOwner as Runnable).run()
                }))
            }
            calls.during(view?.takeIf { settings().enabled }, root) { chain.proceed() }
        }
        runtime.hook(apply, "window-backdrop:compositor-radius") { chain ->
            val root = chain.getArg(0)
            val view = synchronized(rootViews) { rootViews[root]?.get() }
            val write = view?.let { observeNativeWrite(it, "window-blur") }
            val state = settings()
            val input = chain.getArg(1) as? Int ?: return@hook chain.proceed()
            val output = if (state.enabled && write?.control?.get()?.isValid == true && calls.allows(root)) WindowBackdropPolicy.radius(input, true, state.backgroundBlur) else input
            val result = if (input == output) chain.proceed() else chain.proceedWith(chain.thisObject, arrayOf(root, output))
            if (view != null && write != null && input != output)
                runCatching { ownWrite(view, write, arrayOf(output)) }.onFailure { runtime.log("Could not retain compositor ownership", it) }
            result
        }
        installed = true
        runtime.log("Full-window compositor blur channel installed")
    }

    private fun installProvider() {
        val type = runtime.type("com.miui.systemui.shade.blur.ShadeBlendBlurController\$BlurProvider") ?: return
        val field = runCatching { type.getDeclaredField("view").apply { isAccessible = true } }.getOrNull() ?: return
        val enabled = runCatching { type.getDeclaredField("enabled").apply { isAccessible = true } }.getOrNull() ?: return
        val maxima = runCatching { listOf("maxRadius", "maxGlassSmallBlurRadius", "maxGlassBigBlurRadius")
            .map { type.getDeclaredField(it).apply { isAccessible = true } } }.getOrNull() ?: return
        val update = runCatching { type.getDeclaredMethod("updateMaxRadius", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true } }.getOrNull() ?: return
        val mode = runCatching { View::class.java.getMethod("setMiBackgroundBlurMode", Int::class.javaPrimitiveType) }.getOrNull() ?: return
        val channels = setOf("setMiBackgroundBlurMode", "setMiBackgroundBlurRadius", "setMiGlassBlurRadius")
        track(type, field, "provider", setOf("setBlurRatio", "applyGlassBlurBlurRatio", "setPassBlur", "updateMaxRadius"), channels, 1) { nativeOwner, view, changed, current ->
            if ("setMiBackgroundBlurMode" in changed && current()) mode.invoke(view, if (enabled.getBoolean(nativeOwner)) 1 else 0)
            if (changed.any { it == "setMiBackgroundBlurRadius" || it == "setMiGlassBlurRadius" } && current())
                update.invoke(nativeOwner, *maxima.map { it.getInt(nativeOwner) as Any }.toTypedArray())
        }
    }

    private fun installBlendRefresh() {
        val type = runtime.type("com.miui.systemui.shade.blur.ShadeBlendBlurController\$BlendBackground") ?: return
        val field = runCatching { type.getDeclaredField("view").apply { isAccessible = true } }.getOrNull() ?: return
        val enabled = runCatching { type.getDeclaredField("enabled").apply { isAccessible = true } }.getOrNull() ?: return
        val apply = runCatching { type.getDeclaredMethod("applyBlendColors").apply { isAccessible = true } }.getOrNull() ?: return
        val mode = runCatching { View::class.java.getMethod("setMiViewBlurMode", Int::class.javaPrimitiveType) }.getOrNull() ?: return
        val channels = setOf("setMiViewBlurMode", "setMiBackgroundBlendColors")
        track(type, field, "blend", setOf("applyBlendColors", "updateBlendColors", "setEnabled"), channels, 2) { nativeOwner, view, changed, current ->
            if ("setMiViewBlurMode" in changed && current()) mode.invoke(view, if (enabled.getBoolean(nativeOwner)) 1 else 0)
            if (changed.any { it == "setMiBackgroundBlendColors" || it.startsWith("blend-mode:") } && current()) apply.invoke(nativeOwner)
        }
    }

    private fun source(view: View, channel: String): Source? = synchronized(sources) {
        sources[view]?.get(if (channel.startsWith("blend-mode:") || channel == "clearMiBackgroundBlendColor") "setMiBackgroundBlendColors" else channel)
    }
    private fun clock(channel: String) = synchronized(clocks) { clocks.getOrPut(channel) { MaterialWriteClock() } }
    private fun register(view: View, channels: Set<String>, source: Source) = synchronized(sources) {
        val current = sources.getOrPut(view) { mutableMapOf() }
        channels.forEach { channel ->
            if (current[channel]?.owner?.get()?.let { it !== source.owner.get() } == true) {
                clock(channel).touch(view)
                synchronized(owned) { owned[view]?.remove(channel) }
            }
            current[channel] = source
        }
    }

    /** Native replacement/clear retires all earlier blend entries, even visually equal zeros. */
    fun observeNativeWrite(view: View, channel: String): BackdropWrite? = runCatching {
        val source = source(view, channel) ?: (if (channel == "setBackgroundColor") {
            solidCall.get()?.takeIf { candidate -> candidate.owner.get()?.let { candidate.view(it) === view } == true }
                ?.also { register(view, setOf(channel), it) }
        } else null) ?: return@runCatching null
        val replacesBlend = channel == "setMiBackgroundBlendColors" || channel == "clearMiBackgroundBlendColor"
        val invalidated = if (replacesBlend) synchronized(clocks) { clocks.keys.filter { it == "setMiBackgroundBlendColors" || it.startsWith("blend-mode:") } }
            else if (channel.startsWith("blend-mode:")) listOf("setMiBackgroundBlendColors") else emptyList()
        invalidated.forEach { key -> clock(key).touch(view); synchronized(owned) { owned[view]?.remove(key) } }
        clock(channel).touch(view)
        synchronized(owned) { owned[view]?.remove(channel) }
        val owner = source.owner.get() ?: return@runCatching null
        if (source.view(owner) !== view) return@runCatching null
        val root = viewRoot?.invoke(view) ?: return@runCatching null
        val control = if (channel == "window-blur") surface?.invoke(root) as? SurfaceControl else null
        if (channel == "window-blur" && control?.isValid != true) return@runCatching null
        BackdropWrite(view, owner, root, channel, clock(channel).ticket(view), control)
    }.getOrNull()

    /** Record only an actual adjusted/suppressed native write, never an enabled setting alone. */
    fun ownWrite(view: View, write: BackdropWrite, output: Array<Any?>) {
        val source = source(view, write.channel) ?: return
        val owner = source.owner.get() ?: return
        val root = write.root.get() ?: return
        if (write.view.get() !== view || write.owner.get() !== owner || source.view(owner) !== view ||
            viewRoot?.invoke(view) !== root || !view.isAttachedToWindow || !clock(write.channel).current(write.ticket)) return
        if (write.channel == "window-blur" && (write.control?.get()?.let { it.isValid && surface?.invoke(root) === it } != true)) return
        val color = if (write.channel == "setBackgroundColor") output.firstOrNull() as? Int else null
        synchronized(owned) { owned.getOrPut(view) { mutableMapOf() }[write.channel] =
            Owned(write, BackdropWriteLease(owner, view, root, write.control?.get()), view.background?.let(::WeakReference), color) }
        if (!settings().enabled || !ShadeScope.allows(view)) scheduleRefresh()
    }

    /** Consume each exact current channel, then call its native owner once for the whole group. */
    private fun retireWrites() {
        val entries = synchronized(owned) { owned.entries.flatMap { (view, channels) -> channels.entries.map { Triple(view, it.key, it.value) } } }
        val releases = mutableListOf<Release>()
        entries.forEach { (view, channel, claim) -> runCatching {
            val source = source(view, channel)
            val owner = source?.owner?.get()
            val currentView = if (source != null && owner != null && GlassSurfaces.classify(view) == GlassSurface.SHADE) source.view(owner) else null
            val root = viewRoot?.invoke(view)
            val epoch = ShadeScope.generation
            val active = settings().enabled && ShadeScope.allows(view) && epoch == ShadeScope.generation
            val colorCurrent = claim.color == null || (claim.drawable?.get() != null && claim.drawable.get() === view.background &&
                (view.background as? ColorDrawable)?.color == claim.color)
            val currentControl = if (channel == "window-blur" && root != null) surface?.invoke(root) as? SurfaceControl else null
            val nativeOwner = claim.lease.candidate(active, owner, currentView, root, view.isAttachedToWindow,
                clock(channel).current(claim.write.ticket) && colorCurrent, currentControl,
                currentControl?.isValid ?: (channel != "window-blur"))
            if (nativeOwner != null && source != null && root != null) {
                val release = releases.firstOrNull { it.owner === nativeOwner && it.view === view && it.root === root }
                    ?: Release(source, nativeOwner, view, root, epoch).also(releases::add)
                release.claims[channel] = claim
            }
            if (claim.lease.retired) synchronized(owned) { if (owned[view]?.get(channel) === claim) owned[view]?.remove(channel) }
        }.onFailure { runtime.log("Native backdrop release cancelled safely", it) } }
        releases.sortedBy { it.source.order }.forEach { release -> runCatching {
            fun identityCurrent(): Boolean = release.view.isAttachedToWindow && GlassSurfaces.classify(release.view) == GlassSurface.SHADE &&
                release.source.view(release.owner) === release.view && viewRoot?.invoke(release.view) === release.root &&
                release.channels.all { source(release.view, it)?.owner?.get() === release.owner } &&
                ("window-blur" !in release.channels || release.claims["window-blur"]?.write?.control?.get()?.let {
                    it.isValid && surface?.invoke(release.root) === it
                } == true)
            fun current(): Boolean = release.epoch == ShadeScope.generation && identityCurrent()
            // Revalidate once before entering this owner's native batch. Its own
            // writes legitimately advance these stamps while the batch proceeds.
            val stampsCurrent = release.claims.all { (key, claim) ->
                !claim.lease.retired && clock(key).current(claim.write.ticket) &&
                    synchronized(owned) { owned[release.view]?.get(key) === claim }
            }
            if (current() && stampsCurrent) {
                val channels = release.claims.filterValues { it.lease.consume(release.owner) }.keys
                channels.forEach { key -> synchronized(owned) {
                    if (owned[release.view]?.get(key) === release.claims[key]) owned[release.view]?.remove(key)
                } }
                if (channels.isNotEmpty()) try { release.source.fresh(release.owner, release.view, channels, ::current) }
                finally {
                    // Reentry may cancel the rest of a partially submitted batch.
                    // Retain only ownership stamps that no native write took over.
                    if (release.epoch != ShadeScope.generation && identityCurrent()) {
                        channels.forEach { key ->
                            val claim = release.claims.getValue(key)
                            val colorCurrent = claim.color == null || (claim.drawable?.get() != null &&
                                claim.drawable.get() === release.view.background && (release.view.background as? ColorDrawable)?.color == claim.color)
                            if (clock(key).current(claim.write.ticket) && colorCurrent) synchronized(owned) {
                                owned.getOrPut(release.view) { mutableMapOf() }.putIfAbsent(key,
                                    Owned(claim.write, BackdropWriteLease(release.owner, release.view, release.root,
                                        claim.write.control?.get()), claim.drawable, claim.color))
                            }
                        }
                        scheduleRefresh()
                    }
                }
            } else if (stampsCurrent && release.epoch != ShadeScope.generation) {
                // A phase change can cancel delivery without replacing the owner.
                // Keep the claim until the next frame can reassess the new scene.
                scheduleRefresh()
            }
        }.onFailure { runtime.log("Native backdrop owner retained its current state", it) } }
    }

    private fun installSolidRefresh() {
        val type = runtime.type("com.miui.systemui.shade.blur.ShadeSolidBackgroundController") ?: return
        val field = runCatching { type.getDeclaredField("windowBackground").apply { isAccessible = true } }.getOrNull() ?: return
        val get = runCatching { field.type.getMethod("get").apply { isAccessible = true } }.getOrNull() ?: return
        val update = runCatching { type.getDeclaredMethod("updateWindow").apply { isAccessible = true } }.getOrNull() ?: return
        val owners = WeakHashMap<Any, WeakReference<View>>()
        val leases = WeakHashMap<Any, SolidBackdropAlphaLease<Any, View, Drawable>>()
        runtime.hook(update, "window-backdrop:solid-current") { chain ->
            val previous = solidCall.get()
            val currentOwner = chain.thisObject
            if (currentOwner != null) solidCall.set(Source(currentOwner, 3,
                { get.invoke(field.get(it)) as? View }, { nativeOwner, _, _, current -> if (current()) update.invoke(nativeOwner) }))
            val result = try { chain.proceed() }
                finally { if (previous == null) solidCall.remove() else solidCall.set(previous) }
            runCatching {
                val owner = chain.thisObject ?: return@runCatching
                // Both retained native branches set their current alpha, superseding
                // our previous write even when the new native value happens to be zero.
                synchronized(leases) { leases.remove(owner) }
                // Resolve only after the OEM has used this Lazy, preserving startup order.
                val view = get.invoke(field.get(owner)) as? View ?: return@runCatching
                synchronized(owners) { owners[owner] = WeakReference(view) }
                register(view, setOf("setBackgroundColor"), Source(owner, 3,
                    { get.invoke(field.get(it)) as? View }, { nativeOwner, _, _, current -> if (current()) update.invoke(nativeOwner) }))
                nativeBackground(view)
                val epoch = ShadeScope.generation
                if (WindowBackdropPolicy.transparent(settings()) && view.isAttachedToWindow && GlassSurfaces.classify(view) == GlassSurface.SHADE &&
                    ShadeScope.allows(view) && epoch == ShadeScope.generation && view.alpha != 0f) {
                    val root = viewRoot?.invoke(view) ?: return@runCatching
                    val lease = SolidBackdropAlphaLease(owner, view, root, view.background)
                    // Also removes the OEM's preblurred screenshot fallback at the
                    // explicit transparent endpoint; never multiply a card's alpha.
                    view.alpha = 0f
                    synchronized(leases) { leases[owner] = lease }
                    if (epoch != ShadeScope.generation || !WindowBackdropPolicy.transparent(settings()) ||
                        !ShadeScope.allows(view)) scheduleRefresh()
                }
            }.onFailure { runtime.log("Solid backdrop retained native state", it) }
            result
        }
        solidRefresh = {
            val current = synchronized(owners) { owners.entries.map { it.key to it.value.get() } }
            current.forEach { (owner, recordedView) ->
                val view = get.invoke(field.get(owner)) as? View
                val lease = synchronized(leases) { leases[owner] }
                val epoch = ShadeScope.generation
                val admitted = view != null && view.isAttachedToWindow && ShadeScope.allows(view) &&
                    epoch == ShadeScope.generation
                val state = settings()
                val needsOverride = admitted && WindowBackdropPolicy.transparent(state)
                val root = view?.let { viewRoot?.invoke(it) }
                val restored = lease?.retire(needsOverride, owner, view, root, view?.background,
                    view?.isAttachedToWindow == true, view?.alpha ?: Float.NaN) { nativeOwner ->
                    synchronized(leases) { if (leases[nativeOwner] === lease) leases.remove(nativeOwner) }
                    // The retained native non-screenshot path sets alpha=1; its
                    // screenshot path sets alpha=current ratio. No saved alpha is replayed.
                    update.invoke(nativeOwner)
                } == true
                if (lease?.retired == true) synchronized(leases) {
                    if (leases[owner] === lease) leases.remove(owner)
                }
                // An expired lease cannot authorize a write to a new View/root, or
                // overwrite a newer native alpha. A later independent scoped edit
                // can still use the normal current-state refresh below.
                val retiredWithoutRestore = lease?.retired == true && !restored
                if (!restored && !retiredWithoutRestore && state.enabled && admitted && view === recordedView &&
                    epoch == ShadeScope.generation && get.invoke(field.get(owner)) === view)
                    update.invoke(owner)
            }
        }
    }


    /** Retain native writers weakly; no callback or queued action owns the native instances. */
    private fun track(type: Class<*>, field: Field, id: String, methods: Set<String>, channels: Set<String>, order: Int,
                      write: (Any, View, Set<String>, () -> Boolean) -> Unit) {
        if (!View::class.java.isAssignableFrom(field.type)) return
        val owners = WeakHashMap<Any, WeakReference<View>>()
        fun observe(owner: Any?) {
            if (owner == null) return
            val view = runCatching { field.get(owner) as? View }.getOrNull() ?: return
            GlassSurfaces.markWindowBackdrop(view)
            register(view, channels, Source(owner, order, { field.get(it) as? View }, write))
            synchronized(owners) { owners[owner] = WeakReference(view) }
        }
        type.declaredConstructors.forEachIndexed { index, constructor ->
            runtime.hook(constructor, "window-backdrop:$id-created:$index") { chain ->
                val result = chain.proceed(); runCatching { observe(chain.thisObject) }; result
            }
        }
        type.declaredMethods.filter { it.name in methods }.forEach { method ->
            runtime.hook(method, "window-backdrop:$id-view:${method.name}") { chain ->
                runCatching { observe(chain.thisObject) }; chain.proceed()
            }
        }
        rebinders += {
            val active = synchronized(owners) { owners.entries.map { it.key to it.value.get() } }
            active.forEach { (owner, view) ->
                if (settings().enabled && view != null && view.isAttachedToWindow && GlassSurfaces.classify(view) == GlassSurface.SHADE && ShadeScope.allows(view)) {
                    val epoch = ShadeScope.generation
                    val root = viewRoot?.invoke(view)
                    val current = { settings().enabled && view.isAttachedToWindow && field.get(owner) === view && viewRoot?.invoke(view) === root &&
                        epoch == ShadeScope.generation && ShadeScope.allows(view) && epoch == ShadeScope.generation }
                    runCatching { write(owner, view, channels, current) }
                        .onFailure { runtime.log("Native backdrop writer retained its current state: $id", it) }
                }
            }
        }
    }

    private fun validSurface(view: View): Boolean = runCatching {
        val root = viewRoot?.invoke(view)
        root != null && (surface?.invoke(root) as? SurfaceControl)?.isValid == true
    }.getOrDefault(false)

    /** Edits and scope changes share one frame; releases ask only still-owned native writers. */
    fun scheduleRefresh() {
        refresh?.let(handler::removeCallbacks)
        refresh = Runnable {
            retireWrites()
            if (settings().enabled) {
                rebinders.forEach { rebind -> runCatching(rebind).onFailure { runtime.log("Native backdrop refresh cancelled safely", it) } }
                val windows = synchronized(sources) { sources.entries.mapNotNull { (view, channels) -> channels["window-blur"]?.let { view to it } } }
                windows.forEach { (view, source) ->
                    val owner = source.owner.get()
                    if (settings().enabled && owner != null && view.isAttachedToWindow && ShadeScope.allows(view) && validSurface(view))
                        runCatching { (owner as Runnable).run() }.onFailure { runtime.log("Window backdrop refresh retained native state", it) }
                }
            }
            solidRefresh?.let { rebind -> runCatching(rebind).onFailure { runtime.log("Solid backdrop refresh cancelled safely", it) } }
        }.also { handler.postDelayed(it, 16L) }
    }
}
