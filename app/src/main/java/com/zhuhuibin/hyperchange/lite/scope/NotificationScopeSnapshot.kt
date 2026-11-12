// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

/** Short-lived decision input; never retain it for a queued task or native execution. */
internal data class NotificationScopeSnapshot(
    val role: NotificationRole,
    val token: NotificationScopeToken,
    val allowed: Boolean,
    val revision: Long,
)
