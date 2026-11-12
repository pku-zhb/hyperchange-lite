// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import java.util.IdentityHashMap

/** Reuses decisions only inside one synchronous operation, never across frames or callbacks. */
internal class MaterialCallMemo<K : Any, V : Any, S> {
    private data class Entry<V, S>(val stamp: S, val value: V)
    private val current = ThreadLocal<IdentityHashMap<K, Entry<V, S>>>()

    fun <R> within(action: () -> R): R {
        if (current.get() != null) return action()
        current.set(IdentityHashMap())
        return try { action() } finally { current.remove() }
    }

    fun get(key: K, stamp: S, capture: () -> V): V {
        return scoped(key, stamp, capture) ?: capture()
    }

    fun forget(key: K) { current.get()?.remove(key) }

    /** Unscoped lookups do not allocate a decision or retain a view. */
    fun scoped(key: K, stamp: S, capture: () -> V): V? {
        val frame = current.get() ?: return null
        frame[key]?.takeIf { it.stamp == stamp }?.let { return it.value }
        return capture().also { frame[key] = Entry(stamp, it) }
    }
}
