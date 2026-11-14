// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class NativeNotificationRoleTest {
    class Flow(var current: Boolean, var failing: Boolean = false) {
        fun getValue(): Any { check(!failing); return current }
    }
    class Readonly(val flow: Flow)
    class Model(val headUpState: Readonly, val hasCalledHUNDragDownDisappear: Readonly)
    class Injector(var viewModel: Any?)
    class DisplayRow(val injector: Injector) {
        // The display flag can outlive the current material selector's two flows.
        fun isHeadsUpState(): Boolean = true
    }

    @Test fun oldDisappearanceFlagDoesNotKeepAnOrdinaryRowInTheBannerMaterialScope() {
        val showing = Flow(false)
        val dragged = Flow(false)
        val row = DisplayRow(Injector(Model(Readonly(showing), Readonly(dragged))))
        val accessors = SurfaceRoleAccessors()
        assertEquals(true, accessors.headsUp(row))
        assertEquals(false, accessors.materialHeadsUp(row))
        showing.current = true
        assertEquals(true, accessors.materialHeadsUp(row))
        showing.current = false
        dragged.current = true
        assertEquals(true, accessors.materialHeadsUp(row))
        dragged.current = false
        assertEquals(false, accessors.materialHeadsUp(row))
        assertEquals(true, accessors.headsUp(row))
    }

    @Test fun failedOrUnsupportedFlowReadsRemainConservativeAndAreNotValueCached() {
        val showing = Flow(false, failing = true)
        val dragged = Flow(false)
        val row = DisplayRow(Injector(Model(Readonly(showing), Readonly(dragged))))
        val accessors = SurfaceRoleAccessors()
        assertEquals(true, accessors.materialHeadsUp(row))
        showing.failing = false
        assertEquals(false, accessors.materialHeadsUp(row))
        row.injector.viewModel = Any()
        assertEquals(true, accessors.materialHeadsUp(row))
    }

    @Test fun missingInflationModelMatchesTheNativeSelectorsOrdinaryRow() {
        assertEquals(false, NativeNotificationRole().headsUp(Injector(null)))
    }
}
