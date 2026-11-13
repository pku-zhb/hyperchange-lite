// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class CardBlendOpacityTest {
    @Test fun activeNotificationCardsConsumeTheSelectedTintInsteadOfAnImplicitHundredPercent() {
        // Heads-up and ordinary rows are both non-volume cards. Native colors and
        // blend modes stay scene-specific; the selected percentage is no longer skipped.
        assertEquals(73, cardBlendOpacity(tuning = true, volume = false, percent = 73))
        assertEquals(0, cardBlendOpacity(tuning = true, volume = false, percent = 0))
        assertEquals(100, cardBlendOpacity(tuning = true, volume = false, percent = 100))
    }

    @Test fun disabledTuningAndVolumePreserveTheNativeAlphaOwner() {
        assertEquals(100, cardBlendOpacity(tuning = false, volume = false, percent = 0))
        assertEquals(100, cardBlendOpacity(tuning = true, volume = true, percent = 0))
    }

    @Test fun selectedTintPreservesTheNativeRgbAndZeroAlpha() {
        val percent = cardBlendOpacity(tuning = true, volume = false, percent = 73)
        assertEquals(0x95919191.toInt(), softGlassColorAlpha(0xcc919191.toInt(), percent))
        assertEquals(0x00919191, softGlassColorAlpha(0x00919191, percent))
    }
}
