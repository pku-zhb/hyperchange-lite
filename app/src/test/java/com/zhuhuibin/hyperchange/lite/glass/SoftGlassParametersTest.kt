// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class SoftGlassParametersTest {
    private fun recipe() = FloatArray(42).apply { this[4] = 0.5f; this[24] = 0.8f; this[32] = 1.3f; this[34] = -0.2f }

    @Test fun transparencyAndInvalidRecipesStayNative() {
        val off = SoftGlassSettings(enabled = false, adjustments = mapOf("luminance" to 200))
        val source = recipe()
        assertSame(source, tuneSoftGlass(source, off))
        val zero = FloatArray(42)
        assertSame(zero, tuneSoftGlass(zero, off.copy(enabled = true)))
        val invalid = recipe().apply { this[20] = Float.NaN }
        assertSame(invalid, tuneSoftGlass(invalid, off.copy(enabled = true)))
        assertEquals(0, softGlassBlur(0, 200))
        assertEquals(0, softGlassColorAlpha(0x00123456, 200) ushr 24)
    }

    @Test fun parametersUseUpstreamRelativeMathAndInverseFeather() {
        val source = recipe()
        val output = tuneSoftGlass(source, SoftGlassSettings(enabled = true, adjustments = mapOf("luminance" to 50, "edge_feather" to 50)))
        assertEquals(0.75f, output[4], 0.0001f)
        assertEquals(0.4f, output[24], 0.0001f)
        assertEquals(0.5f, source[4], 0f)
        assertEquals(1.3f, output[32], 0f)
    }

    @Test fun zeroFallbackUsesHundredthsAndBoundsRemainFinite() {
        val source = recipe().apply { this[6] = 0f }
        val output = tuneSoftGlass(source, SoftGlassSettings(enabled = true, adjustments = mapOf("brightness" to 20, "refraction" to 200)))
        assertEquals(0.2f, output[6], 0f)
        assertEquals(3.9f, output[32], 0.0001f)
        assertTrue(output.all(Float::isFinite))
        assertEquals(1f, softGlassScale(0.8f, 200), 0f)
        assertEquals(255, softGlassColorAlpha(0xff123456.toInt(), 200) ushr 24)
        assertEquals(0x00123456, softGlassColorAlpha(0x80123456.toInt(), 50) and 0x00ffffff)
    }

    @Test fun recipeCacheDoesNotCompoundItsOwnOutputAndTracksNativeAnimation() {
        val cache = SoftGlassRecipeCache()
        val settings = SoftGlassSettings(enabled = true, adjustments = mapOf("luminance" to 100))
        val source = recipe()
        val first = cache.tune(source, settings)
        val again = cache.tune(first, settings)
        assertEquals(1f, again[4], 0f)
        assertEquals(0.5f, cache.baseline!![4], 0f)
        source[4] = 0.4f
        val nativeFrame = cache.tune(source, settings)
        assertEquals(0.8f, nativeFrame[4], 0.0001f)
        val restored = cache.tune(nativeFrame, settings.copy(enabled = false))
        assertEquals(0.4f, restored[4], 0f)
    }
}
