// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

/** Restore a temporary property only if nobody replaced our last committed value. */
internal class OwnedValue<T>(private var original: T) {
    private var wrote = false
    private var last: T = original

    fun record(value: T) { last = value; wrote = true }
    fun rebase(value: T) { original = value }
    fun owns(current: T): Boolean = wrote && current == last

    fun release(current: T, restore: (T) -> Unit) {
        if (!wrote) return
        wrote = false
        if (current == last) restore(original)
    }
}
