// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

/** Do not mistake a caller replaying our previous output for a new OEM baseline. */
internal class RadiusBaseline(var original: Float) {
    private var lastOutput: Float? = null
    fun resolve(incoming: Float, override: Float?, acceptNativeUpdate: Boolean = true,
                freshResourceValue: Boolean = false): Float {
        if (acceptNativeUpdate && (freshResourceValue || incoming != lastOutput)) original = incoming
        return (override ?: original).also { lastOutput = if (override == null) null else it }
    }
}
