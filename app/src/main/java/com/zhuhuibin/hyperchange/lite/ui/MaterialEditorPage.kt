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

internal fun previewTargetForSetting(spec: LiteSetting): SettingPreviewTarget {
    val key = spec.key
    val scene = when {
        key.startsWith("glass_sidebar") -> GlassPlayground.Scene.SIDEBAR
        key == "glass_assistant" -> GlassPlayground.Scene.WIDGET
        key == SettingsMigration.MEDIA_KEY || key == "notification_press_tilt_media_enabled" -> GlassPlayground.Scene.MEDIA
        key == "glass_shade" || key.startsWith("glass_background_") -> GlassPlayground.Scene.SHADE
        key.startsWith("cc_") || key == "glass_control_center" -> GlassPlayground.Scene.CONTROL
        key.startsWith("volume_") || key == "glass_volume" -> GlassPlayground.Scene.VOLUME
        key.startsWith("notification_heads_up_") -> GlassPlayground.Scene.HEADS_UP
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
