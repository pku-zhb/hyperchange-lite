// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

internal enum class NotificationRole { MEDIA, HEADS_UP, ORDINARY }

/** Unknown controller state never means an unlocked notification shade. */
internal data class NotificationScene(
    val showing: Boolean? = null,
    val dozing: Boolean? = null,
    val transitioning: Boolean? = null,
    val statusBarState: Int? = null,
    val wakefulness: Int? = null,
    val screenState: Int? = null,
) {
    val confirmedUnlocked: Boolean
        get() = showing == false && dozing == false && transitioning == false && statusBarState == 0
    val confirmedLitKeyguard: Boolean
        get() = wakefulness == 2 && screenState == 2 && showing == true && dozing == false && transitioning == false &&
            (statusBarState == 1 || statusBarState == 2)
}

internal object NotificationScopePolicy {
    /** Media has separate permission, but its OEM recipe still changes with keyguard/AOD. */
    fun materialGeneration(role: NotificationRole, sceneGeneration: Long): Long =
        if (role == NotificationRole.HEADS_UP) 0L else sceneGeneration

    fun allows(role: NotificationRole, lockscreenEffects: Boolean, scene: NotificationScene): Boolean =
        role != NotificationRole.ORDINARY || lockscreenEffects || scene.confirmedUnlocked

    /** Every animation role yields in unknown, AOD and transitioning scenes. */
    fun animationAllows(lockscreenAnimations: Boolean, scene: NotificationScene): Boolean =
        scene.wakefulness == 2 && scene.screenState == 2 &&
            (scene.confirmedUnlocked || lockscreenAnimations && scene.confirmedLitKeyguard)
}

/** A queued callback cannot regain ownership after a scope or role round trip. */
internal data class NotificationScopeToken(val generation: Long, val role: NotificationRole, val roleGeneration: Long = 0) {
    fun isCurrent(currentGeneration: Long, currentRole: NotificationRole, currentRoleGeneration: Long = 0): Boolean =
        generation == currentGeneration && role == currentRole && roleGeneration == currentRoleGeneration
}
