// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import org.junit.Assert.*
import org.junit.Test

class NotificationOwnershipTest {
    @Test fun releaseRestoresOnlyTheLastOwnedWriteAndOnlyOnce() {
        var value = 1f
        var writes = 0
        val owned = OwnedValue(value)
        value = .8f; owned.record(value)
        owned.release(value) { value = it; writes++ }
        owned.release(.8f) { value = it; writes++ }
        assertEquals(1f, value, 0f)
        assertEquals(1, writes)
    }

    @Test fun newerHmcOrOemValueSurvivesLateCleanup() {
        val owned = OwnedValue(1f)
        owned.record(.8f)
        var value = .37f
        owned.release(value) { value = it }
        assertEquals(.37f, value, 0f)
    }

    @Test fun composedTiltRestoresLatestNativeBaseAndUntouchedPropertiesStayUntouched() {
        val owned = OwnedValue(1f)
        owned.record(.96f)
        owned.rebase(.8f)
        owned.record(.768f)
        var value = .768f
        owned.release(value) { value = it }
        assertEquals(.8f, value, 0f)
        val untouched = OwnedValue(1f)
        untouched.release(.4f) { fail("Never-written property was restored") }
    }

    @Test fun partialWriteFailureRestoresCommittedPropertiesOnly() {
        val x = OwnedValue(1f)
        val y = OwnedValue(1f)
        var currentX = .6f
        x.record(currentX)
        // The second setter throws before commit; its ownership was never recorded.
        x.release(currentX) { currentX = it }
        y.release(1f) { fail("Failed setter must not claim a property") }
        assertEquals(1f, currentX, 0f)
    }

    @Test fun reentrantCancelDeliversEndOnceAfterBothStartCallbacks() {
        val calls = mutableListOf<String>()
        val completion = Completion { calls += "end" }
        completion.defer {
            calls += "start"
            completion.finish() // A synchronous scene change or detach during onStart.
            completion.finish()
            calls += "external-start"
        }
        completion.finish() // Animator end after cancellation.
        assertEquals(listOf("start", "external-start", "end"), calls)
    }
}
