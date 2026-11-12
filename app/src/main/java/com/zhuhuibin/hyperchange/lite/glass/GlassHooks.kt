// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import android.content.SharedPreferences
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope

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
    private val normal = NotificationMaterial(runtime, ::notificationRange, { state.glass.enabled }) { view ->
        notificationPolicy().force(GlassSurfaces.media(view), NotificationScope.sceneAllows(view),
            NotificationScope.role(view) == com.zhuhuibin.hyperchange.lite.scope.NotificationRole.HEADS_UP, GlassSurfaces.focus(view))
    }
    private val engine = ViewMaterialEngine(runtime, { GlassSurfaces.classify(it) != null }, { view ->
        val scopeEnabled = when (GlassSurfaces.classify(view)) {
            GlassSurface.NOTIFICATION -> notificationRange(view)
            GlassSurface.CONTROL_CENTER -> state.controlCenter
            GlassSurface.VOLUME -> state.volume
            GlassSurface.SHADE -> state.shade
            null -> GlassSurfaces.isShadeWindow(view) && state.shade
        }
        state.glass.copy(enabled = if (GlassSurfaces.classify(view) == GlassSurface.NOTIFICATION)
            notificationPolicy().tune(GlassSurfaces.media(view), NotificationScope.sceneAllows(view))
            else state.glass.enabled && scopeEnabled)
    }, { view ->
        if (normal.shouldUnify(view)) normal.normalRecipe(view)
        else if (state.glass.enabled && state.volume && GlassSurfaces.classify(view) == GlassSurface.VOLUME) normal.normalRecipe(view)
        else null
    }, normal::normalBlends, { view -> if (GlassSurfaces.classify(view) == GlassSurface.VOLUME) 0.48f else 0f },
        scopeAllows = { view ->
            GlassSurfaces.classify(view) != GlassSurface.NOTIFICATION ||
                (notificationRange(view) && (state.glass.enabled || normal.shouldUnify(view)))
        }, nativeNotificationRefresh = normal::requestRefresh)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("glass_")) {
            val next = GlassRuntimeSettings.read(runtime.preferences)
            // One atomic preset edit can notify several keys with the same full
            // snapshot. Recompute each current root once for that actual change.
            if (next != state) { state = next; refresh() }
        }
    }
    fun install() {
        NotificationScope.install(runtime)
        NotificationScope.addListener { refresh(sceneOnly = true) }
        runtime.preferences.registerOnSharedPreferenceChangeListener(listener)
        engine.install()
    }
    private fun refresh(sceneOnly: Boolean = false) {
        runCatching { normal.refresh(sceneOnly) }.onFailure { runtime.log("Notification material refresh cancelled safely", it) }
        engine.scheduleRefresh(sceneOnly)
    }
    fun installEffects(loader: ClassLoader) = normal.installEffects(loader)
}
