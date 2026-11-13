// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

/** Retained for backup migration only, with no current App control or installed feature. */
internal object DeferredSettings {
    val hiddenKeys = setOf(
        "notification_group_stack_enabled", "notification_group_stack_spacing",
        "notification_group_stack_scale", "notification_disable_fold_history_enabled",
        "glass_shade", "glass_shade_scale",
    )
}
