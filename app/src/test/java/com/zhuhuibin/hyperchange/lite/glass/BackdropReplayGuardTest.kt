// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class BackdropReplayGuardTest {
    @Test fun nativeClearsRemainAllowedEvenWhileNoPanelIsOpen() {
        var revision = 0L
        val guard = BackdropReplayGuard { revision }
        assertFalse(guard.shouldSkip(active = false))
        guard.duringReplay { assertTrue(guard.shouldSkip(active = false)) }
        revision++
        assertFalse(guard.shouldSkip(active = false))
    }

    @Test fun closingAndReopeningCannotResurrectThePreviousWindowSnapshot() {
        var revision = 0L
        var active = true
        val guard = BackdropReplayGuard { revision }
        val applied = mutableListOf<Int>()
        fun apply(radius: Int) { if (!guard.shouldSkip(active)) applied += radius }
        guard.duringReplay {
            apply(30)
            active = false
            revision++
            apply(50)
            active = true
            revision++
            apply(60)
        }
        // A new native frame and a new replay can use the new panel session.
        apply(0)
        guard.duringReplay { apply(20) }
        assertEquals(listOf(30, 0, 20), applied)
    }

    @Test fun nestedReplayAndExceptionsRestoreTheOuterRevisionAndNativeDispatch() {
        var revision = 0L
        val guard = BackdropReplayGuard { revision }
        guard.duringReplay {
            revision++
            assertTrue(guard.shouldSkip(active = true))
            assertThrows(IllegalStateException::class.java) {
                guard.duringReplay {
                    assertFalse(guard.shouldSkip(active = true))
                    throw IllegalStateException("native callback failed")
                }
            }
            assertTrue(guard.shouldSkip(active = true))
        }
        assertFalse(guard.shouldSkip(active = false))
    }
}
