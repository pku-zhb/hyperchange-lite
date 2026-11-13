package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.preview.previewBehavior
import org.junit.Assert.*
import org.junit.Test

class PreviewBehaviorTest {
    @Test fun openingPreviewDoesNotPretendDisabledEffectsAreEnabled() {
        val state = previewBehavior(emptyMap<String, Any>())
        assertFalse(state.tiltFor("NOTIFICATION"))
        assertFalse(state.tiltFor("MEDIA"))
        assertFalse(state.headsUp)
        assertFalse(state.stack)
        assertFalse(state.group)
        assertFalse(state.volume)
    }

    @Test fun rowAndMediaPressRangesStayIndependent() {
        val state = previewBehavior(mapOf(
            "notification_press_tilt_enabled" to true,
            "notification_press_tilt_row_enabled" to false,
        ))
        assertFalse(state.tiltFor("NOTIFICATION"))
        assertFalse(state.tiltFor("HEADS_UP"))
        assertTrue(state.tiltFor("MEDIA"))
        assertFalse(state.tiltFor("CONTROL"))
    }

    @Test fun animationSwitchesDoNotEnableUnrelatedPreviews() {
        val state = previewBehavior(mapOf("volume_motion_enabled" to true, "notification_heads_up_pop_enabled" to true))
        assertTrue(state.volume)
        assertTrue(state.headsUp)
        assertFalse(state.stack)
        assertFalse(state.group)
        assertFalse(state.tiltFor("NOTIFICATION"))
    }
}
