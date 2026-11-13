// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import android.content.SharedPreferences
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import com.zhuhuibin.hyperchange.lite.scope.ShadeScope

/** A single native setter owner for SystemUI and its control-center plugin. */
object GlassHooks {
    private var system: SystemGlass? = null
    private var assistant: AssistantGlass? = null
    private var sidebar: SidebarGlass? = null

    @Synchronized fun install(runtime: HookRuntime) {
        when (runtime.packageName) {
            "com.android.systemui", "miui.systemui.plugin" -> {
                val state = system ?: SystemGlass(runtime).also { system = it; it.install() }
                state.installEffects(runtime.loader)
            }
            "com.miui.personalassistant" -> if (assistant == null) AssistantGlass(runtime).also { assistant = it; it.install() }
            "com.miui.securitycenter" -> if (sidebar == null) SidebarGlass(runtime).also { sidebar = it; it.install() }
        }
    }
}

private class SystemGlass(private val runtime: HookRuntime) {
    @Volatile private var state = GlassRuntimeSettings.read(runtime.preferences)
    private fun notificationPolicy(): NotificationMaterialPolicy = NotificationMaterialPolicy(
        state.notifications, state.media, state.glass.enabled, state.unify,
    )
    private fun notificationRange(view: android.view.View): Boolean =
        notificationPolicy().range(GlassSurfaces.media(view), NotificationScope.sceneAllows(view))
    private val normal = NotificationMaterial(runtime, ::notificationRange, { state.glass.enabled }, {
        headsUpGlassAllowed(state.headsUp, state.glass.enabled, state.notifications, NotificationScope.confirmedUnlocked())
    }) { view ->
        notificationPolicy().force(GlassSurfaces.media(view), NotificationScope.sceneAllows(view),
            NotificationScope.role(view) == com.zhuhuibin.hyperchange.lite.scope.NotificationRole.HEADS_UP, GlassSurfaces.focus(view))
    }
    private val window: WindowBackdropHooks = WindowBackdropHooks(runtime, { state.shadeGlass }, ::nativeBackdropBackground)
    private fun nativeBackdropBackground(view: android.view.View): Unit = engine.observeNativeBackdropBackground(view)
    private val engine: ViewMaterialEngine = ViewMaterialEngine(runtime, { GlassSurfaces.classify(it) != null }, { view ->
        val surface = GlassSurfaces.classify(view)
        val backdrop = surface == GlassSurface.SHADE || GlassSurfaces.isShadeWindow(view)
        val scopeEnabled = when (surface) {
            GlassSurface.NOTIFICATION -> notificationRange(view)
            GlassSurface.CONTROL_CENTER -> state.controlCenter
            GlassSurface.VOLUME -> state.volume
            GlassSurface.SHADE -> state.shade
            null -> GlassSurfaces.isShadeWindow(view) && state.shade
        }
        val selected = if (backdrop) state.shadeGlass else state.glass
        selected.copy(enabled = if (backdrop) state.shadeGlass.enabled else if (surface == GlassSurface.NOTIFICATION)
            notificationPolicy().tune(GlassSurfaces.media(view), NotificationScope.sceneAllows(view))
            else state.glass.enabled && scopeEnabled)
    }, { view ->
        if (normal.shouldUnify(view)) normal.normalRecipe(view)
        else if (state.glass.enabled && state.volume && GlassSurfaces.classify(view) == GlassSurface.VOLUME) normal.normalRecipe(view)
        else null
    }, normal::normalBlends, { view -> if (GlassSurfaces.classify(view) == GlassSurface.VOLUME) 0.48f else 0f },
        scopeAllows = { view ->
            when {
                GlassSurfaces.classify(view) == GlassSurface.NOTIFICATION ->
                    notificationRange(view) && (state.glass.enabled || normal.shouldUnify(view))
                GlassSurfaces.classify(view) == GlassSurface.SHADE || GlassSurfaces.isShadeWindow(view) ->
                    ShadeScope.allows(view)
                else -> true
            }
        }, nativeNotificationRefresh = normal::requestRefresh,
        nativeBackdropWrite = window::observeNativeWrite, ownBackdropWrite = window::ownWrite)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("glass_")) {
            val next = GlassRuntimeSettings.read(runtime.preferences)
            // One atomic preset edit can notify several keys with the same full
            // snapshot. Recompute each current root once for that actual change.
            if (next != state) {
                val previous = state
                val cards = next.copy(shadeGlass = previous.shadeGlass, shade = previous.shade) != previous
                val backdrops = next.shadeGlass != previous.shadeGlass
                state = next
                if (cards) runCatching { normal.refresh() }
                    .onFailure { runtime.log("Notification material refresh cancelled safely", it) }
                if (cards || backdrops) engine.scheduleRefresh(cards = cards, backdrops = backdrops)
                if (backdrops) window.scheduleRefresh()
            }
        }
    }
    fun install() {
        NotificationScope.install(runtime)
        NotificationScope.addListener { refresh(sceneOnly = true) }
        ShadeScope.addListener {
            engine.retireInactiveBackdrops()
            engine.scheduleRefresh(sceneOnly = true)
            window.scheduleRefresh()
        }
        runtime.preferences.registerOnSharedPreferenceChangeListener(listener)
        engine.install()
        window.install()
    }
    private fun refresh(sceneOnly: Boolean = false) {
        runCatching { normal.refresh(sceneOnly) }.onFailure { runtime.log("Notification material refresh cancelled safely", it) }
        engine.scheduleRefresh(sceneOnly)
    }
    fun installEffects(loader: ClassLoader) {
        ShadeScope.install(HookRuntime(runtime.module, loader, runtime.preferences, runtime.packageName))
        normal.installEffects(loader)
    }
}
