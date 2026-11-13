// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

/** A compositor call belongs to one native controller, exact ViewRoot and scene. */
internal class WindowBackdropCallGuard<T : Any>(private val revision: () -> Long,
                                               private val active: (T) -> Boolean) {
    private data class Source<T>(val owner: T, val root: Any, val revision: Long)
    private val source = ThreadLocal<Source<T>>()

    fun <R> during(owner: T?, root: Any?, action: () -> R): R {
        val before = source.get()
        val current = runCatching {
            val epoch = revision()
            if (owner != null && root != null && active(owner) && epoch == revision())
                Source(owner, root, epoch) else null
        }.getOrNull()
        if (current == null) source.remove() else source.set(current)
        return try { action() }
        finally { if (before == null) source.remove() else source.set(before) }
    }

    fun allows(root: Any?): Boolean {
        val current = source.get() ?: return false
        return runCatching {
            root === current.root && current.revision == revision() &&
                active(current.owner) && current.revision == revision()
        }.getOrDefault(false)
    }
}
