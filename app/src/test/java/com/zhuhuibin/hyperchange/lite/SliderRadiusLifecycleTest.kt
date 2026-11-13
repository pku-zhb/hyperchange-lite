package com.zhuhuibin.hyperchange.lite

import org.junit.Assert.*
import org.junit.Test

class SliderRadiusLifecycleTest {
    @Test fun independentTrackAndFillRestoreTheirOwnNativeValues() {
        val outline = RadiusBaseline(40f)
        val progress = RadiusBaseline(24f)
        assertEquals(64f, outline.resolve(40f, 64f), 0f)
        assertEquals(64f, progress.resolve(24f, 64f), 0f)
        // A replacement drawable receives the saved native input, not last output.
        assertEquals(64f, progress.resolve(progress.original, 64f, false), 0f)
        assertEquals(40f, outline.resolve(outline.original, null, false), 0f)
        assertEquals(24f, progress.resolve(progress.original, null, false), 0f)
    }

    @Test fun aDensityOrSizeUpdateRebasesBothChannelsBeforeTheNextDisable() {
        val outline = RadiusBaseline(40f)
        val progress = RadiusBaseline(24f)
        outline.resolve(40f, 64f); progress.resolve(24f, 64f)
        outline.resolve(52f, 80f); progress.resolve(32f, 80f)
        assertEquals(52f, outline.resolve(80f, null), 0f)
        assertEquals(32f, progress.resolve(80f, null), 0f)
    }

    @Test fun foldingIntoTheQualifierMatchingTheOldOverrideStillUpdatesNativeBaseline() {
        val radius = RadiusBaseline(24f)
        radius.resolve(24f, 48f)
        // This ROM changes universal radius from 24dp to 48dp at sw340dp.
        radius.resolve(48f, 64f, freshResourceValue = true)
        assertEquals(48f, radius.resolve(64f, null), 0f)
        // Our own refresh must still not adopt its output as native.
        radius.resolve(48f, 64f)
        radius.resolve(64f, 80f, acceptNativeUpdate = false, freshResourceValue = true)
        assertEquals(48f, radius.resolve(80f, null), 0f)
    }

}
