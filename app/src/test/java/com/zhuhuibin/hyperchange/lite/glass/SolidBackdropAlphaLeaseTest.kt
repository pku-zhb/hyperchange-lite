// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class SolidBackdropAlphaLeaseTest {
    /** Equality must never substitute for the exact native owner/View/window/Drawable. */
    private class Node {
        override fun equals(other: Any?): Boolean = other is Node
        override fun hashCode(): Int = 1
    }
    private val owner = Node()
    private val view = Node()
    private val root = Node()
    private val background = Node()
    private fun lease(background: Node? = this.background) = SolidBackdropAlphaLease(owner, view, root, background)

    @Test fun activeOverrideWaitsAndRevocationRestoresFreshNativeStateOnce() {
        val lease = lease()
        var desiredNativeAlpha = .4f
        var alpha = 0f
        var calls = 0
        assertFalse(lease.retire(true, owner, view, root, background, true, alpha) { calls++ })
        assertFalse(lease.retired)
        desiredNativeAlpha = .7f
        assertTrue(lease.retire(false, owner, view, root, background, true, alpha) {
            assertSame(owner, it)
            calls++
            alpha = desiredNativeAlpha
            assertFalse(lease.retire(false, owner, view, root, background, true, 0f) { calls++ })
        })
        assertEquals(.7f, alpha, 0f)
        assertEquals(1, calls)
        assertTrue(lease.retired)
        assertFalse(lease.retire(false, owner, view, root, background, true, 0f) { calls++ })
        assertEquals(1, calls)
    }

    @Test fun newerNativeAlphaRetiresTheClaimWithoutWriting() {
        val lease = lease()
        var calls = 0
        assertFalse(lease.retire(false, owner, view, root, background, true, .3f) { calls++ })
        assertTrue(lease.retired)
        // A later native zero is not ours and cannot reactivate this old claim.
        assertFalse(lease.retire(false, owner, view, root, background, true, 0f) { calls++ })
        assertEquals(0, calls)
    }

    @Test fun replacedOwnerViewRootOrDrawableCannotReceiveARelease() {
        val original = listOf(owner, view, root, background)
        for (changed in original.indices) {
            val current = original.toMutableList().apply { this[changed] = Node() }
            assertEquals(original[changed], current[changed])
            assertNotSame(original[changed], current[changed])
            val lease = lease()
            var calls = 0
            assertFalse(lease.retire(false, current[0], current[1], current[2], current[3], true, 0f) { calls++ })
            assertTrue(lease.retired)
            assertEquals(0, calls)
        }
    }

    @Test fun aNativeNullBackgroundMustRemainNullToRelease() {
        val untouched = lease(null)
        var calls = 0
        assertTrue(untouched.retire(false, owner, view, root, null, true, 0f) { calls++ })
        val replaced = lease(null)
        assertFalse(replaced.retire(false, owner, view, root, background, true, 0f) { calls++ })
        assertEquals(1, calls)
        assertFalse(lease().retire(false, owner, view, root, null, true, 0f) { calls++ })
    }

    @Test fun detachCannotQueueAReleaseForLaterReattachment() {
        val lease = lease()
        var calls = 0
        assertFalse(lease.retire(false, owner, view, root, background, false, 0f) { calls++ })
        assertFalse(lease.retire(false, owner, view, Node(), background, true, 0f) { calls++ })
        assertEquals(0, calls)
    }

    @Test fun aNativeFailureConsumesTheOldClaimBeforeTheCallback() {
        val lease = lease()
        val error = IllegalStateException("native")
        var calls = 0
        try {
            lease.retire(false, owner, view, root, background, true, 0f) {
                calls++
                throw error
            }
            fail("Expected native failure")
        } catch (actual: IllegalStateException) { assertSame(error, actual) }
        assertTrue(lease.retired)
        assertFalse(lease.retire(false, owner, view, root, background, true, 0f) { calls++ })
        assertEquals(1, calls)
    }

    @Test fun anActiveButSupersededClaimCannotReleaseAfterAnotherClose() {
        val lease = lease()
        var calls = 0
        assertFalse(lease.retire(true, owner, view, Node(), background, true, 0f) { calls++ })
        assertTrue(lease.retired)
        assertFalse(lease.retire(false, owner, view, root, background, true, 0f) { calls++ })
        assertEquals(0, calls)
    }
}
