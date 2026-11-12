// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

import android.view.View
import org.junit.Assert.*
import org.junit.Test

class NotificationScopeDecisionTest {
    private fun snapshot(role: NotificationRole, allowed: Boolean = true) = NotificationScopeSnapshot(
        role, NotificationScopeToken(0, role), allowed, NotificationScope.generation)

    @Test fun preparedCallsShareTheirDecisionOnlyUntilTheBoundaryReturns() {
        val view = View(null)
        val current = snapshot(NotificationRole.MEDIA)
        NotificationScope.withSnapshot(view, current) {
            assertEquals(NotificationRole.MEDIA, NotificationScope.role(view))
            assertTrue(NotificationScope.sceneAllows(view))
            assertEquals(current.token, NotificationScope.token(view))
        }
        // A plain view is not media. Do not retain the prepared classification for native work.
        assertEquals(NotificationRole.ORDINARY, NotificationScope.role(view))
    }

    @Test fun nestedPreparationAndExceptionsRestoreThePreviousCaller() {
        val first = View(null)
        val second = View(null)
        NotificationScope.withSnapshot(first, snapshot(NotificationRole.MEDIA)) {
            try {
                NotificationScope.withSnapshot(second, snapshot(NotificationRole.HEADS_UP, false)) {
                    assertEquals(NotificationRole.HEADS_UP, NotificationScope.role(second))
                    assertFalse(NotificationScope.sceneAllows(second))
                    assertEquals(NotificationRole.ORDINARY, NotificationScope.role(first))
                    error("preparation failed")
                }
            } catch (expected: IllegalStateException) {
                assertEquals("preparation failed", expected.message)
            }
            assertEquals(NotificationRole.MEDIA, NotificationScope.role(first))
        }
        assertEquals(NotificationRole.ORDINARY, NotificationScope.role(first))
        assertEquals(NotificationRole.ORDINARY, NotificationScope.role(second))
    }

    @Test fun anExpiredRevisionCannotGrantItsPreparedRole() {
        val view = View(null)
        val old = snapshot(NotificationRole.MEDIA).copy(revision = NotificationScope.generation - 1)
        NotificationScope.withSnapshot(view, old) {
            assertEquals(NotificationRole.ORDINARY, NotificationScope.role(view))
        }
    }
}
