// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class NotificationAccessorCacheTest {
    private open class Base {
        @JvmField var changing = 1
        private val hidden = "base"
        private fun inherited(): String = hidden
        private fun overloaded(value: Int): String = "int:$value"
        private fun overloaded(value: String): String = "string:$value"
    }
    private class Child : Base() {
        private val hidden = "child"
        private fun fails(): Nothing = throw IllegalStateException("native failure")
    }
    private class Other { private val hidden = "other" }
    private class Positive : java.util.function.Predicate<Int> {
        override fun test(value: Int): Boolean = value > 0
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun inheritedPublicInterfaceDefaultsAreResolvedAndCached() {
        val cache = NotificationAccessorCache()
        val method = requireNotNull(cache.method(Positive::class.java, "negate", emptyArray()))
        assertTrue(method.isDefault)
        assertEquals(java.util.function.Predicate::class.java, method.declaringClass)
        val inverted = method.invoke(Positive()) as java.util.function.Predicate<Int>
        assertFalse(inverted.test(1))
        assertTrue(inverted.test(-1))
        repeat(100) { assertSame(method, cache.method(Positive::class.java, "negate", emptyArray())) }
    }

    @Test fun repeatedHitsAndMissingFieldsScanEachClassAndNameOnlyOnce() {
        val attempts = AtomicInteger()
        val cache = NotificationAccessorCache(declaredField = { type, name ->
            attempts.incrementAndGet()
            type.getDeclaredField(name)
        })
        val field = requireNotNull(cache.field(Child::class.java, "changing"))
        assertEquals(2, attempts.get())
        repeat(100) { assertSame(field, cache.field(Child::class.java, "changing")) }
        assertEquals(2, attempts.get())
        assertNull(cache.field(Child::class.java, "absent"))
        val afterMissing = attempts.get()
        assertEquals(5, afterMissing) // Child, Base, Object.
        repeat(100) { assertNull(cache.field(Child::class.java, "absent")) }
        assertEquals(afterMissing, attempts.get())
    }

    @Test fun exactMethodSignatureAndMissingOverloadsAreCachedIndependently() {
        val attempts = AtomicInteger()
        val cache = NotificationAccessorCache(declaredMethod = { type, name, parameters ->
            attempts.incrementAndGet()
            type.getDeclaredMethod(name, *parameters)
        })
        val integer = arrayOf<Class<*>>(Int::class.javaPrimitiveType!!)
        val text = arrayOf<Class<*>>(String::class.java)
        val missing = arrayOf<Class<*>>(Long::class.javaPrimitiveType!!)
        val intMethod = requireNotNull(cache.method(Child::class.java, "overloaded", integer))
        val stringMethod = requireNotNull(cache.method(Child::class.java, "overloaded", text))
        assertEquals("int:3", intMethod.invoke(Child(), 3))
        assertEquals("string:x", stringMethod.invoke(Child(), "x"))
        assertNull(cache.method(Child::class.java, "overloaded", missing))
        val count = attempts.get()
        repeat(100) {
            assertSame(intMethod, cache.method(Child::class.java, "overloaded", integer))
            assertSame(stringMethod, cache.method(Child::class.java, "overloaded", text))
            assertNull(cache.method(Child::class.java, "overloaded", missing))
        }
        assertEquals(count, attempts.get())
    }

    @Test fun lookupKeepsNearestDeclarationAndRuntimeClassIdentity() {
        val cache = NotificationAccessorCache()
        assertEquals("child", cache.field(Child::class.java, "hidden")!!.get(Child()))
        assertEquals("base", cache.field(Base::class.java, "hidden")!!.get(Base()))
        assertEquals("other", cache.field(Other::class.java, "hidden")!!.get(Other()))
        assertEquals("base", cache.method(Child::class.java, "inherited", emptyArray())!!.invoke(Child()))
    }

    @Suppress("DEPRECATION")
    @Test fun accessibilityIsRestoredWithoutCachingValuesOrInvocationResults() {
        val cache = NotificationAccessorCache()
        val row = Child()
        val field = requireNotNull(cache.field(Child::class.java, "changing"))
        assertEquals(1, field.get(row))
        row.changing = 8
        field.isAccessible = false
        assertEquals(8, cache.field(Child::class.java, "changing")!!.get(row))
        val method = requireNotNull(cache.method(Child::class.java, "inherited", emptyArray()))
        method.isAccessible = false
        assertEquals("base", cache.method(Child::class.java, "inherited", emptyArray())!!.invoke(row))
    }

    @Test fun parameterArrayMutationDoesNotRewriteAnExistingCacheKey() {
        val cache = NotificationAccessorCache()
        val parameters = arrayOf<Class<*>>(Int::class.javaPrimitiveType!!)
        val integer = cache.method(Child::class.java, "overloaded", parameters)
        parameters[0] = String::class.java
        val text = cache.method(Child::class.java, "overloaded", parameters)
        assertNotSame(integer, text)
        assertSame(integer, cache.method(Child::class.java, "overloaded", arrayOf(Int::class.javaPrimitiveType!!)))
    }

    @Test fun lookupFailuresAreNotConvertedToPermanentMissingMembers() {
        var blocked = true
        val cache = NotificationAccessorCache(declaredField = { type, name ->
            if (blocked) throw SecurityException("denied")
            type.getDeclaredField(name)
        })
        try { cache.field(Child::class.java, "hidden"); fail("access errors must propagate") }
        catch (expected: SecurityException) { assertEquals("denied", expected.message) }
        blocked = false
        assertEquals("child", cache.field(Child::class.java, "hidden")!!.get(Child()))
    }

    @Test fun concurrentReadsResolveOneMissingSignature() {
        val attempts = AtomicInteger()
        val cache = NotificationAccessorCache(declaredMethod = { type, name, parameters ->
            attempts.incrementAndGet()
            type.getDeclaredMethod(name, *parameters)
        })
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(List(40) { Callable {
                cache.method(Child::class.java, "absent", emptyArray())
            } })
            results.forEach { assertNull(it.get()) }
            assertEquals(3, attempts.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun readAndCallStillReadCurrentObjectsAndPropagateNativeInvocationFailure() {
        val row = Child()
        assertEquals(1, row.read("changing"))
        row.changing = 9
        assertEquals(9, row.read("changing"))
        assertEquals("base", row.call("inherited"))
        assertNull(row.read("missing"))
        assertNull(row.call("missing"))
        try { row.call("fails"); fail("native invocation errors must propagate") }
        catch (expected: InvocationTargetException) {
            assertEquals("native failure", expected.targetException.message)
        }
    }
}
