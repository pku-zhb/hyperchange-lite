// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

/** The narrow side panel omits its footer; the full panel retains OEM controls. */
internal object VolumePanelAppearance {
    fun hideRinger(controlCenter: Boolean, expanded: Boolean, narrow: Boolean, explicitHide: Boolean): Boolean =
        !controlCenter && !expanded && (narrow || explicitHide)

    fun hideExpand(controlCenter: Boolean, expanded: Boolean, longPress: Boolean): Boolean =
        !controlCenter && !expanded && longPress
}

/** Keep the native visibility and yield if OEM replaced our temporary hidden state. */
internal class VolumeButtonVisibility {
    private var original: Int? = null

    fun resolve(current: Int, hide: Boolean, nativeUpdated: Boolean, hidden: Int): Int {
        if (hide) {
            if (original == null || nativeUpdated && current != hidden) original = current
            return hidden
        }
        val saved = original ?: return current
        original = null
        return if (current == hidden) saved else current
    }
}
