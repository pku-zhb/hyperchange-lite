// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

/** The track corner and the moving fill boundary are different native channels. */
internal object SliderRadiusPolicy {
    fun configured(method: String, radius: Float?): Float? = when (method) {
        "setOutlineRadius", "getCornerRadius" -> radius
        else -> null
    }
}
