// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Range permission is independent of tuning and of the forced normal recipe. */
internal data class NotificationMaterialPolicy(
    val ordinary: Boolean,
    val media: Boolean,
    val tuning: Boolean,
    val unify: Boolean,
) {
    fun range(isMedia: Boolean, sceneAllowed: Boolean): Boolean =
        (if (isMedia) media else ordinary) && (isMedia || sceneAllowed)

    fun tune(isMedia: Boolean, sceneAllowed: Boolean): Boolean = tuning && range(isMedia, sceneAllowed)

    fun force(isMedia: Boolean, sceneAllowed: Boolean, headsUp: Boolean, focus: Boolean): Boolean =
        unify && range(isMedia, sceneAllowed) && !headsUp && (isMedia || focus)
}

/** A queued material operation must belong to the current configuration/scene. */
internal class MaterialGeneration {
    @Volatile var value: Long = 0L
        private set
    @Synchronized fun invalidate(): Long { value++; return value }
    fun current(ticket: Long, permitted: Boolean): Boolean = ticket == value && permitted
}

/** Observing a newer native write revokes a queued release even if pixels look equal. */
internal class MaterialWriteClock<K : Any> {
    class Ticket<K : Any> internal constructor(internal val owner: WeakReference<K>, internal val revision: Long)
    private val revisions = WeakHashMap<K, Long>()
    @Synchronized fun touch(owner: K) { revisions[owner] = (revisions[owner] ?: 0L) + 1L }
    fun observe(owner: K, permitted: Boolean) { if (!permitted) touch(owner) }
    @Synchronized fun ticket(owner: K): Ticket<K> = Ticket(WeakReference(owner), revisions[owner] ?: 0L)
    @Synchronized fun current(ticket: Ticket<K>): Boolean =
        ticket.owner.get()?.let { (revisions[it] ?: 0L) == ticket.revision } == true
}

/** Release ownership outlives a cancelled frame; delivery or a newer owner ends it. */
internal class MaterialReleaseLedger<K : Any> {
    private val pending = WeakHashMap<K, () -> Boolean>()
    @Synchronized fun offer(owner: K, guard: () -> Boolean) { pending[owner] = guard }
    @Synchronized fun snapshot(): Map<K, () -> Boolean> = pending.toMap()
    @Synchronized fun discard(owner: K, guard: () -> Boolean) { if (pending[owner] === guard) pending.remove(owner) }
    @Synchronized fun take(owner: K, guard: () -> Boolean): Boolean {
        if (pending[owner] !== guard) return false
        pending.remove(owner)
        return guard()
    }
}

/** An admitted refresh is a request, not an ownership handoff. Its queue may be cancelled. */
internal fun canForgetMaterialState(allowed: Boolean, stillOwned: Boolean, releaseTransferred: Boolean): Boolean =
    !allowed && (!stillOwned || releaseTransferred)
