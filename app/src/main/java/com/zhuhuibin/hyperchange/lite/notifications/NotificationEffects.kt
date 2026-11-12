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
    private val groups = NotificationGroupStack()

    fun install(runtime: HookRuntime) {
        if (installed || runtime.packageName != "com.android.systemui") return
        installed = true
        NotificationScope.install(runtime)
        for ((name, install) in listOf<Pair<String, () -> Unit>>(
            "press" to { tilt.install(runtime) }, "heads-up" to { headsUp.install(runtime) },
            "add/remove" to { rows.install(runtime) }, "groups" to { groups.install(runtime) },
        )) runCatching(install).onFailure { runtime.log("Notification $name hooks unavailable; native behavior retained", it) }
        installHistoryPolicy(runtime)
    }

    /** Adapted from HyperChanger NotificationHooks, Copyright 2026 btm_m. */
    private fun installHistoryPolicy(runtime: HookRuntime) {
        val key = "notification_disable_fold_history_enabled"
        runtime.type("com.android.systemui.statusbar.notification.collection.coordinator.FoldCoordinator")
            ?.declaredMethods?.filter { it.name == "attach" && it.parameterCount == 1 }?.forEachIndexed { index, method ->
                runtime.hook(method, "notification-history:coordinator:$index") { chain ->
                    if (runtime.preferences.flag(key)) null else chain.proceed()
                }
            }
        runtime.type("com.miui.systemui.notification.MiuiBaseNotifUtil")?.declaredMethods
            ?.filter { it.name == "shouldSuppressFold" && it.parameterCount == 0 }?.forEachIndexed { index, method ->
                runtime.hook(method, "notification-history:utility:$index") { chain ->
                    if (runtime.preferences.flag(key)) true else chain.proceed()
                }
            }
    }
}
