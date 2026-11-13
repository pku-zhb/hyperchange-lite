// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

/** Retained handler codes 0..3 change screen state; 4..7 change wakefulness. */
internal object NotificationLifecycleTransitions {
    private val targets = intArrayOf(1, 2, 3, 0)
    fun changes(what: Int, screen: Int?, wakefulness: Int?): Boolean = when (what) {
        in 0..3 -> screen != targets[what]
        in 4..7 -> wakefulness != targets[what - 4]
        else -> false
    }
}
