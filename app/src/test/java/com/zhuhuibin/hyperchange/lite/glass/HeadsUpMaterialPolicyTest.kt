// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import com.zhuhuibin.hyperchange.lite.preview.headsUpPreviewRecipe
import com.zhuhuibin.hyperchange.lite.preview.previewMaterialState
import org.junit.Assert.*
import org.junit.Test

class HeadsUpMaterialPolicyTest {
    @Test fun bannerConversionRequiresEveryIndependentPermission() {
        for (bits in 0 until 16) {
            val allowed = headsUpGlassAllowed(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0)
            assertEquals("Permission set $bits", bits == 15, allowed)
        }
    }

    @Test fun addingTheOptInDoesNotSilentlyChangeAnUpgradedConfiguration() {
        val existing = GlassRuntimeSettings.from(mapOf("glass_enabled" to true, "glass_notifications" to true,
            "glass_unify_notifications" to true))
        assertFalse(existing.headsUp)
        assertTrue(existing.unify)
    }

    @Test fun previewSelectionUsesTheSameMasterRangeAndExplicitOptIn() {
        val on = mapOf("glass_heads_up" to true, "glass_notifications" to true)
        val saved = SoftGlassSettings(enabled = true)
        assertTrue(previewMaterialState("HEADS_UP", on, saved, true).headsUpGlass)
        assertFalse(previewMaterialState("HEADS_UP", emptyMap<String, Any>(), saved, true).headsUpGlass)
        assertFalse(previewMaterialState("HEADS_UP", on, saved.copy(enabled = false), true).headsUpGlass)
        assertFalse(previewMaterialState("HEADS_UP", on + ("glass_notifications" to false), saved, true).headsUpGlass)
        assertFalse(previewMaterialState("NOTIFICATION", on, saved, true).headsUpGlass)
    }

    @Test fun nativeBannerPreviewKeepsItsOwnVerifiedLightAndDarkBaselines() {
        val day = headsUpPreviewRecipe(false)
        val night = headsUpPreviewRecipe(true)
        assertTrue(hasSoftGlassRecipe(day))
        assertTrue(hasSoftGlassRecipe(night))
        assertEquals(42, day.size)
        assertEquals(42, night.size)
        assertEquals(1.5f, day[14], 0f)
        assertEquals(.6f, night[14], 0f)
        // The same saved +110% is much denser on the OEM light banner than on
        // a normal resource token whose native slot 14 is 0.10.
        val adjusted = tuneSoftGlass(day, SoftGlassSettings(enabled = true, adjustments = mapOf("alpha" to 110)))
        assertEquals(2f, adjusted[14], 0f)
        day[14] = 0f
        assertEquals(1.5f, headsUpPreviewRecipe(false)[14], 0f)
    }
}
