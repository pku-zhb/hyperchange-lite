// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

/** Coalescing a scene signal must not promote a card edit into a whole-window replay. */
internal class MaterialRefreshPlan {
    data class Request(val cards: Boolean, val backdrops: Boolean, val notificationScene: Boolean) {
        val notifications get() = cards || notificationScene
    }
    private var cards = false
    private var backdrops = false
    private var notificationScene = false

    fun include(sceneOnly: Boolean, cards: Boolean, backdrops: Boolean) {
        if (sceneOnly) notificationScene = true
        else { this.cards = this.cards || cards; this.backdrops = this.backdrops || backdrops }
    }

    fun take(): Request = Request(cards, backdrops, notificationScene).also {
        cards = false; backdrops = false; notificationScene = false
    }
}
