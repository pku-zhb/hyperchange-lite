package com.zhuhuibin.hyperchange.lite
import com.zhuhuibin.hyperchange.lite.preview.shadeBackdropPreviewState
import com.zhuhuibin.hyperchange.lite.ui.Feature
import org.junit.Assert.*
import org.junit.Test
class ShadeBackdropPreviewTest {
 @Test fun panelControlsHaveTheirOwnDestinationAndOnlyTwoVisibleNumbers() {
  assertEquals(setOf("glass_shade_enabled", "glass_shade_blur", "glass_shade_opacity"), Feature.BACKDROP.visibleSettings.map { it.key }.toSet())
  assertFalse(Feature.MATERIAL.visibleSettings.any { it.key.startsWith("glass_shade_") })
 }
 @Test fun zeroClearsTheWholeBackgroundWhileOffKeepsTheSystemBaseline() {
  val clear=shadeBackdropPreviewState(true,0,0)
  assertEquals(0f,clear.blurRatio,0f);assertEquals(0f,clear.dimRatio,0f)
  val off=shadeBackdropPreviewState(false,0,0)
  assertEquals(1f,off.blurRatio,0f);assertEquals(1f,off.dimRatio,0f)
  assertEquals(1f,shadeBackdropPreviewState(true,100,0).blurRatio,0f)
  assertEquals(0f,shadeBackdropPreviewState(true,100,0).dimRatio,0f)
 }
}
