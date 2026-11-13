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

    /** Native set*Size writes are fresh layout inputs, never our animated frame widths. */
    fun updateNative(index: Int, width: Int) {
        val previous = saved ?: return
        if (index !in previous.indices || width <= 0) return
        saved = previous.toMutableList().also { it[index] = width }
    }
}
