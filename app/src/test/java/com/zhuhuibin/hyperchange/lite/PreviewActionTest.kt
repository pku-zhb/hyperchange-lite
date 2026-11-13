package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.preview.PreviewAction
import com.zhuhuibin.hyperchange.lite.preview.previewActionFor
import org.junit.Assert.*
import org.junit.Test

class PreviewActionTest {
    @Test fun editingExitParametersDoesNotReplayAnEntryInstead() {
        assertEquals(PreviewAction.HEADS_UP_EXIT, previewActionFor("notification_heads_up_pop_exit_damping"))
        assertEquals(PreviewAction.ROW_EXIT, previewActionFor("notification_stack_anim_remove_alpha"))
        assertEquals(PreviewAction.HEADS_UP_ENTER, previewActionFor("notification_heads_up_pop_blur"))
        assertEquals(PreviewAction.ROW_ENTER, previewActionFor("notification_stack_anim_add_duration"))
    }

    @Test fun volumeWidthPopAndNudgeUseTheirOwnEffects() {
        assertEquals(PreviewAction.VOLUME_WIDTH, previewActionFor("volume_thin_width"))
        assertEquals(PreviewAction.VOLUME_POP, previewActionFor("volume_pop_scale"))
        assertEquals(PreviewAction.VOLUME_NUDGE, previewActionFor("volume_nudge_response"))
        assertEquals(PreviewAction.STATIC, previewActionFor("volume_offset_y"))
    }

    @Test fun materialTuningDoesNotTriggerUnrelatedNotificationOrVolumeMotion() {
        assertEquals(PreviewAction.STATIC, previewActionFor("glass_refraction"))
        assertEquals(PreviewAction.STATIC, previewActionFor("notification_radius"))
        assertEquals(PreviewAction.GROUP, previewActionFor("notification_group_stack_scale"))
        assertEquals(PreviewAction.PRESS, previewActionFor("notification_press_tilt_max_deg"))
    }
}
