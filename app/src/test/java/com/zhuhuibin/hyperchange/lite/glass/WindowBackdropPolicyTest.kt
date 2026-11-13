// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class WindowBackdropPolicyTest {
    @Test fun zeroRemovesNativeBlurWithoutRaisingItsAnimationZero() {
        assertEquals(0, WindowBackdropPolicy.radius(120, true, 0))
        assertEquals(0, WindowBackdropPolicy.radius(0, true, 200))
        assertEquals(60, WindowBackdropPolicy.radius(120, true, 50))
        assertEquals(120, WindowBackdropPolicy.radius(120, true, 100))
        assertEquals(240, WindowBackdropPolicy.radius(120, true, 200))
    }

    @Test fun disabledAlwaysReturnsCurrentNativeRadius() {
        for (radius in listOf(0, 4, 89, 250))
            for (percent in listOf(0, 50, 100, 200))
                assertEquals(radius, WindowBackdropPolicy.radius(radius, false, percent))
    }

    @Test fun onlyTheExplicitTwoZeroStateRemovesWholeWindowOptics() {
        val clear = SoftGlassSettings(enabled = true, backgroundBlur = 0, backgroundOpacity = 0)
        assertTrue(WindowBackdropPolicy.transparent(clear))
        assertFalse(WindowBackdropPolicy.transparent(clear.copy(enabled = false)))
        assertFalse(WindowBackdropPolicy.transparent(clear.copy(backgroundBlur = 1)))
        assertFalse(WindowBackdropPolicy.transparent(clear.copy(backgroundOpacity = 1)))
        assertFalse(WindowBackdropPolicy.transparent(SoftGlassSettings(enabled = true)))
    }
}
