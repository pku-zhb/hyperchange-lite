package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class MaterialCallMemoTest {
    @Test fun nestedSettersAndPaletteAddsShareOneDecisionUntilTheOperationReturns() {
        val memo = MaterialCallMemo<Any, Int, Long>()
        val view = Any()
        var captures = 0
        memo.within {
            repeat(5) { memo.within { assertEquals(1, memo.get(view, 1L) { ++captures }) } }
        }
        assertEquals(1, captures)
        memo.within { assertEquals(2, memo.get(view, 1L) { ++captures }) }
    }

    @Test fun sceneOrConfigurationRoundTripCannotReuseThePreviousPermission() {
        data class Stamp(val configuration: Long, val scene: Long)
        val memo = MaterialCallMemo<Any, Boolean, Stamp>()
        val view = Any()
        var captures = 0
        memo.within {
            assertTrue(memo.get(view, Stamp(1, 1)) { captures++; true })
            assertFalse(memo.get(view, Stamp(1, 2)) { captures++; false })
            assertFalse(memo.get(view, Stamp(2, 2)) { captures++; false })
            assertTrue(memo.get(view, Stamp(2, 3)) { captures++; true })
        }
        assertEquals(4, captures)
    }

    @Test fun equalObjectsStillHaveSeparateOwnershipIdentities() {
        data class Owner(val name: String)
        val memo = MaterialCallMemo<Owner, Int, Long>()
        val first = Owner("row")
        val second = Owner("row")
        memo.within {
            assertEquals(1, memo.get(first, 1L) { 1 })
            assertEquals(2, memo.get(second, 1L) { 2 })
            assertEquals(1, memo.get(first, 1L) { error("must reuse first") })
        }
    }

    @Test fun throwingOperationReleasesItsFrameAndTheNextCallReadsFreshValues() {
        val memo = MaterialCallMemo<Any, String, Long>()
        val view = Any()
        try {
            memo.within {
                memo.get(view, 1L) { "old" }
                error("OEM setter failed")
            }
        } catch (expected: IllegalStateException) {
            assertEquals("OEM setter failed", expected.message)
        }
        memo.within { assertEquals("new", memo.get(view, 1L) { "new" }) }
    }

    @Test fun delayedCallbacksAndUnscopedReadsNeverInheritThePreviousCall() {
        val memo = MaterialCallMemo<Any, Int, Long>()
        val view = Any()
        var captures = 0
        val deferred = memo.within {
            memo.get(view, 1L) { ++captures }
            val callback: () -> Int = { memo.within { memo.get(view, 1L) { ++captures } } }
            callback
        }
        assertEquals(2, deferred())
        assertEquals(3, memo.get(view, 1L) { ++captures })
        assertEquals(4, memo.get(view, 1L) { ++captures })
    }

    @Test fun postNativeReadSeesLiveRoleChangesEvenWithoutAGenerationSignal() {
        val memo = MaterialCallMemo<Any, Boolean, Long>()
        val view = Any()
        var headsUp = false
        memo.within {
            assertFalse(memo.get(view, 1L) { headsUp })
            assertFalse(memo.get(view, 1L) { error("same preparation") })
        }
        // The native call is deliberately outside the pure preparation frame.
        headsUp = true
        assertNull(memo.scoped(view, 1L) { error("native boundary retained a frame") })
        assertTrue(memo.get(view, 1L) { headsUp })
    }

    @Test fun reparentedOwnerCanInvalidateOnlyItsOwnDecision() {
        val memo = MaterialCallMemo<Any, String, Long>()
        val moved = Any()
        val other = Any()
        memo.within {
            memo.get(moved, 1L) { "old parent" }
            memo.get(other, 1L) { "unchanged" }
            memo.forget(moved)
            assertEquals("new parent", memo.get(moved, 1L) { "new parent" })
            assertEquals("unchanged", memo.get(other, 1L) { error("another row was invalidated") })
        }
    }
}
