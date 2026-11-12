// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

internal enum class NotificationRole { MEDIA, HEADS_UP, ORDINARY }

/** Unknown controller state never means an unlocked notification shade. */
internal data class NotificationScene(
    val showing: Boolean? = null,
    val dozing: Boolean? = null,
    val transitioning: Boolean? = null,
    val statusBarState: Int? = null,
) {
    val confirmedUnlocked: Boolean
        get() = showing == false && dozing == false && transitioning == false && statusBarState == 0
}

internal object NotificationScopePolicy {
    fun allows(role: NotificationRole, lockscreenEffects: Boolean, scene: NotificationScene): Boolean =
        role != NotificationRole.ORDINARY || lockscreenEffects || scene.confirmedUnlocked
}

/** A queued callback cannot regain ownership after a scope or role round trip. */
internal data class NotificationScopeToken(val generation: Long, val role: NotificationRole, val roleGeneration: Long = 0) {
    fun isCurrent(currentGeneration: Long, currentRole: NotificationRole, currentRoleGeneration: Long = 0): Boolean =
        generation == currentGeneration && role == currentRole && roleGeneration == currentRoleGeneration
}
