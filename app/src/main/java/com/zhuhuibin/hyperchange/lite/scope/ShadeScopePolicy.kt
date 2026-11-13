// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** A visible heads-up window is not an expanded shade. Unknown evidence stays native. */
internal object ShadeScopePolicy {
    fun notification(
        fraction: Float?, expandedWithoutHeadsUp: Boolean?, barState: Int?, confirmedUnlocked: Boolean,
    ): Boolean = confirmedUnlocked && barState == 0 && expandedWithoutHeadsUp == true && positive(fraction)

    /** Appearance can turn false before a real control-center collapse finishes. */
    fun controlCenter(fraction: Float?, confirmedUnlocked: Boolean): Boolean =
        confirmedUnlocked && positive(fraction)

    fun owns(
        targetRoot: Any?, ownerRoot: Any?, currentOwnerRoot: Any?, attached: Boolean,
        active: Boolean, stopped: Boolean, blocked: Boolean,
    ): Boolean = targetRoot != null && targetRoot === ownerRoot && ownerRoot === currentOwnerRoot &&
        attached && active && !stopped && !blocked

    private fun positive(value: Float?): Boolean = value != null && value.isFinite() && value > 0f
}

/** A stale positive getter cannot reopen ownership after close, detach or scene replacement. */
internal class ShadeScopeLease {
    var active = false
        private set
    private var needsOpenSignal = true

    fun revoke(): Boolean {
        needsOpenSignal = true
        val changed = active
        active = false
        return changed
    }

    /** Return true only when authorization changes, not on every animation frame. */
    fun sample(valid: Boolean, openSignal: Boolean): Boolean {
        if (!valid) needsOpenSignal = true
        else if (openSignal) needsOpenSignal = false
        val next = valid && !needsOpenSignal
        val changed = active != next
        active = next
        return changed
    }
}

/** Caller-serialized weak ownership, with independent kinds even inside one shared window. */
internal class ShadeScopeRegistry<R : Any, K : Any, O : Any> {
    data class Binding<O : Any>(val accepted: Boolean, val changed: Boolean, val replaced: O? = null)
    private val roots = WeakHashMap<R, MutableMap<K, WeakReference<O>>>()
    private val retired = WeakHashMap<O, Boolean>()

    fun owner(root: R, kind: K): O? = roots[root]?.get(kind)?.get()

    fun owners(root: R): List<O> = roots[root]?.values?.mapNotNull { it.get() }.orEmpty()

    fun bind(root: R, kind: K, owner: O): Binding<O> {
        if (retired[owner] == true) return Binding(accepted = false, changed = false)
        val previous = this.owner(root, kind)
        if (previous === owner) return Binding(accepted = true, changed = false)
        if (previous != null) retired[previous] = true
        roots.getOrPut(root) { mutableMapOf() }[kind] = WeakReference(owner)
        return Binding(accepted = true, changed = true, replaced = previous)
    }

    fun remove(root: R, kind: K, owner: O): Boolean {
        if (this.owner(root, kind) !== owner) return false
        val kinds = roots[root] ?: return false
        kinds.remove(kind)
        if (kinds.isEmpty()) roots.remove(root)
        return true
    }
}
