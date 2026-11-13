// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class LockscreenRowMaterialPolicyTest {
    @Test fun currentWallpaperStateSelectsTheMatchingKeyguardRecipe() {
        assertEquals("NotificationRowGlassOnKeyguardEffect",
            LockscreenRowMaterialPolicy.effect(true, true, false, false, false))
        assertEquals("NotificationRowGlassOnKeyguardLightWallPaperEffect",
            LockscreenRowMaterialPolicy.effect(true, true, true, false, false))
    }

    @Test fun lockedGlobalStateAloneCannotOverrideAnUnlockedNativeRow() {
        assertNull(LockscreenRowMaterialPolicy.effect(true, false, false, false, false))
        assertNull(LockscreenRowMaterialPolicy.effect(true, null, false, false, false))
        assertNull(LockscreenRowMaterialPolicy.effect(false, true, false, false, false))
    }

    @Test fun fullAodAndItsTransitionNeverAcquireAForcedKeyguardRecipe() {
        assertNull(LockscreenRowMaterialPolicy.effect(true, true, false, true, false))
        assertNull(LockscreenRowMaterialPolicy.effect(true, true, false, false, true))
        assertNull(LockscreenRowMaterialPolicy.effect(true, true, true, true, true))
    }

    @Test fun unknownWallpaperOrAodSignalsKeepTheNativeEffect() {
        assertNull(LockscreenRowMaterialPolicy.effect(true, true, null, false, false))
        assertNull(LockscreenRowMaterialPolicy.effect(true, true, false, null, false))
        assertNull(LockscreenRowMaterialPolicy.effect(true, true, false, false, null))
    }

    @Test fun anAodNoOpSelectorDoesNotRevokeResponsibilityForAnExistingConversion() {
        val writes = MaterialWriteClock<Any>()
        val row = Any()
        val own = writes.ticket(row)
        if (LockscreenRowMaterialPolicy.selectorMayApply(true, true, false)) writes.touch(row)
        assertTrue(writes.current(own))
        if (LockscreenRowMaterialPolicy.selectorMayApply(null, null, null)) writes.touch(row)
        assertTrue(writes.current(own))
        if (LockscreenRowMaterialPolicy.selectorMayApply(false, false, false)) writes.touch(row)
        assertFalse(writes.current(own))
    }

    @Test fun theNativeGroupedChildExceptionStillAdvancesMaterialOwnership() {
        assertTrue(LockscreenRowMaterialPolicy.selectorMayApply(true, false, true))
        assertFalse(LockscreenRowMaterialPolicy.selectorMayApply(true, false, false))
        assertFalse(LockscreenRowMaterialPolicy.selectorMayApply(true, null, true))
    }
}
