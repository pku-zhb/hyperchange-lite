package com.zhuhuibin.hyperchange.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class RadiusBaselineTest {
    @Test fun repeatedCustomOutputDoesNotBecomeNativeOnDisable() {
        val baseline = RadiusBaseline(20f)
        assertEquals(40f, baseline.resolve(20f, 40f), 0f)
        assertEquals(50f, baseline.resolve(40f, 50f), 0f)
        assertEquals(20f, baseline.resolve(50f, null), 0f)
    }

    @Test fun realNativeChangesArePreservedAcrossDisable() {
        val baseline = RadiusBaseline(20f)
        baseline.resolve(20f, 40f)
        baseline.resolve(24f, 40f)
        assertEquals(24f, baseline.resolve(40f, null), 0f)
    }
}
