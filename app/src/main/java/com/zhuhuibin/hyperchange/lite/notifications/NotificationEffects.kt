// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope

/** Each feature owns its reflection contract, cleanup and failure boundary. */
class NotificationEffects {
    private var installed = false
    private val tilt = NotificationTilt()
    private val headsUp = NotificationHeadsUp()
    private val rows = NotificationRowAnimations()
    private val readability = BannerReadability()

    fun install(runtime: HookRuntime) {
        if (installed || runtime.packageName != "com.android.systemui") return
        installed = true
        NotificationScope.install(runtime)
        for ((name, install) in listOf<Pair<String, () -> Unit>>(
            "press" to { tilt.install(runtime) }, "heads-up" to { headsUp.install(runtime) },
            "add/remove" to { rows.install(runtime) },
            "banner readability" to { readability.install(runtime) },
        )) runCatching(install).onFailure { runtime.log("Notification $name hooks unavailable; native behavior retained", it) }
    }
}
