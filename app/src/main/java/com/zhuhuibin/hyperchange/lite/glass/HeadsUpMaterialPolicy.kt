// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

/** Explicit banner styling never broadens the ordinary range or lockscreen permission. */
internal fun headsUpGlassAllowed(optIn: Boolean, tuning: Boolean, ordinary: Boolean, confirmedUnlocked: Boolean): Boolean =
    optIn && tuning && ordinary && confirmedUnlocked
