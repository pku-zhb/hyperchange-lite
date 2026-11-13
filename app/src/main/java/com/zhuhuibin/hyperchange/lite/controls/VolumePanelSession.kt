// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

/** Slider progress animates every frame; only a new discrete native level is an input update. */
internal class VolumePanelSession {
    var state = VolumeInteractionState(); private set
    var dismissing = false; private set
    private var level: Int? = null

    fun show(nativeLevel: Int?) {
        if (dismissing) event(VolumeInteractionEvent.HIDE)
        if (!state.visible) level = nativeLevel
        state = VolumeInteractionModel.transition(state, VolumeInteractionEvent.SHOW)
    }

    fun event(event: VolumeInteractionEvent) {
        state = VolumeInteractionModel.transition(state, event)
        if (event == VolumeInteractionEvent.HIDE) { level = null; dismissing = false }
    }

    /** Dismiss is still visible: retain the current pose until OEM actually hides the panel. */
    fun dismiss() { if (state.visible) dismissing = true }

    /** The initial level seeds a wide session. Repeated frames of that level stay wide. */
    fun sliderUpdate(nativeLevel: Int?): Boolean {
        if (!state.visible || dismissing || nativeLevel == null) return false
        val changed = level != null && level != nativeLevel
        level = nativeLevel
        if (!changed || state.touching) return false
        event(VolumeInteractionEvent.HARDWARE_KEY)
        return true
    }
}
