// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import java.lang.ref.WeakReference

/** Counts cannot identify recycled or reordered notification rows. */
internal class GroupStackMembership<T : Any>(members: List<T>, private val participates: (T) -> Boolean = { true }) {
    private val visible = members.map(participates)
    private val members = members.map(::WeakReference)

    fun matches(current: List<T>, attachedToOwner: (T) -> Boolean): Boolean =
        current.size == members.size && current.indices.all { index ->
            members[index].get() === current[index] && attachedToOwner(current[index]) &&
                visible[index] == participates(current[index])
        }
}
