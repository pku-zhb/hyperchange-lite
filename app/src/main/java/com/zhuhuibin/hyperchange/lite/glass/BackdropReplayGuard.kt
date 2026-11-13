// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

/** Native clears always pass through; only our own stale replay is suppressed. */
internal class BackdropReplayGuard(private val revision: () -> Long) {
    private val replayRevision = ThreadLocal<Long>()

    fun shouldSkip(active: Boolean): Boolean = replayRevision.get()?.let {
        !active || it != revision()
    } ?: false

    fun <T> duringReplay(action: () -> T): T {
        val previous = replayRevision.get()
        replayRevision.set(revision())
        return try { action() }
        finally {
            if (previous == null) replayRevision.remove() else replayRevision.set(previous)
        }
    }
}
