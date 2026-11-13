// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class ShadeBlendPolicyTest {
    @Test fun zeroTintRetainsTheStructuralShadeTransform() {
        val neutral = 0xff999999.toInt()
        val tint = 0xff0d8000.toInt()
        assertEquals(neutral, shadeBlendColor(neutral, 21, 0))
        assertEquals(0x000d8000, shadeBlendColor(tint, 106, 0))
    }

    @Test fun tintUsesNativeAlphaWhileOtherModesRemainUntouched() {
        val tint = 0x80604020.toInt()
        for (percent in listOf(0, 25, 50, 100, 150, 200)) {
            assertEquals(softGlassColorAlpha(tint, percent), shadeBlendColor(tint, 106, percent))
            for (mode in listOf(21, 0, 3, 24, 999)) assertEquals(tint, shadeBlendColor(tint, mode, percent))
        }
    }

    @Test fun disabledOrNativeBaselineKeepsAllColorBits() {
        for (color in listOf(0, 0xff0d8000.toInt(), 0x52800000, 0x80ffffff.toInt()))
            for (mode in listOf(21, 106, 999)) assertEquals(color, shadeBlendColor(color, mode, 100))
    }
}
