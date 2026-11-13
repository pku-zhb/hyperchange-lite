// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import java.util.WeakHashMap

/** Generated view IDs have no resource package. Never ask Resources to resolve them. */
internal class ResourceNameCache<R : Any>(private val capacity: Int = 512) {
    private val owners = WeakHashMap<R, LinkedHashMap<Int, String>>()

    @Synchronized fun name(owner: R, id: Int, lookup: (Int) -> String): String {
        if (id == -1 || id ushr 24 == 0) return ""
        val names = owners.getOrPut(owner) { linkedMapOf() }
        names[id]?.let { return it }
        // Cache misses too: catching the same NotFoundException on every native frame is costly.
        val name = runCatching { lookup(id) }.getOrDefault("")
        if (names.size >= capacity) names.remove(names.keys.first())
        names[id] = name
        return name
    }
}
