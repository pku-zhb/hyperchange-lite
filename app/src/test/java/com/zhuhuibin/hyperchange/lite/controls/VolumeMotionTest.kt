package com.zhuhuibin.hyperchange.lite.controls

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VolumeMotionTest {
    @Test fun popScaleIsInitialSizeAndShiftIncludesVerticalComponent() {
        val start = VolumeMotionModel.popTransform(0f, .2f, 10f, 3f)
        assertEquals(.2f, start.scale, .0001f)
        assertEquals(-10f, start.x, .0001f)
        assertEquals(-3.5f, start.y, .0001f)
        assertEquals(1f, VolumeMotionModel.popTransform(1f, .2f, 10f, 3f).scale, 0f)
        val overshoot = VolumeMotionModel.popTransform(1.1f, .2f, 10f, 3f)
        assertTrue(overshoot.scale > 1f && overshoot.x > 0f && overshoot.y > 0f)
    }

    @Test fun dragIsBoundedAndPreservesSymmetry() {
        val positive = VolumeMotionModel.dragTransform(100f, 0f, 60f, 160f)
        val negative = VolumeMotionModel.dragTransform(-100f, 0f, 60f, 160f)
        assertEquals(-positive.x, negative.x, .0001f)
        assertTrue(positive.x in 0f..60f)
        assertTrue(positive.scaleX > 1f && positive.scaleY < 1f)
        assertEquals(VolumeDragTransform(0f, 0f, 1f, 1f), VolumeMotionModel.dragTransform(0f, 0f, 60f, 160f))
    }

    @Test fun widthSettlesAcrossRefreshRatesAndFrameStalls() {
        for (fps in listOf(60, 90, 120)) {
            val spring = VolumeSpring(64f)
            val (k, d) = VolumeMotionModel.widthCoefficients(600f)
            spring.target(9f, k, d)
            repeat(fps * 6) { spring.advance(if (it == 12) .5f else 1f / fps) }
            assertEquals(9f, spring.value, .01f)
            assertTrue(spring.settled)
        }
    }

    @Test fun highResponseRemainsFiniteAndCanRetargetDuringMotion() {
        val spring = VolumeSpring(0f)
        spring.target(100f, 4000f, 400f)
        repeat(12) { spring.advance(.064f); assertTrue(spring.value.isFinite()) }
        spring.target(0f, 400f, 40f)
        repeat(300) { spring.advance(1f / 120f) }
        assertEquals(0f, spring.value, .01f)
    }

    @Test fun fastLowDampingPopSettlesAtThirtyFpsAndDuringFrameStalls() {
        val (k, d) = VolumeMotionModel.popCoefficients(200f, .5f)
        for (frameTime in listOf(1f / 30f, .064f)) {
            val spring = VolumeSpring(0f)
            spring.target(1f, k, d)
            repeat(120) {
                spring.advance(frameTime)
                assertTrue("Pop must remain bounded at $frameTime", spring.value in -.01f..2f)
            }
            assertEquals(1f, spring.value, .002f)
            assertTrue(spring.settled)
        }
    }

    @Test fun iconOnlyReappearsAtFinalWidthTransition() {
        assertEquals(0f, VolumeMotionModel.iconOpacity(9f, 9f, 64f), 0f)
        assertEquals(0f, VolumeMotionModel.iconOpacity(50f, 9f, 64f), 0f)
        assertEquals(1f, VolumeMotionModel.iconOpacity(64f, 9f, 64f), .0001f)
    }
}
