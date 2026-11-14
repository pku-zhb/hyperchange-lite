// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class DeferredMaterialRootsTest {
    @Test fun aBusyNativeTransitionRetainsOnlyOneDeliveryUntilItActuallySettles() {
        val deferred = DeferredMaterialRoots<Any, Any>()
        val row = Any(); val owner = Any()
        repeat(100) { deferred.blocked(row, owner) }
        assertEquals(1, deferred.size())
        assertTrue(deferred.take(owner, false).isEmpty())
        assertEquals(listOf(row), deferred.take(owner, true))
        assertTrue(deferred.take(owner, true).isEmpty())
    }

    @Test fun anotherControllerCannotCompleteThePendingRoots() {
        val deferred = DeferredMaterialRoots<Any, Any>()
        val row = Any(); val first = Any(); val second = Any()
        deferred.blocked(row, first)
        assertTrue(deferred.take(second, true).isEmpty())
        assertEquals(listOf(row), deferred.take(first, true))
    }

    @Test fun aReboundRootBelongsOnlyToItsLatestNativeController() {
        val deferred = DeferredMaterialRoots<Any, Any>()
        val row = Any(); val first = Any(); val second = Any()
        deferred.blocked(row, first)
        deferred.blocked(row, second)
        assertTrue(deferred.take(first, true).isEmpty())
        assertEquals(listOf(row), deferred.take(second, true))
    }

    @Test fun aSuccessfulCurrentRefreshRetiresTheBusyRetry() {
        val deferred = DeferredMaterialRoots<Any, Any>()
        val row = Any(); val media = Any(); val owner = Any()
        deferred.blocked(row, owner)
        deferred.blocked(media, owner)
        deferred.clear(row)
        assertEquals(listOf(media), deferred.take(owner, true))
    }
}
