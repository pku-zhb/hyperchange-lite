// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class BackdropWriteLeaseTest {
    private class Provider(var enabled: Boolean = true, var radius: Int = 120)
    private val owner = Provider()
    private val view = Any()
    private val root = Any()
    private fun lease() = BackdropWriteLease(owner, view, root)

    @Test fun aStillEnabledHeadsUpProviderReclaimsModeAndRadiusWhenShadeCloses() {
        val lease = lease()
        var mode = 0
        var radius = 0
        var calls = 0
        assertFalse(lease.retire(true, owner, view, root, true, true) { calls++ })
        // The OEM's enabled/ratio did not change, so its collector need not emit.
        owner.radius = 96
        assertTrue(lease.retire(false, owner, view, root, true, true) {
            calls++
            mode = if (it.enabled) 1 else 0
            radius = it.radius
            assertFalse(lease.retire(false, owner, view, root, true, true) { calls++ })
        })
        assertEquals(1, mode)
        assertEquals(96, radius)
        assertEquals(1, calls)
    }

    @Test fun releaseUsesTheOwnersCurrentDisabledStateRatherThanAnOldOne() {
        val lease = lease()
        owner.enabled = false
        owner.radius = 0
        var mode = 1
        assertTrue(lease.retire(false, owner, view, root, true, true) {
            mode = if (it.enabled) 1 else 0
            assertEquals(0, it.radius)
        })
        assertEquals(0, mode)
    }

    @Test fun aNewNativeZeroSupersedesOurOldZeroEvenWhenPixelsLookEqual() {
        val writes = MaterialWriteClock<Any>()
        val ticket = writes.ticket(view)
        val lease = lease()
        writes.touch(view)
        var calls = 0
        assertFalse(lease.retire(false, owner, view, root, true, writes.current(ticket)) { calls++ })
        assertTrue(lease.retired)
        assertFalse(lease.retire(false, owner, view, root, true, true) { calls++ })
        assertEquals(0, calls)
    }

    @Test fun aNewNativeNonzeroModeIsNeverOverwrittenByRelease() {
        val lease = lease()
        var calls = 0
        assertFalse(lease.retire(false, owner, view, root, true, false) { calls++ })
        assertTrue(lease.retired)
        assertEquals(0, calls)
    }

    @Test fun replacedProviderViewOrWindowCannotReceiveTheOldRelease() {
        var calls = 0
        assertFalse(lease().retire(false, Provider(), view, root, true, true) { calls++ })
        assertFalse(lease().retire(false, owner, Any(), root, true, true) { calls++ })
        assertFalse(lease().retire(false, owner, view, Any(), true, true) { calls++ })
        assertFalse(lease().retire(false, owner, view, null, true, true) { calls++ })
        assertEquals(0, calls)
    }

    @Test fun detachDiscardsTheClaimInsteadOfReplayingItAfterAttach() {
        val lease = lease()
        var calls = 0
        assertFalse(lease.retire(false, owner, view, root, false, true) { calls++ })
        assertFalse(lease.retire(false, owner, view, root, true, true) { calls++ })
        assertEquals(0, calls)
    }

    @Test fun replacingTheSurfaceInsideTheSameViewRootRetiresWithoutWriting() {
        val surface = Any()
        val lease = BackdropWriteLease(owner, view, root, surface)
        var calls = 0
        assertFalse(lease.retire(false, owner, view, root, true, true, Any(), true) { calls++ })
        assertTrue(lease.retired)
        assertEquals(0, calls)
    }

    @Test fun anInvalidOriginalSurfaceCannotStartTheNativeRetryLoop() {
        val surface = Any()
        val lease = BackdropWriteLease(owner, view, root, surface)
        var calls = 0
        assertFalse(lease.retire(false, owner, view, root, true, true, surface, false) { calls++ })
        assertTrue(lease.retired)
        assertEquals(0, calls)
    }

    @Test fun theSameValidSurfaceCanReceiveOneFreshNativeWindowDecision() {
        val surface = Any()
        val lease = BackdropWriteLease(owner, view, root, surface)
        var calls = 0
        assertTrue(lease.retire(false, owner, view, root, true, true, surface, true) { calls++ })
        assertFalse(lease.retire(false, owner, view, root, true, true, surface, true) { calls++ })
        assertEquals(1, calls)
    }

    @Test fun aCancelledBatchKeepsItsDeliveryResponsibilityUntilItCanRun() {
        val lease = lease()
        assertSame(owner, lease.candidate(false, owner, view, root, true, true))
        // Another owner's fresh batch changes the scene epoch before this batch runs.
        assertFalse(lease.retired)
        var calls = 0
        assertTrue(lease.retire(false, owner, view, root, true, true) { calls++ })
        assertTrue(lease.retired)
        assertEquals(1, calls)
    }

    @Test fun nativeSupersessionAfterCandidateSelectionPreventsDelivery() {
        val lease = lease()
        assertSame(owner, lease.candidate(false, owner, view, root, true, true))
        assertNull(lease.candidate(false, owner, view, root, true, false))
        assertTrue(lease.retired)
        assertFalse(lease.consume(owner))
    }

    @Test fun failedNativeReleaseCannotRetryAnOldClaim() {
        val lease = lease()
        val failure = IllegalStateException("native")
        try {
            lease.retire(false, owner, view, root, true, true) { throw failure }
            fail("Expected native failure")
        } catch (actual: IllegalStateException) { assertSame(failure, actual) }
        assertTrue(lease.retired)
        assertFalse(lease.retire(false, owner, view, root, true, true) { fail("Retried") })
    }
}
