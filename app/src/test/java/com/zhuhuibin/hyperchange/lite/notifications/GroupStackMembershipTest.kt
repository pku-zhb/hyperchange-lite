package com.zhuhuibin.hyperchange.lite.notifications

import org.junit.Assert.*
import org.junit.Test

class GroupStackMembershipTest {
    private data class Row(var parent: Any, val label: String = "same", var shown: Boolean = true)

    @Test fun equalCountReplacementCannotReuseOldRows() {
        val owner = Any()
        val a = Row(owner); val b = Row(owner)
        val snapshot = GroupStackMembership(listOf(a, b))
        assertTrue(snapshot.matches(listOf(a, b)) { it.parent === owner })
        assertFalse(snapshot.matches(listOf(a, Row(owner))) { it.parent === owner })
    }

    @Test fun nativeReorderInvalidatesTheOldStackWithoutChangingCount() {
        val owner = Any()
        val rows = List(3) { Row(owner) }
        val snapshot = GroupStackMembership(rows)
        assertFalse(snapshot.matches(listOf(rows[2], rows[0], rows[1])) { it.parent === owner })
        assertTrue(snapshot.matches(rows) { it.parent === owner })
    }

    @Test fun movingARowToAnotherGroupInvalidatesItsFormerOwner() {
        val owner = Any()
        val rows = List(2) { Row(owner) }
        val snapshot = GroupStackMembership(rows)
        rows[1].parent = Any()
        assertFalse(snapshot.matches(rows) { it.parent === owner })
    }

    @Test fun extraMembersAlsoParticipateInTheLifecycle() {
        val owner = Any()
        val rows = List(5) { Row(owner) }
        val snapshot = GroupStackMembership(rows)
        assertFalse(snapshot.matches(rows.take(4) + Row(owner)) { it.parent === owner })
        assertFalse(snapshot.matches(rows.take(3)) { it.parent === owner })
        assertFalse(snapshot.matches(rows + Row(owner)) { it.parent === owner })
    }

    @Test fun nativeFilteringCannotBeUndoneByAnOldVisibleStack() {
        val owner = Any()
        val rows = List(3) { Row(owner) }
        val snapshot = GroupStackMembership(rows) { it.shown }
        rows[0].shown = false
        assertFalse(snapshot.matches(rows) { it.parent === owner })
    }

    @Test fun nativeMeasurementAndGeometryRestoreIndependently() {
        val real = OwnedValue(300)
        val measured = OwnedValue(320)
        real.record(120); measured.record(120)
        var nativeReal = 120
        var nativeMeasured = 400 // OEM already remeasured the expanded group.
        real.release(nativeReal) { nativeReal = it }
        measured.release(nativeMeasured) { nativeMeasured = it }
        assertEquals(300, nativeReal)
        assertEquals(400, nativeMeasured)
        real.release(nativeReal) { error("A second release must not restore again") }
    }
}
