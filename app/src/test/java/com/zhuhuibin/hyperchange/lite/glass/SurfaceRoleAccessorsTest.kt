// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class SurfaceRoleAccessorsTest {
    class CurrentRow {
        var headsUp = false
        var reads = 0
        var source: Any? = null
        fun isHeadsUpState(): Boolean { reads++; return headsUp }
        fun isHeadsUp(): Boolean = error("legacy getter must not run when primary exists")
        fun getInjector(): Any? = source
    }
    class LegacyRow {
        var headsUp = false
        fun isHeadsUp(): Boolean = headsUp
    }
    class FocusOwner {
        var focus = false
        fun isFocusNotification(): Boolean = focus
    }
    class OtherFocusOwner {
        fun isFocusNotification(): Boolean = true
    }
    class UnknownRow
    class UnknownInjector
    class TemporarilyFailingRow {
        var failing = true
        fun isHeadsUpState(): Boolean { check(!failing); return true }
        fun isHeadsUp(): Boolean = true
    }

    private val resolutions = mutableMapOf<Pair<Class<*>, String>, Int>()
    private fun accessors() = SurfaceRoleAccessors { type, name ->
        val key = type to name
        resolutions[key] = (resolutions[key] ?: 0) + 1
        runCatching { type.getMethod(name) }.getOrNull()
    }
    private fun lookups(type: Class<*>, name: String) = resolutions[type to name] ?: 0

    @Test fun primaryFalseDoesNotProbeTheLegacyNameAndCurrentRoleRemainsLive() {
        val accessors = accessors()
        val row = CurrentRow()
        repeat(100) { assertEquals(false, accessors.headsUp(row)) }
        row.headsUp = true
        assertEquals(true, accessors.headsUp(row))
        row.headsUp = false
        assertEquals(false, accessors.headsUp(row))
        assertEquals(102, row.reads)
        assertEquals(1, lookups(CurrentRow::class.java, "isHeadsUpState"))
        assertEquals(0, lookups(CurrentRow::class.java, "isHeadsUp"))
    }

    @Test fun missingPrimaryIsResolvedOnceAndLegacyGetterStillReadsEachInstance() {
        val accessors = accessors()
        val first = LegacyRow()
        val second = LegacyRow().apply { headsUp = true }
        repeat(100) {
            assertEquals(false, accessors.headsUp(first))
            assertEquals(true, accessors.headsUp(second))
        }
        assertEquals(1, lookups(LegacyRow::class.java, "isHeadsUpState"))
        assertEquals(1, lookups(LegacyRow::class.java, "isHeadsUp"))
    }

    @Test fun missingRowAndInjectorMethodsAreNegativeCachedWithoutGrantingARole() {
        val accessors = accessors()
        val unknown = UnknownRow()
        val row = CurrentRow().apply { source = UnknownInjector() }
        repeat(100) {
            assertNull(accessors.headsUp(unknown))
            assertNull(accessors.focus(unknown))
            assertNull(accessors.focus(row))
        }
        for (name in listOf("isHeadsUpState", "isHeadsUp", "getInjector"))
            assertEquals(1, lookups(UnknownRow::class.java, name))
        assertEquals(1, lookups(UnknownInjector::class.java, "isFocusNotification"))
    }

    @Test fun focusReadsCurrentInjectorAndValueWhileResolvingEachClassOnlyOnce() {
        val accessors = accessors()
        val owner = FocusOwner()
        val row = CurrentRow().apply { source = owner }
        assertEquals(false, accessors.focus(row))
        owner.focus = true
        repeat(20) { assertEquals(true, accessors.focus(row)) }
        row.source = OtherFocusOwner()
        assertEquals(true, accessors.focus(row))
        row.source = null
        assertNull(accessors.focus(row))
        row.source = owner.apply { focus = false }
        assertEquals(false, accessors.focus(row))
        assertEquals(1, lookups(CurrentRow::class.java, "getInjector"))
        assertEquals(1, lookups(FocusOwner::class.java, "isFocusNotification"))
        assertEquals(1, lookups(OtherFocusOwner::class.java, "isFocusNotification"))
    }

    @Test fun getterFailureStaysUnknownWithoutFallbackOrCachingTheFailedValue() {
        val accessors = accessors()
        val row = TemporarilyFailingRow()
        assertNull(accessors.headsUp(row))
        row.failing = false
        assertEquals(true, accessors.headsUp(row))
        assertEquals(0, lookups(TemporarilyFailingRow::class.java, "isHeadsUp"))
        assertEquals(1, lookups(TemporarilyFailingRow::class.java, "isHeadsUpState"))
    }
}
