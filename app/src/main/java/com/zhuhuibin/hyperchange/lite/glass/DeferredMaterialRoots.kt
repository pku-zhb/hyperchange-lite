// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** A native busy rejection may retry once when that same transition owner settles. */
internal class DeferredMaterialRoots<K : Any, O : Any> {
    private val roots = WeakHashMap<K, WeakReference<O>>()

    @Synchronized fun blocked(root: K, owner: O) { roots[root] = WeakReference(owner) }
    @Synchronized fun clear(root: K) { roots.remove(root) }
    @Synchronized fun size(): Int = roots.size

    @Synchronized fun take(owner: O, settled: Boolean): List<K> {
        if (!settled) return emptyList()
        val ready = roots.filterValues { it.get() === owner }.keys.toList()
        ready.forEach(roots::remove)
        return ready
    }
}
