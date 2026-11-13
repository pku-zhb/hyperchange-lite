// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

/** Capture native dimensions once per ownership period, before applying any width. */
internal class VolumeWidthBaseline {
    private var saved: List<Int>? = null
    val widths: List<Int> get() = saved.orEmpty()

    fun acquire(current: List<Int>): Boolean {
        if (saved != null) return true
        if (current.size != 3 || current.any { it <= 0 }) return false
        saved = current.toList()
        return true
    }

    fun release(): List<Int>? {
        val original = saved
        saved = null
        return original
    }
}
