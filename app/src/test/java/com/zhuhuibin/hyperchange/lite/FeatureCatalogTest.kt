package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.ui.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureCatalogTest {
    @Test fun everyExistingSettingIsReachableThroughExactlyOneFeature() {
        val destinations = Feature.entries.flatMap { it.settings }
        assertTrue(Feature.entries.all { it.settings.isNotEmpty() })
        assertEquals(LiteCatalog.entries.map { it.key }.toSet(), destinations.map { it.key }.toSet())
        assertEquals(LiteCatalog.entries.size, destinations.size)
    }

    @Test(expected = IllegalStateException::class)
    fun newUnassignedSettingsCannotDisappearSilentlyFromTheApp() {
        Feature.owner(LiteSetting.toggle("unassigned_feature", "New setting", "New group"))
    }
}
