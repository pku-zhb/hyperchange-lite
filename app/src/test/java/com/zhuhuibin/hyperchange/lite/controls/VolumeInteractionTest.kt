// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import org.junit.Assert.*
import org.junit.Test

class VolumeInteractionTest {
    private fun VolumeInteractionState.on(event: VolumeInteractionEvent) = VolumeInteractionModel.transition(this, event)

    @Test fun initialKeyThenShowStaysWideAndSubsequentKeysNarrow() {
        var state = VolumeInteractionState().on(VolumeInteractionEvent.HARDWARE_KEY)
        assertFalse(state.visible)
        state = state.on(VolumeInteractionEvent.SHOW)
        assertEquals(VolumeInteractionState(visible = true), state)
        state = state.on(VolumeInteractionEvent.HARDWARE_KEY)
        assertTrue(state.narrow)
        assertEquals(state, state.on(VolumeInteractionEvent.HARDWARE_KEY))
        // Duplicate native show callbacks must not reset a live narrow session.
        assertEquals(state, state.on(VolumeInteractionEvent.SHOW))
    }

    @Test fun touchOwnsWidthUntilUpOrCancellation() {
        val narrow = VolumeInteractionState().on(VolumeInteractionEvent.SHOW).on(VolumeInteractionEvent.HARDWARE_KEY)
        val touching = narrow.on(VolumeInteractionEvent.TOUCH_DOWN)
        assertTrue(touching.touching)
        assertFalse(touching.narrow)
        assertEquals(touching, touching.on(VolumeInteractionEvent.HARDWARE_KEY))
        for (end in listOf(VolumeInteractionEvent.TOUCH_UP, VolumeInteractionEvent.TOUCH_CANCEL)) {
            val released = touching.on(end)
            assertFalse(released.touching)
            assertTrue(released.narrow)
            assertEquals(released, released.on(end))
        }
    }

    @Test fun hidingOrRestoringClearsTouchAndNextShowIsWide() {
        for (active in listOf(VolumeInteractionState(true, false, true), VolumeInteractionState(true, true, false))) {
            val hidden = active.on(VolumeInteractionEvent.HIDE)
            assertEquals(VolumeInteractionState(), hidden)
            assertEquals(hidden, hidden.on(VolumeInteractionEvent.TOUCH_CANCEL))
            assertEquals(VolumeInteractionState(visible = true), hidden.on(VolumeInteractionEvent.SHOW))
        }
    }
}
