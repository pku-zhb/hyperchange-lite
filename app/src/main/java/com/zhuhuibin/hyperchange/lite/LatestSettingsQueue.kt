// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import java.util.concurrent.Executor

/** Coalesce edits by key, with ordered barriers for import, export, reset and service binding. */
internal class LatestSettingsQueue<K : Any>(
    private val executor: Executor,
    private val onFailure: (Throwable) -> Unit,
) {
    private class Batch<K : Any> { val writes = linkedMapOf<K, () -> Unit>() }
    private var current: Batch<K>? = null

    @Synchronized fun update(key: K, action: () -> Unit) {
        val batch = current
        if (batch != null) {
            batch.writes[key] = action
        } else {
            val next = Batch<K>()
            next.writes[key] = action
            current = next
            executor.execute { drain(next) }
        }
    }

    /** Seal the current batch so a later edit cannot move ahead of this operation. */
    @Synchronized fun execute(action: () -> Unit) {
        current = null
        executor.execute { runCatching(action).onFailure(onFailure) }
    }

    private fun drain(batch: Batch<K>) {
        while (true) {
            val action = synchronized(this) {
                val key = batch.writes.keys.firstOrNull()
                if (key == null) {
                    if (current === batch) current = null
                    null
                } else batch.writes.remove(key)
            } ?: return
            runCatching(action).onFailure(onFailure)
        }
    }
}
