// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

import android.os.Handler
import android.os.Looper
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.notifications.call
import com.zhuhuibin.hyperchange.lite.notifications.read
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Contract: retained SystemUI.apk SHA-256
 * 074486d6856c8eda2764d8864d99365e178d4a4758836e3c44152862ddc8b7e1.
 * Panel isExpanded() and the expansion callback's boolean include pinned heads-up;
 * mExpandedFraction and isExpandedWithoutHeadsUp() distinguish the real panel.
 * The retained CC ExpandController exposes getExpansion(); onStop does not reset it.
 * Each root/kind has one live controller; different kinds may share a window root.
 * Authorization never comes from an expanded controller in another window.
 */
internal object ShadeScope {
    private enum class Kind { NOTIFICATION, CONTROL_CENTER }
    private val lock = Any()
    private val installed = Collections.synchronizedSet(mutableSetOf<Class<*>>())
    private val owners = WeakHashMap<Any, Owner>()
    private val roots = ShadeScopeRegistry<View, Kind, Owner>()
    private val listeners = mutableListOf<() -> Unit>()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var sceneListenerInstalled = false
    private var notificationPending = false
    @Volatile var generation = 0L
        private set

    private class Owner(controller: Any, val kind: Kind) : View.OnAttachStateChangeListener {
        val controller = WeakReference(controller)
        var view = WeakReference<View>(null)
        var root = WeakReference<View>(null)
        val lease = ShadeScopeLease()
        val active: Boolean get() = lease.active
        var stopped = false
        var destroyed = false
        var closingDepth = 0
        override fun onViewAttachedToWindow(v: View) {
            // Attachment alone cannot revive a stale positive fraction from an old window.
            sample(this, openSignal = false, bindRoot = true)
        }
        override fun onViewDetachedFromWindow(v: View) = detach(this)
    }

    private data class Snapshot(
        val view: View?, val root: View?, val attached: Boolean, val expanded: Boolean,
        val sceneGeneration: Long,
    )

    fun addListener(listener: () -> Unit) { synchronized(lock) { listeners += listener } }

    /** Main SystemUI and independently loaded CC plugins may call this repeatedly. */
    fun install(runtime: HookRuntime) {
        if (runtime.packageName != "com.android.systemui") return
        val addSceneListener = synchronized(lock) {
            if (sceneListenerInstalled) false else { sceneListenerInstalled = true; true }
        }
        if (addSceneListener) NotificationScope.addListener {
            val current = synchronized(lock) { owners.values.toList() }
            current.forEach { sample(it, openSignal = false, bindRoot = false) }
        }
        installType(runtime, "com.android.systemui.shade.NotificationPanelViewController", Kind.NOTIFICATION)
        installType(runtime, "miui.systemui.controlcenter.windowview.ControlCenterExpandController", Kind.CONTROL_CENTER)
    }

    /** A read may revoke stale ownership, but only a new OEM expansion signal can open it. */
    fun allows(view: View): Boolean {
        if (!view.isAttachedToWindow) return false
        val targetRoot = view.rootView
        val current = synchronized(lock) { roots.owners(targetRoot) }
        current.forEach { sample(it, openSignal = false, bindRoot = false) }
        return synchronized(lock) {
            // Foldable layouts may host notification and CC controllers in the same window.
            current.any { owner ->
                ShadeScopePolicy.owns(targetRoot, owner.root.get(), owner.view.get()?.rootView,
                    view.isAttachedToWindow && owner.view.get()?.isAttachedToWindow == true,
                    owner.active && roots.owner(targetRoot, owner.kind) === owner,
                    owner.stopped || owner.destroyed, owner.closingDepth != 0)
            }
        }
    }

    private fun installType(runtime: HookRuntime, name: String, kind: Kind) {
        val type = runtime.type(name) ?: return
        if (!installed.add(type)) return
        type.declaredConstructors.forEachIndexed { index, constructor ->
            runtime.hook(constructor, "shade-scope:construct:$name:$index") { chain ->
                val result = chain.proceed()
                chain.thisObject?.let { controller ->
                    runCatching { sample(owner(controller, kind), openSignal = false, bindRoot = true) }
                }
                result
            }
        }
        val signals = if (kind == Kind.NOTIFICATION) setOf(
            "setExpandedHeight", "setExpandedFraction", "setExpandedHeightInternalAosp",
            "updateExpandedHeight\$1", "updateExpansionAndVisibility", "instantCollapse",
            "onTrackingStarted\$2", "onTrackingStopped\$1", "notifyExpandingFinished",
        ) else setOf("setExpansion", "notifyExpansionChanged", "onExpandChange", "onExpandFinish", "handleAnimFinish")
        val lifecycle = setOf("onStart", "onStop", "onDestroy", "onViewAttached", "onViewDetached")
        type.declaredMethods.filter { it.name in signals || it.name in lifecycle }.forEachIndexed { index, method ->
            val stopping = method.name in setOf("onStop", "onDestroy", "onViewDetached")
            val starting = method.name in setOf("onStart", "onViewAttached")
            val fractionSetter = method.name in setOf("setExpansion", "notifyExpansionChanged", "setExpandedHeight",
                "setExpandedFraction", "setExpandedHeightInternalAosp")
            runtime.hook(method, "shade-scope:$name:${method.name}:$index") { chain ->
                val controller = chain.thisObject ?: return@hook chain.proceed()
                if (!type.isInstance(controller)) return@hook chain.proceed()
                val state = owner(controller, kind)
                val explicitClose = method.name == "instantCollapse" ||
                    (fractionSetter && (chain.getArg(0) as? Number)?.toFloat()?.let { !it.isFinite() || it <= 0f } == true)
                val closing = stopping || starting || explicitClose
                runCatching {
                    if (closing) beginClose(state, stopping, method.name == "onDestroy")
                    else sample(state, openSignal = false, bindRoot = false)
                }
                var completed = false
                try { chain.proceed().also { completed = true } }
                finally {
                    runCatching {
                        if (!completed) synchronized(lock) { if (state.lease.revoke()) changed() }
                        if (closing) synchronized(lock) {
                            state.closingDepth = (state.closingDepth - 1).coerceAtLeast(0)
                            if (stopping) state.stopped = true
                            else if (starting && !state.destroyed) state.stopped = false
                        }
                        if (state.destroyed) detach(state)
                        else sample(state, openSignal = completed && !closing, bindRoot = true)
                    }
                }
            }
        }
    }

    private fun owner(controller: Any, kind: Kind): Owner = synchronized(lock) {
        owners.getOrPut(controller) { Owner(controller, kind) }
    }

    private fun beginClose(owner: Owner, stopping: Boolean, destroyed: Boolean) = synchronized(lock) {
        owner.closingDepth++
        if (stopping) owner.stopped = true
        owner.destroyed = owner.destroyed || destroyed
        if (owner.lease.revoke()) changed()
    }

    private fun capture(owner: Owner): Snapshot {
        val before = NotificationScope.generation
        val unlocked = NotificationScope.confirmedUnlocked()
        val sceneGeneration = NotificationScope.generation
        val controller = owner.controller.get()
        val view = runCatching { (controller?.read("mView") as? View) ?: (controller?.call("getView") as? View) }.getOrNull()
        val root = view?.rootView
        val expanded = runCatching {
            val confirmed = unlocked && before == sceneGeneration
            when (owner.kind) {
                Kind.NOTIFICATION -> ShadeScopePolicy.notification(
                    (controller?.read("mExpandedFraction") as? Number)?.toFloat(),
                    controller?.call("isExpandedWithoutHeadsUp") as? Boolean,
                    (controller?.read("mBarState") as? Number)?.toInt(), confirmed,
                )
                Kind.CONTROL_CENTER -> ShadeScopePolicy.controlCenter(
                    (controller?.call("getExpansion") as? Number)?.toFloat(), confirmed,
                )
            }
        }.getOrDefault(false)
        return Snapshot(view, root, view?.isAttachedToWindow == true && root?.isAttachedToWindow == true,
            expanded, sceneGeneration)
    }

    private fun sample(owner: Owner, openSignal: Boolean, bindRoot: Boolean) {
        val snapshot = capture(owner)
        synchronized(lock) {
            var scopeChanged = false
            val oldView = owner.view.get()
            val oldRoot = owner.root.get()
            val sameBinding = oldView === snapshot.view && oldRoot === snapshot.root
            if (!sameBinding) {
                scopeChanged = owner.lease.revoke() || scopeChanged
                if (oldRoot != null && roots.remove(oldRoot, owner.kind, owner)) {
                    scopeChanged = true
                }
                owner.root.clear()
                if (bindRoot && !owner.destroyed) {
                    if (oldView !== snapshot.view) {
                        oldView?.removeOnAttachStateChangeListener(owner)
                        snapshot.view?.addOnAttachStateChangeListener(owner)
                        owner.view = WeakReference(snapshot.view)
                    }
                    owner.root = WeakReference(snapshot.root)
                }
            }
            if (bindRoot && !owner.destroyed && snapshot.root != null && owner.root.get() === snapshot.root) {
                val binding = roots.bind(snapshot.root, owner.kind, owner)
                if (binding.changed) {
                    binding.replaced?.let {
                        it.lease.revoke()
                        // An old controller's late animation callback cannot steal this root back.
                        it.destroyed = true
                        it.view.get()?.removeOnAttachStateChangeListener(it)
                    }
                    scopeChanged = true
                }
            }
            val valid = snapshot.expanded && snapshot.attached && !owner.stopped && !owner.destroyed &&
                owner.closingDepth == 0 && snapshot.sceneGeneration == NotificationScope.generation &&
                owner.view.get() === snapshot.view && owner.root.get() === snapshot.root &&
                snapshot.root != null && roots.owner(snapshot.root, owner.kind) === owner
            scopeChanged = owner.lease.sample(valid, openSignal) || scopeChanged
            if (scopeChanged) changed()
        }
    }

    private fun detach(owner: Owner) = synchronized(lock) {
        val root = owner.root.get()
        val ownsRoot = root != null && roots.remove(root, owner.kind, owner)
        val scopeChanged = owner.lease.revoke() || ownsRoot
        owner.root.clear()
        if (owner.destroyed) {
            owner.view.get()?.removeOnAttachStateChangeListener(owner)
            owner.view.clear()
        }
        if (scopeChanged) changed()
    }

    /** Epoch changes are synchronous; observers are coalesced and never reenter native setters. */
    private fun changed() {
        generation++
        if (notificationPending) return
        notificationPending = true
        main.post {
            val callbacks = synchronized(lock) { notificationPending = false; listeners.toList() }
            callbacks.forEach { runCatching(it) }
        }
    }
}
