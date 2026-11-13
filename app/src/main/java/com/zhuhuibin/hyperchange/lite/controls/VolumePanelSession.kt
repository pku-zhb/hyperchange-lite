// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

/** Slider progress animates every frame; only a new discrete native level is an input update. */
internal class VolumePanelSession {
    var state = VolumeInteractionState(); private set
    private var level: Int? = null

    fun show(nativeLevel: Int?) {
        if (!state.visible) level = nativeLevel
        state = VolumeInteractionModel.transition(state, VolumeInteractionEvent.SHOW)
    }

    fun event(event: VolumeInteractionEvent) {
        state = VolumeInteractionModel.transition(state, event)
        if (event == VolumeInteractionEvent.HIDE) level = null
    }

    /** The initial level seeds a wide session. Repeated frames of that level stay wide. */
    fun sliderUpdate(nativeLevel: Int?): Boolean {
        if (!state.visible || nativeLevel == null) return false
        val changed = level != null && level != nativeLevel
        level = nativeLevel
        if (!changed || state.touching) return false
        event(VolumeInteractionEvent.HARDWARE_KEY)
        return true
    }
}
