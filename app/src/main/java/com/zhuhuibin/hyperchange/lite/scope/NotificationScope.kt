// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.SettingsMigration
import com.zhuhuibin.hyperchange.lite.glass.GlassSurfaces
import com.zhuhuibin.hyperchange.lite.notifications.read
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Focused OEM scene callbacks, installed before SystemUI constructs its controllers. */
internal object NotificationScope {
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val listeners = mutableListOf<() -> Unit>()
    private val pendingRoles = WeakHashMap<View, NotificationRole>()
    private val roleGenerations = WeakHashMap<View, Long>()
    @Volatile private var sceneGeneration = 0L
    @Volatile private var scene = NotificationScene()
    @Volatile private var lockscreenEffects = false
    @Volatile var generation = 0L
        private set
    private var installed = false
    @Volatile private var pendingTransitions = 0
    @Volatile private var keyguard = WeakReference<Any>(null)
    @Volatile private var statusBar = WeakReference<Any>(null)
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun role(view: View): NotificationRole {
        if (GlassSurfaces.media(view)) return NotificationRole.MEDIA
        val row = GlassSurfaces.row(view)
        synchronized(pendingRoles) { pendingRoles[row]?.let { return it } }
        return if (GlassSurfaces.headsUp(view)) NotificationRole.HEADS_UP else NotificationRole.ORDINARY
    }

    fun sceneAllows(view: View): Boolean {
        refreshSnapshot()
        return NotificationScopePolicy.allows(role(view), lockscreenEffects, scene)
    }
    fun token(view: View, ordinaryScene: Boolean = true): NotificationScopeToken {
        val role = role(view)
        val row = GlassSurfaces.row(view) ?: view
        val roleGeneration = synchronized(roleGenerations) { roleGenerations[row] ?: 0L }
        return NotificationScopeToken(if (ordinaryScene && role == NotificationRole.ORDINARY) sceneGeneration else 0L, role, roleGeneration)
    }
    fun isCurrent(view: View, token: NotificationScopeToken, ordinaryScene: Boolean = true): Boolean = token == token(view, ordinaryScene)

    fun addListener(listener: () -> Unit) { synchronized(listeners) { listeners += listener } }

    @Synchronized fun install(runtime: HookRuntime) {
        if (installed || runtime.packageName != "com.android.systemui") return
        installed = true
        fun preferencesChanged() {
            val enabled = SettingsMigration.resolve(runtime.preferences.all)[SettingsMigration.LOCKSCREEN_KEY] == true
            if (lockscreenEffects != enabled) {
                lockscreenEffects = enabled
                invalidate()
            }
        }
        preferencesChanged()
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Missing keys can still be a legacy remote snapshot until the settings App opens.
            if (key == null || key == SettingsMigration.LOCKSCREEN_KEY || key == SettingsMigration.VERSION_KEY ||
                !runtime.preferences.contains(SettingsMigration.LOCKSCREEN_KEY)) preferencesChanged()
        }.also(runtime.preferences::registerOnSharedPreferenceChangeListener)

        var installedSignals = 0
        fun observe(className: String, names: Set<String>, sample: (Any) -> Unit) {
            val type = runtime.type(className) ?: return
            type.declaredConstructors.forEachIndexed { index, constructor ->
                runtime.hook(constructor, "notification-scope:construct:$className:$index") { chain ->
                    val result = chain.proceed()
                    chain.thisObject?.let(sample)
                    result
                }
            }
            type.declaredMethods.filter { it.name in names }.forEachIndexed { index, method ->
                runtime.hook(method, "notification-scope:$className:${method.name}:$index") { chain ->
                    synchronized(this) { pendingTransitions++ }
                    // Enter the conservative state before OEM setters notify their observers.
                    if (method.name == "notifyKeyguardState" && chain.getArg(0) == true) {
                        update { copy(showing = true) }
                    } else if (method.name == "notifyKeyguardGoingAway" && chain.getArg(0) == true) {
                        update { copy(transitioning = true) }
                    } else if (method.name == "setIsDozing" && chain.getArg(0) == true) {
                        update { copy(dozing = true) }
                    } else if (method.name == "setState" && chain.getArg(0) != 0) {
                        update { copy(statusBarState = chain.getArg(0) as? Int) }
                    }
                    try { chain.proceed() }
                    finally {
                        synchronized(this) { pendingTransitions-- }
                        chain.thisObject?.let(sample)
                    }
                }
                installedSignals++
            }
        }
        observe("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", setOf(
            "notifyKeyguardState", "notifyKeyguardGoingAway",
        )) { owner -> keyguard = WeakReference(owner); refreshSnapshot() }
        observe("com.android.systemui.statusbar.StatusBarStateControllerImpl", setOf("setIsDozing", "setState")) { owner ->
            statusBar = WeakReference(owner)
            refreshSnapshot()
        }
        runtime.type("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow")?.declaredMethods
            ?.filter { it.name == "setHeadsUp" && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)) }
            ?.forEach { method ->
                runtime.hook(method, "notification-scope:heads-up-role") { chain ->
                    val view = chain.thisObject as? View ?: return@hook chain.proceed()
                    val next = if (chain.getArg(0) == true) NotificationRole.HEADS_UP else NotificationRole.ORDINARY
                    val changed = !GlassSurfaces.media(view) && role(view) != next
                    if (changed) {
                        synchronized(pendingRoles) { pendingRoles[view] = next }
                        synchronized(roleGenerations) { roleGenerations[view] = (roleGenerations[view] ?: 0L) + 1L }
                        invalidate(sceneChanged = false)
                    }
                    try { chain.proceed() }
                    finally {
                        if (changed) {
                            synchronized(pendingRoles) { pendingRoles.remove(view) }
                            if (role(view) != next) {
                                synchronized(roleGenerations) { roleGenerations[view] = (roleGenerations[view] ?: 0L) + 1L }
                                invalidate(sceneChanged = false)
                            }
                        }
                    }
                }
            }
        runtime.log("Notification scope signals installed: $installedSignals; ordinary effects wait for confirmed unlocked state when lockscreen scope is off")
    }

    private fun readBoolean(owner: Any, field: String): Boolean? = runCatching { owner.read(field) as? Boolean }.getOrNull()
    private fun readInt(owner: Any, field: String): Int? = runCatching { owner.read(field) as? Int }.getOrNull()

    @Synchronized private fun refreshSnapshot() {
        if (pendingTransitions > 0) return
        val keyguardOwner = keyguard.get()
        val statusOwner = statusBar.get()
        val showing = keyguardOwner?.let { readBoolean(it, "mShowing") }
        val away = keyguardOwner?.let { readBoolean(it, "mKeyguardGoingAway") }
        val fading = keyguardOwner?.let { readBoolean(it, "mKeyguardFadingAway") }
        val miFading = keyguardOwner?.let { readBoolean(it, "mMiKeyguardFadingAway") }
        // This ROM exposes no fade-complete callback on this controller. Read its
        // fields at each decision instead of waiting for a nonexistent callback.
        val transition = if (showing == null || away == null || fading == null || miFading == null) null
            else fading || miFading || (showing && away)
        update { copy(showing = showing, transitioning = transition,
            dozing = statusOwner?.let { readBoolean(it, "mIsDozing") },
            statusBarState = statusOwner?.let { readInt(it, "mState") }) }
    }

    private fun update(change: NotificationScene.() -> NotificationScene) {
        val changed = synchronized(this) {
            val next = scene.change()
            if (next == scene) false else {
                scene = next
                generation++
                sceneGeneration++
                true
            }
        }
        if (changed) notifyListeners()
    }

    private fun invalidate(sceneChanged: Boolean = true) {
        synchronized(this) { generation++; if (sceneChanged) sceneGeneration++ }
        notifyListeners()
    }

    private fun notifyListeners() {
        val notify = Runnable { synchronized(listeners) { listeners.toList() }.forEach { runCatching(it) } }
        if (Looper.myLooper() == Looper.getMainLooper()) notify.run() else main.post(notify)
    }
}
