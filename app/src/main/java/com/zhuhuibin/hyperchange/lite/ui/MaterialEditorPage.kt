// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.SettingsMigration
import com.zhuhuibin.hyperchange.lite.preview.GlassPlayground

/** A setting selects both its representative surface and its exact motion inputs. */
internal data class SettingPreviewTarget(
    val scene: GlassPlayground.Scene,
    val focusGroup: String,
    val focusKey: String,
)

internal fun previewTargetForSetting(spec: LiteSetting, values: Map<String, *> = emptyMap<String, Any>()): SettingPreviewTarget {
    val key = spec.key
    fun flag(key: String, default: Boolean = false) = values[key] as? Boolean ?: default
    fun systemCore(): GlassPlayground.Scene = when {
        flag("glass_notifications", true) -> GlassPlayground.Scene.NOTIFICATION
        flag("glass_control_center", true) -> GlassPlayground.Scene.CONTROL
        flag("glass_volume", true) -> GlassPlayground.Scene.VOLUME
        flag("glass_shade", true) -> GlassPlayground.Scene.SHADE
        flag(SettingsMigration.MEDIA_KEY) -> GlassPlayground.Scene.MEDIA
        else -> GlassPlayground.Scene.NOTIFICATION
    }
    fun sharedCore(): GlassPlayground.Scene = when {
        flag("glass_enabled") -> systemCore()
        flag("glass_assistant") -> GlassPlayground.Scene.WIDGET
        flag("glass_sidebar") -> GlassPlayground.Scene.SIDEBAR
        else -> systemCore()
    }
    val scene = when {
        key.startsWith("glass_sidebar") -> GlassPlayground.Scene.SIDEBAR
        key == "glass_assistant" -> GlassPlayground.Scene.WIDGET
        key == SettingsMigration.MEDIA_KEY || key == "notification_press_tilt_media_enabled" -> GlassPlayground.Scene.MEDIA
        key == "glass_shade" -> GlassPlayground.Scene.SHADE
        key.startsWith("glass_background_") -> if (flag("glass_shade", true)) GlassPlayground.Scene.SHADE else systemCore()
        key.startsWith("cc_") || key == "glass_control_center" -> GlassPlayground.Scene.CONTROL
        key.startsWith("volume_") || key == "glass_volume" -> GlassPlayground.Scene.VOLUME
        key.startsWith("notification_heads_up_") -> GlassPlayground.Scene.HEADS_UP
        key.startsWith("notification_press_tilt_") && !spec.isToggle &&
            !flag("notification_press_tilt_row_enabled", true) && flag("notification_press_tilt_media_enabled", true) -> GlassPlayground.Scene.MEDIA
        key.startsWith("glass_") && !spec.isToggle -> sharedCore()
        key == "glass_enabled" -> systemCore()
        else -> GlassPlayground.Scene.NOTIFICATION
    }
    return SettingPreviewTarget(scene, spec.group, key)
}

internal fun materialSceneForSetting(spec: LiteSetting): GlassPlayground.Scene = previewTargetForSetting(spec).scene

internal fun materialSceneForCategory(category: MaterialCategory): GlassPlayground.Scene = when (category) {
    MaterialCategory.BACKGROUND -> GlassPlayground.Scene.SHADE
    MaterialCategory.CONTROL -> GlassPlayground.Scene.CONTROL
    MaterialCategory.VOLUME -> GlassPlayground.Scene.VOLUME
    MaterialCategory.ASSISTANT -> GlassPlayground.Scene.WIDGET
    else -> GlassPlayground.Scene.NOTIFICATION
}

internal fun settingDisplayTitle(spec: LiteSetting): String = spec.title +
    if (spec.key == SettingsMigration.MEDIA_KEY || spec.key == SettingsMigration.LOCKSCREEN_KEY) "（注意兼容）" else ""
