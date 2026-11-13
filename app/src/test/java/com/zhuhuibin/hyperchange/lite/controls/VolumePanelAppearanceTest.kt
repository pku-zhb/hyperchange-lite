// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import org.junit.Assert.*
import org.junit.Test

class VolumePanelAppearanceTest {
    @Test fun narrowPanelHidesRingerWithoutRequiringASecondSwitch() {
        assertTrue(VolumePanelAppearance.hideRinger(false, false, narrow = true, explicitHide = false))
        assertFalse(VolumePanelAppearance.hideRinger(false, false, narrow = false, explicitHide = false))
        assertTrue(VolumePanelAppearance.hideRinger(false, false, narrow = false, explicitHide = true))
    }

    @Test fun expandedAndControlCenterPanelsKeepNativeButtons() {
        for ((controlCenter, expanded) in listOf(true to false, false to true, true to true)) {
            assertFalse(VolumePanelAppearance.hideRinger(controlCenter, expanded, narrow = true, explicitHide = true))
            assertFalse(VolumePanelAppearance.hideExpand(controlCenter, expanded, longPress = true))
        }
        // Narrow appearance alone must not remove the only explicit expansion action.
        assertFalse(VolumePanelAppearance.hideExpand(false, false, longPress = false))
        assertTrue(VolumePanelAppearance.hideExpand(false, false, longPress = true))
    }

    @Test fun automaticFooterFollowsTheCurrentSessionPhase() {
        var state = VolumeInteractionState()
        for ((event, hidden) in listOf(
            VolumeInteractionEvent.SHOW to false,
            VolumeInteractionEvent.HARDWARE_KEY to true,
            VolumeInteractionEvent.TOUCH_DOWN to false,
            VolumeInteractionEvent.TOUCH_CANCEL to true,
            VolumeInteractionEvent.HIDE to false,
            VolumeInteractionEvent.SHOW to false,
        )) {
            state = VolumeInteractionModel.transition(state, event)
            assertEquals(hidden, VolumePanelAppearance.hideRinger(false, false, state.narrow, explicitHide = false))
            assertTrue(VolumePanelAppearance.hideRinger(false, false, state.narrow, explicitHide = true))
        }
    }

    @Test fun repeatedRefreshPreservesTheNativeVisibilityForDisable() {
        val visibility = VolumeButtonVisibility()
        assertEquals(8, visibility.resolve(0, hide = true, nativeUpdated = true, hidden = 8))
        assertEquals(8, visibility.resolve(8, hide = true, nativeUpdated = true, hidden = 8))
        assertEquals(0, visibility.resolve(8, hide = false, nativeUpdated = false, hidden = 8))
        // A native INVISIBLE value must not become VISIBLE when the option is disabled.
        assertEquals(8, visibility.resolve(4, hide = true, nativeUpdated = true, hidden = 8))
        assertEquals(4, visibility.resolve(8, hide = false, nativeUpdated = false, hidden = 8))
    }

    @Test fun nativeExpansionCanReplaceTheHiddenVisibilityBeforeCleanup() {
        val visibility = VolumeButtonVisibility()
        visibility.resolve(4, hide = true, nativeUpdated = true, hidden = 8)
        // OEM already chose VISIBLE for the expanded panel; do not restore the stale 4.
        assertEquals(0, visibility.resolve(0, hide = false, nativeUpdated = false, hidden = 8))
        assertEquals(0, visibility.resolve(0, hide = false, nativeUpdated = false, hidden = 8))
    }
}
