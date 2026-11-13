// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class ResourceNameCacheTest {
    @Test fun generatedIdsNeverReachTheResourceResolver() {
        val cache = ResourceNameCache<Any>()
        for (id in listOf(-1, 0, 3, 18, 0x10a, 0x00ffffff))
            assertEquals("", cache.name(Any(), id) { error("generated ID was resolved") })
    }

    @Test fun nativeAndAppNamesAreCachedPerResourcesOwner() {
        val cache = ResourceNameCache<Any>()
        val resources = Any()
        var lookups = 0
        repeat(1000) {
            assertEquals("background", cache.name(resources, 0x01020001) { lookups++; "background" })
            assertEquals("shade_background", cache.name(resources, 0x7f010001) { lookups++; "shade_background" })
        }
        assertEquals(2, lookups)
        assertEquals("another_owner", cache.name(Any(), 0x7f010001) { "another_owner" })
    }

    @Test fun aMissingResourceDoesNotThrowOnEveryNativeFrame() {
        val cache = ResourceNameCache<Any>()
        val resources = Any()
        var lookups = 0
        repeat(1000) {
            assertEquals("", cache.name(resources, 0x7f011234) { lookups++; error("missing resource") })
        }
        assertEquals(1, lookups)
    }

    @Test fun thePerOwnerCacheIsBounded() {
        val cache = ResourceNameCache<Any>(capacity = 2)
        val resources = Any()
        for (id in 1..3) cache.name(resources, 0x7f010000 + id) { "name-$id" }
        assertEquals("new-name", cache.name(resources, 0x7f010001) { "new-name" })
    }
}
