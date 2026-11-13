// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import android.content.SharedPreferences
import com.zhuhuibin.hyperchange.lite.SettingsMigration
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class GlassSettingsSnapshotTest {
    @Test fun oneRuntimeReadUsesOneConsistentPreferenceSnapshot() {
        var reads = 0
        val values = mapOf(
            SettingsMigration.VERSION_KEY to SettingsMigration.VERSION,
            SettingsMigration.BACKDROP_VERSION_KEY to SettingsMigration.BACKDROP_VERSION,
            "glass_enabled" to true, "glass_background_blur" to 44f, "glass_refraction" to 20f,
            "glass_shade_enabled" to true, "glass_shade_blur" to 90f, "glass_shade_scale" to 0f,
            "glass_shade_opacity" to 73f,
        )
        val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            check(method.name == "getAll") { "Read outside the snapshot: ${method.name}" }
            reads++
            values
        } as SharedPreferences
        val state = GlassRuntimeSettings.read(preferences)
        assertEquals(1, reads)
        assertEquals(44, state.glass.backgroundBlur)
        assertEquals(20, state.glass.adjustments["refraction"])
        assertTrue(state.shade)
        assertEquals(90, state.shadeGlass.backgroundBlur)
        assertEquals(100, state.shadeGlass.backgroundScale)
        assertEquals(73, state.shadeGlass.backgroundOpacity)
        assertTrue(state.shadeGlass.adjustments.values.all { it == 0 })
    }
}
