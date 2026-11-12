// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import android.content.SharedPreferences
import com.zhuhuibin.hyperchange.lite.HookRuntime

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
    private val normal = NotificationMaterial(runtime) { state.unify && state.notifications }
    private val engine = ViewMaterialEngine(runtime, { GlassSurfaces.classify(it) != null }, { view ->
        val scopeEnabled = when (GlassSurfaces.classify(view)) {
            GlassSurface.NOTIFICATION -> state.notifications
            GlassSurface.CONTROL_CENTER -> state.controlCenter
            GlassSurface.VOLUME -> state.volume
            GlassSurface.SHADE -> state.shade
            null -> GlassSurfaces.isShadeWindow(view) && state.shade
        }
        state.glass.copy(enabled = state.glass.enabled && scopeEnabled)
    }, { view ->
        if (normal.shouldUnify(view)) normal.normalRecipe(view)
        else if (state.glass.enabled && state.volume && GlassSurfaces.classify(view) == GlassSurface.VOLUME) normal.normalRecipe(view)
        else null
    }, normal::normalBlends, { view -> if (GlassSurfaces.classify(view) == GlassSurface.VOLUME) 0.48f else 0f })
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("glass_")) {
            state = GlassRuntimeSettings.read(runtime.preferences)
            normal.refresh()
            engine.scheduleRefresh()
        }
    }
    fun install() {
        runtime.preferences.registerOnSharedPreferenceChangeListener(listener)
        engine.install()
    }
    fun installEffects(loader: ClassLoader) = normal.installEffects(loader)
}
