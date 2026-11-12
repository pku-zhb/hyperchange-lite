// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import org.junit.Assert.*
import org.junit.Test

class NotificationMotionTest {
    @Test fun recoveredSpringGoldenValues() {
        // Independent golden samples from the observed damped response equation.
        assertEquals(.7928373f, NotificationMotion.spring(.25f, .72f, 9f), .000001f)
        assertEquals(1.0383969f, NotificationMotion.spring(.5f, .72f, 9f), .000001f)
        assertEquals(1.1656378f, NotificationMotion.spring(.5f, .48f, 8f), .000001f)
        assertEquals(1.296917f, NotificationMotion.spring(.5f, .34f, 7.5f), .000001f)
        assertEquals(1.0945349f, NotificationMotion.spring(.5f, .6f, 8f), .000001f)
        assertEquals(0f, NotificationMotion.spring(0f, .6f, 8f), 0f)
        assertEquals(1f, NotificationMotion.spring(1f, .6f, 8f), 0f)
    }

    @Test fun springOvershootIsKeptForTapRelease() {
        assertTrue(NotificationMotion.spring(.5f, .34f, 7.5f) > 1f)
        assertTrue(NotificationMotion.spring(.5f, .34f, 7.5f) > NotificationMotion.spring(.5f, .72f, 9f))
    }

    @Test fun bezierSolvesTheXCoordinateInsteadOfTreatingItAsTime() {
        assertEquals(.5f, NotificationMotion.bezier(.5f, .25f, .25f, .75f, .75f), .000001f)
        assertTrue(NotificationMotion.bezier(.5f) > .9f)
        assertEquals(0f, NotificationMotion.bezier(0f), 0f)
        assertEquals(1f, NotificationMotion.bezier(1f), 0f)
    }

    @Test fun tiltDirectionAndReleaseAreNeutralAtRest() {
        val lowerRight = NotificationMotion.tilt(1f, 1f, 1f, 4f, .96f)
        assertEquals(-4f, lowerRight.rotationX, 0f)
        assertEquals(4f, lowerRight.rotationY, 0f)
        assertEquals(.96f, lowerRight.scale, .000001f)
        assertEquals(-2f, lowerRight.zDp, 0f)
        val rest = NotificationMotion.tilt(1f, 1f, 0f, 4f, .96f)
        assertEquals(0f, rest.rotationX, 0f)
        assertEquals(1f, rest.scale, 0f)
    }

    @Test fun nativeDismissAndMediaDragTakePriority() {
        assertFalse(NotificationMotion.releasesTilt(1f, 1f, false))
        assertTrue(NotificationMotion.releasesTilt(20f, 4f, false))
        assertFalse(NotificationMotion.releasesTilt(4f, 20f, false))
        assertTrue(NotificationMotion.releasesTilt(4f, 20f, true))
    }

    @Test fun collapsedGroupIsThreeCardLinearStack() {
        assertEquals(NotificationMotion.GroupFrame(8f, .98f), NotificationMotion.group(2, 4f, .99f))
        assertEquals(NotificationMotion.group(2, 4f, .99f), NotificationMotion.group(99, 4f, .99f))
        assertEquals(NotificationMotion.GroupFrame(0f, 1f), NotificationMotion.group(0, 6f, .95f))
    }

    @Test fun cancellationAndAnimationEndCannotDoubleCompleteRemoval() {
        var calls = 0
        val completion = Completion { calls++ }
        completion.finish() // Detach/cancel.
        completion.finish() // Animator end after cancel.
        completion.finish() // A replacement animation.
        assertEquals(1, calls)
    }
}
