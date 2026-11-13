// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import org.junit.Assert.*
import org.junit.Test

class SettingsEditDecisionTest {
    @Test fun coalescedOffEditStillClaimsFreshLocalDefaultsBeforeRemoteBinding() {
        // Migration wrote this default but has not established user-owned local state.
        val migrated = mapOf("glass_shade_enabled" to false)
        assertFalse(SettingsMigration.hasLocalState(SettingsMigration.resolve(emptyMap<String, Any>())))
        assertFalse(canSkipSettingsEdit(false, migrated.containsKey("glass_shade_enabled"),
            migrated["glass_shade_enabled"], false))
        assertTrue(canSkipSettingsEdit(true, true, false, false))
    }

    @Test fun absentOrDifferentValuesMustStillPublishAfterInitialization() {
        assertFalse(canSkipSettingsEdit(true, false, null, false))
        assertFalse(canSkipSettingsEdit(true, true, 50f, 100f))
        assertTrue(canSkipSettingsEdit(true, true, 100f, 100f))
    }
}
