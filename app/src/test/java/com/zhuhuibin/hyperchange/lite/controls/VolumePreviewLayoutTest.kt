// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import org.junit.Assert.*
import org.junit.Test

class VolumePreviewLayoutTest {
    @Test fun twoIndependentPillsStayBelowTheWideBarAndWithinShortViewports() {
        for (height in listOf(120f, 240f, 480f)) {
            val layout = VolumePreviewLayout.fit(360f, height, 1f, 52f, 150f)
            assertEquals(layout.barLeft, layout.buttonLeft, .001f)
            assertTrue(layout.muteTop > layout.barTop + layout.barHeight)
            assertTrue(layout.dndTop > layout.muteTop + layout.buttonHeight)
            assertTrue(layout.dndTop + layout.buttonHeight <= height + .001f)
        }
    }

    @Test fun narrowingChangesTheVisibleTrackWidthWithoutCreatingAWideTouchPlaceholder() {
        val wide = VolumePreviewLayout.fit(360f, 240f, 1f, 52f, 150f)
        val narrow = VolumePreviewLayout.fit(360f, 240f, 1f, 9f, 150f)
        assertEquals(wide.barHeight, narrow.barHeight, 0f)
        assertEquals(9f / 52f, narrow.barWidth / wide.barWidth, .0001f)
        assertEquals(wide.barLeft + wide.barWidth, narrow.barLeft + narrow.barWidth, .001f)
    }
}
