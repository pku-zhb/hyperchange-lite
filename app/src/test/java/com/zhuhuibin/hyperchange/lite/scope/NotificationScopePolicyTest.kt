// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

import org.junit.Assert.*
import org.junit.Test

class NotificationScopePolicyTest {
    private val unlocked = NotificationScene(false, false, false, 0)

    @Test fun ordinaryNotificationsRequireEveryUnlockedSignalWhenScopeIsOff() {
        assertTrue(NotificationScopePolicy.allows(NotificationRole.ORDINARY, false, unlocked))
        for (scene in listOf(
            NotificationScene(), unlocked.copy(showing = true), unlocked.copy(showing = null),
            unlocked.copy(dozing = true), unlocked.copy(dozing = null),
            unlocked.copy(transitioning = true), unlocked.copy(transitioning = null),
            unlocked.copy(statusBarState = 1), unlocked.copy(statusBarState = 2), unlocked.copy(statusBarState = null),
        )) assertFalse("Unconfirmed scene $scene", NotificationScopePolicy.allows(NotificationRole.ORDINARY, false, scene))
    }

    @Test fun lockscreenListAndAodFollowScopeWhileMediaAndHeadsUpRemainIndependent() {
        for (scene in listOf(NotificationScene(), unlocked, NotificationScene(true, false, false, 2), NotificationScene(true, true, false, 1))) {
            assertTrue(NotificationScopePolicy.allows(NotificationRole.ORDINARY, true, scene))
            for (role in listOf(NotificationRole.MEDIA, NotificationRole.HEADS_UP)) {
                assertTrue(NotificationScopePolicy.allows(role, false, scene))
                assertTrue(NotificationScopePolicy.allows(role, true, scene))
            }
        }
    }

    @Test fun delayedOrdinaryWorkCannotSurviveLockThenUnlockRoundTrip() {
        val queued = NotificationScopeToken(5, NotificationRole.ORDINARY, 2)
        assertTrue(queued.isCurrent(5, NotificationRole.ORDINARY, 2))
        assertFalse(queued.isCurrent(6, NotificationRole.ORDINARY, 2))
        assertFalse(queued.isCurrent(7, NotificationRole.ORDINARY, 2))
        assertTrue(NotificationScopeToken(7, NotificationRole.ORDINARY, 2).isCurrent(7, NotificationRole.ORDINARY, 2))
    }

    @Test fun roleRoundTripExpiresWorkButUnrelatedRowsDoNot() {
        val queued = NotificationScopeToken(0, NotificationRole.HEADS_UP, 4)
        assertFalse(queued.isCurrent(0, NotificationRole.ORDINARY, 5))
        assertFalse(queued.isCurrent(0, NotificationRole.HEADS_UP, 6))
        // A different row's role counter and ordinary scene epoch do not enter this token.
        assertTrue(queued.isCurrent(0, NotificationRole.HEADS_UP, 4))
    }
}
