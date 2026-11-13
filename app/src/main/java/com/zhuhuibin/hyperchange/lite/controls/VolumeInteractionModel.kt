// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

/** A visible session starts wide; only another key or a completed touch narrows it. */
data class VolumeInteractionState(
    val visible: Boolean = false,
    val touching: Boolean = false,
    val narrow: Boolean = false,
)

enum class VolumeInteractionEvent { SHOW, HARDWARE_KEY, TOUCH_DOWN, TOUCH_UP, TOUCH_CANCEL, HIDE }

/** Shared by the OEM adapter and preview; configuration refresh is not an input event. */
object VolumeInteractionModel {
    fun transition(state: VolumeInteractionState, event: VolumeInteractionEvent): VolumeInteractionState = when (event) {
        VolumeInteractionEvent.SHOW -> if (state.visible) state else VolumeInteractionState(visible = true)
        VolumeInteractionEvent.HARDWARE_KEY -> if (state.visible && !state.touching) state.copy(narrow = true) else state
        VolumeInteractionEvent.TOUCH_DOWN -> VolumeInteractionState(visible = true, touching = true)
        VolumeInteractionEvent.TOUCH_UP, VolumeInteractionEvent.TOUCH_CANCEL ->
            if (state.visible && state.touching) state.copy(touching = false, narrow = true) else state
        VolumeInteractionEvent.HIDE -> VolumeInteractionState()
    }
}
