package com.zhuhuibin.hyperchange.lite.notifications

import org.junit.Assert.*
import org.junit.Test

class BlurFrameGateTest {
    @Test fun headsUpPositiveWritesWaitForSixteenMilliseconds() {
        val gate = BlurFrameGate(16)
        assertTrue(gate.shouldApply(50f, 100L))
        gate.committed(50f, 100L)
        assertFalse(gate.shouldApply(48f, 108L))
        assertFalse(gate.shouldApply(46f, 115L))
        assertTrue(gate.shouldApply(44f, 116L))
        gate.committed(44f, 116L)
        assertFalse(gate.shouldApply(44f, 140L))
    }

    @Test fun zeroAndResetDoNotWaitForThePositiveBlurInterval() {
        val gate = BlurFrameGate(16)
        gate.committed(50f, 100L)
        assertTrue(gate.shouldApply(0f, 101L))
        gate.committed(0f, 101L)
        assertFalse(gate.shouldApply(0f, 102L))
        gate.reset()
        assertTrue(gate.shouldApply(50f, 102L))
    }

    @Test fun addRemoveHasNoExtraFrameRateLimit() {
        val gate = BlurFrameGate()
        gate.committed(50f, 100L)
        assertTrue(gate.shouldApply(48f, 101L))
    }

    @Test fun skippedBlurTargetDoesNotClaimTheCurrentEffect() {
        val gate = BlurFrameGate(16)
        val owned = OwnedValue("original")
        var effect = "blur-50"
        owned.record(effect)
        gate.committed(50f, 100L)
        if (gate.shouldApply(48f, 108L)) {
            effect = "blur-48"
            owned.record(effect)
            gate.committed(48f, 108L)
        }
        owned.release(effect) { effect = it }
        assertEquals("original", effect)
    }
}
