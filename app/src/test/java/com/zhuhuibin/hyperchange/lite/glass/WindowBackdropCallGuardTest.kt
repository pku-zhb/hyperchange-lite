// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class WindowBackdropCallGuardTest {
    private class Owner(var active: Boolean = true)

    @Test fun unrelatedBlurCallsAndEqualButDistinctRootsRemainNative() {
        val owner = Owner()
        val root = String(charArrayOf('r'))
        val equalRoot = String(charArrayOf('r'))
        val guard = WindowBackdropCallGuard<Owner>({ 1L }) { it.active }
        assertFalse(guard.allows(root))
        guard.during(owner, root) {
            assertTrue(guard.allows(root))
            assertEquals(root, equalRoot)
            assertFalse(guard.allows(equalRoot))
        }
        assertFalse(guard.allows(root))
    }

    @Test fun closeAodDetachOrGenerationChangeRevokesAnInFlightNativeCall() {
        val owner = Owner()
        val root = Any()
        var revision = 1L
        val guard = WindowBackdropCallGuard<Owner>({ revision }) { it.active }
        guard.during(owner, root) {
            assertTrue(guard.allows(root))
            owner.active = false
            assertFalse(guard.allows(root))
            owner.active = true
            revision++
            assertFalse(guard.allows(root))
        }
        guard.during(owner, root) { assertTrue(guard.allows(root)) }
    }

    @Test fun nestedIneligibleWorkCannotBorrowAnOuterWindowPermission() {
        val owner = Owner()
        val root = Any()
        val otherRoot = Any()
        val guard = WindowBackdropCallGuard<Owner>({ 1L }) { it.active }
        guard.during(owner, root) {
            guard.during(Owner(false), root) { assertFalse(guard.allows(root)) }
            assertTrue(guard.allows(root))
            guard.during(owner, otherRoot) {
                assertTrue(guard.allows(otherRoot))
                assertFalse(guard.allows(root))
            }
            assertTrue(guard.allows(root))
            guard.during(null, null) { assertFalse(guard.allows(root)) }
            assertTrue(guard.allows(root))
        }
    }

    @Test fun deniedOrBrokenProbeStillRunsNativeExactlyOnce() {
        val owner = Owner()
        val root = Any()
        var broken = false
        var executions = 0
        val guard = WindowBackdropCallGuard<Owner>({ 1L }) {
            if (broken) error("probe")
            it.active
        }
        guard.during(owner, root) {
            broken = true
            guard.during(owner, root) {
                executions++
                assertFalse(guard.allows(root))
            }
            assertFalse(guard.allows(root))
            broken = false
            assertTrue(guard.allows(root))
        }
        assertEquals(1, executions)
    }

    @Test fun aControllerCallDoesNotGrantPermissionOnAnotherThread() {
        val owner = Owner()
        val root = Any()
        val guard = WindowBackdropCallGuard<Owner>({ 1L }) { it.active }
        guard.during(owner, root) {
            var otherAllowed = true
            Thread { otherAllowed = guard.allows(root) }.also { it.start(); it.join() }
            assertFalse(otherAllowed)
            assertTrue(guard.allows(root))
        }
    }

    @Test fun nativeFailureDoesNotLeakPermissionToLaterCalls() {
        val owner = Owner()
        val root = Any()
        val guard = WindowBackdropCallGuard<Owner>({ 1L }) { it.active }
        val error = IllegalStateException("native")
        try { guard.during(owner, root) { throw error } }
        catch (actual: IllegalStateException) { assertSame(error, actual) }
        assertFalse(guard.allows(root))
    }

    @Test fun aReentrantScopeProbeCannotAdmitItsOldGeneration() {
        val owner = Owner()
        val root = Any()
        var revision = 1L
        var revoke = false
        val guard = WindowBackdropCallGuard<Owner>({ revision }) {
            if (revoke) revision++
            it.active
        }
        guard.during(owner, root) {
            revoke = true
            assertFalse(guard.allows(root))
        }
        guard.during(owner, root) { assertFalse(guard.allows(root)) }
    }
}
