// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

import org.junit.Assert.*
import org.junit.Test

class NotificationLifecycleTransitionsTest {
    @Test fun screenOnlyWakeMessagesCreateASettledBoundary() {
        assertTrue(NotificationLifecycleTransitions.changes(1, screen = 1, wakefulness = 2))
        assertFalse(NotificationLifecycleTransitions.changes(1, screen = 2, wakefulness = 2))
        assertTrue(NotificationLifecycleTransitions.changes(2, screen = 2, wakefulness = 2))
        assertFalse(NotificationLifecycleTransitions.changes(2, screen = 3, wakefulness = 2))
    }

    @Test fun wakeSleepTransitionsAndDuplicateMessagesRemainDistinct() {
        assertTrue(NotificationLifecycleTransitions.changes(4, screen = 0, wakefulness = 0))
        assertFalse(NotificationLifecycleTransitions.changes(4, screen = 0, wakefulness = 1))
        assertTrue(NotificationLifecycleTransitions.changes(5, screen = 2, wakefulness = 1))
        assertFalse(NotificationLifecycleTransitions.changes(5, screen = 2, wakefulness = 2))
        assertTrue(NotificationLifecycleTransitions.changes(6, screen = 2, wakefulness = 2))
        assertFalse(NotificationLifecycleTransitions.changes(6, screen = 2, wakefulness = 3))
        assertTrue(NotificationLifecycleTransitions.changes(7, screen = 0, wakefulness = 3))
        assertFalse(NotificationLifecycleTransitions.changes(7, screen = 0, wakefulness = 0))
    }

    @Test fun unknownStatesAreConservativeButUnrelatedMessagesAreNotTransitions() {
        assertTrue(NotificationLifecycleTransitions.changes(1, null, null))
        assertTrue(NotificationLifecycleTransitions.changes(5, null, null))
        assertFalse(NotificationLifecycleTransitions.changes(99, null, null))
    }
}
