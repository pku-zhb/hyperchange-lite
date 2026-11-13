// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class MaterialRefreshPlanTest {
    @Test fun cardPresetAndSceneSignalCannotRequestAWholeWindowReplay() {
        val plan = MaterialRefreshPlan()
        plan.include(sceneOnly = false, cards = true, backdrops = false)
        plan.include(sceneOnly = true, cards = true, backdrops = true)
        val request = plan.take()
        assertTrue(request.cards)
        assertTrue(request.notifications)
        assertFalse(request.backdrops)
    }

    @Test fun coalescedIndependentEditsRetainBothLayersInEitherOrder() {
        for (cardFirst in listOf(false, true)) {
            val plan = MaterialRefreshPlan()
            plan.include(sceneOnly = false, cards = cardFirst, backdrops = !cardFirst)
            plan.include(sceneOnly = false, cards = !cardFirst, backdrops = cardFirst)
            assertEquals(MaterialRefreshPlan.Request(true, true, false), plan.take())
            assertEquals(MaterialRefreshPlan.Request(false, false, false), plan.take())
        }
    }

    @Test fun sceneAndWholeEditsDoNotPromoteCardGeometryRefreshes() {
        val plan = MaterialRefreshPlan()
        plan.include(sceneOnly = false, cards = false, backdrops = true)
        plan.include(sceneOnly = true, cards = true, backdrops = true)
        val request = plan.take()
        assertTrue(request.backdrops)
        assertTrue(request.notifications)
        assertFalse(request.cards)
    }
}
