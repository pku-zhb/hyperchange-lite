// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import org.junit.Assert.*
import org.junit.Test

class VolumePanelSessionTest {
    @org.junit.Test fun dismissRetainsTheNarrowPoseUntilNativeHide() {
        val session = VolumePanelSession()
        session.show(5)
        session.sliderUpdate(6)
        session.dismiss()
        org.junit.Assert.assertTrue(session.dismissing)
        org.junit.Assert.assertTrue(session.state.visible)
        org.junit.Assert.assertTrue(session.state.narrow)
        org.junit.Assert.assertFalse(session.sliderUpdate(7))
        session.event(VolumeInteractionEvent.HIDE)
        org.junit.Assert.assertFalse(session.state.visible)
        org.junit.Assert.assertFalse(session.dismissing)
    }

    @org.junit.Test fun aShowInterruptingDismissStartsWideWithANewLevelSeed() {
        val session = VolumePanelSession()
        session.show(5)
        session.sliderUpdate(6)
        session.dismiss()
        session.show(9)
        org.junit.Assert.assertFalse(session.dismissing)
        org.junit.Assert.assertFalse(session.state.narrow)
        org.junit.Assert.assertFalse(session.sliderUpdate(9))
        org.junit.Assert.assertTrue(session.sliderUpdate(10))
    }
    @Test fun ordinaryLevelChangeNarrowsWithoutAnEndpointKeyAnimation() {
        val session = VolumePanelSession()
        session.show(5)
        repeat(30) { assertFalse(session.sliderUpdate(5)) }
        assertFalse(session.state.narrow)
        assertTrue(session.sliderUpdate(6))
        assertTrue(session.state.narrow)
        repeat(30) { assertFalse(session.sliderUpdate(6)) }
    }

    @Test fun missingInitialSnapshotSeedsOnFirstUpdateAndDuplicateShowKeepsIt() {
        val session = VolumePanelSession()
        session.show(null)
        assertFalse(session.sliderUpdate(5))
        session.show(5)
        assertFalse(session.state.narrow)
        assertTrue(session.sliderUpdate(4))
        session.show(4)
        assertTrue(session.state.narrow)
    }

    @Test fun draggingUpdatesTheNativeLevelWithoutStealingWidthAndReShowStartsWide() {
        val session = VolumePanelSession()
        session.show(5)
        session.event(VolumeInteractionEvent.TOUCH_DOWN)
        for (level in 6..10) assertFalse(session.sliderUpdate(level))
        assertTrue(session.state.touching)
        assertFalse(session.state.narrow)
        session.event(VolumeInteractionEvent.TOUCH_CANCEL)
        assertTrue(session.state.narrow)
        session.event(VolumeInteractionEvent.HIDE)
        assertFalse(session.sliderUpdate(11))
        session.show(11)
        assertFalse(session.state.narrow)
        assertFalse(session.sliderUpdate(11))
        assertTrue(session.sliderUpdate(12))
    }
}
