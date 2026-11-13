// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

/** The compositor and its shade layer are distinct from foreground card refraction. */
internal object WindowBackdropPolicy {
    fun radius(native: Int, enabled: Boolean, percent: Int): Int =
        if (enabled) softGlassBlur(native, percent) else native
    fun transparent(settings: SoftGlassSettings): Boolean = settings.enabled &&
        settings.backgroundBlur == 0 && settings.backgroundOpacity == 0
}
