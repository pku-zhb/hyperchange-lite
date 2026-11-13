// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.controls

import org.junit.Assert.*
import org.junit.Test

class VolumePlacementTest {
    @Test fun defaultIsNearEdgeAndZeroIsExactlyFlushRegardlessOfWrapper() {
        for (nativeRight in listOf(824f, 910f, 1060f)) {
            val near = VolumePlacementModel.edgeTranslation(1080f, nativeRight, VolumePlacementModel.DEFAULT_EDGE_GAP)
            val flush = VolumePlacementModel.edgeTranslation(1080f, nativeRight, 0f)
            assertEquals(1074f, nativeRight + near, 0f)
            assertEquals(1080f, nativeRight + flush, 0f)
        }
    }

    @Test fun changingWidthAndFoldedDisplayRecomputesTheSameVisibleGap() {
        // Each pair is a freshly measured screen edge / native visible-track edge.
        for ((screenRight, nativeRight) in listOf(1080f to 1000f, 1080f to 972f, 720f to 660f, 1840f to 1730f)) {
            val adjustment = VolumePlacementModel.edgeTranslation(screenRight, nativeRight, 6f)
            assertEquals(6f, screenRight - (nativeRight + adjustment), 0f)
        }
    }

    @Test fun wideAndTouchExpandedStatesKeepTheNativePosition() {
        assertEquals(0f, VolumePlacementModel.narrowFraction(64f, 9f, 64f), 0f)
        assertEquals(.5f, VolumePlacementModel.narrowFraction(36.5f, 9f, 64f), 0f)
        assertEquals(1f, VolumePlacementModel.narrowFraction(9f, 9f, 64f), 0f)
        assertEquals(0f, VolumePlacementModel.narrowFraction(64f, 64f, 64f), 0f)
    }

    @Test fun restorationKeepsDistinctNativePartsAndRecapturesFoldedDimensions() {
        val baseline = VolumeWidthBaseline()
        val native = mutableListOf(64, 60, 62)
        assertTrue(baseline.acquire(native))
        native[0] = 9
        assertTrue(baseline.acquire(listOf(9, 9, 9)))
        assertEquals(listOf(64, 60, 62), baseline.release())
        assertNull(baseline.release())
        // initColumn releases old ownership before OEM installs the folded layout.
        assertTrue(baseline.acquire(listOf(48, 44, 46)))
        assertEquals(listOf(48, 44, 46), baseline.release())
        assertFalse(baseline.acquire(listOf(0, 44, 46)))
        assertFalse(baseline.acquire(listOf(48, 44)))
    }

    @Test fun legacyPhysicalOffsetsMigrateOnceAndNewGapWins() {
        assertEquals(6f, VolumePlacementModel.resolveEdgeGap(null), 0f)
        assertEquals(0f, VolumePlacementModel.resolveEdgeGap(null, -50f), 0f)
        assertEquals(26.9f, VolumePlacementModel.resolveEdgeGap(null, 20.9f), .0001f)
        assertEquals(4f, VolumePlacementModel.resolveEdgeGap(4f, 50f), 0f)
        assertEquals(200f, VolumePlacementModel.resolveEdgeGap(500f), 0f)
        assertEquals(6f, VolumePlacementModel.resolveEdgeGap(Float.NaN), 0f)
    }
}
