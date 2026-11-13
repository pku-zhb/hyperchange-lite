// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

/** Select from the current OEM row state; unknown/AOD/transition never invent a glass effect. */
internal object LockscreenRowMaterialPolicy {
    /** Same early-return condition as the retained native row selector. */
    fun selectorMayApply(aodTransition: Boolean?, fullAod: Boolean?, childInGroup: Boolean?): Boolean =
        aodTransition == false || aodTransition == true && fullAod == false && childInGroup == true

    fun effect(confirmedLitKeyguard: Boolean, showingKeyguardNotification: Boolean?, lightWallpaper: Boolean?,
               fullAod: Boolean?, aodTransition: Boolean?): String? {
        if (!confirmedLitKeyguard || showingKeyguardNotification != true || fullAod != false ||
            aodTransition != false || lightWallpaper == null) return null
        return if (lightWallpaper) "NotificationRowGlassOnKeyguardLightWallPaperEffect"
        else "NotificationRowGlassOnKeyguardEffect"
    }
}
