// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import org.junit.Assert.*
import org.junit.Test

class BannerContrastPolicyTest {
    @Test fun brightAndDarkActualSamplesChooseOppositeOpaqueInkRoles() {
        val dark = BannerContrastPolicy()
        val light = BannerContrastPolicy()
        assertEquals(BannerContrastPolicy.Tone.LIGHT_TEXT, dark.select(.1f, 0))
        assertEquals(BannerContrastPolicy.Tone.DARK_TEXT, light.select(.9f, 0))
        for (tone in BannerContrastPolicy.Tone.entries) {
            val primary = BannerContrastPolicy.color(tone, true)
            val secondary = BannerContrastPolicy.color(tone, false)
            assertNotEquals(primary, secondary)
            assertEquals(255, primary ushr 24)
            assertEquals(255, secondary ushr 24)
            assertNotEquals(BannerContrastPolicy.shadow(primary) and 0xffffff, primary and 0xffffff)
        }
    }

    @Test fun aBusyBackgroundNearTheBoundaryCannotAlternateInkEverySample() {
        val policy = BannerContrastPolicy()
        policy.select(.1f, 0)
        for (i in 1..100) assertEquals(BannerContrastPolicy.Tone.LIGHT_TEXT,
            policy.select(if (i % 2 == 0) .47f else .51f, i * 750L))
        assertEquals(BannerContrastPolicy.Tone.DARK_TEXT, policy.select(.9f, 76_000))
        for (i in 1..100) assertEquals(BannerContrastPolicy.Tone.DARK_TEXT,
            policy.select(if (i % 2 == 0) .44f else .5f, 76_000 + i * 750L))
    }

    @Test fun aRealBackgroundSwitchStillHonorsTheMinimumHold() {
        val policy = BannerContrastPolicy()
        policy.select(.1f, 1000)
        assertEquals(BannerContrastPolicy.Tone.LIGHT_TEXT, policy.select(.9f, 1599))
        assertEquals(BannerContrastPolicy.Tone.DARK_TEXT, policy.select(.9f, 1600))
        assertEquals(BannerContrastPolicy.Tone.DARK_TEXT, policy.select(.1f, 1601))
        assertEquals(BannerContrastPolicy.Tone.LIGHT_TEXT, policy.select(.1f, 2200))
    }

    @Test fun missingInvalidOrNonFiniteSamplesNeverPretendToEstablishContrast() {
        val policy = BannerContrastPolicy()
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f)) assertNull(policy.select(value, 1000))
        policy.select(.9f, 2000)
        assertEquals(BannerContrastPolicy.Tone.DARK_TEXT, policy.select(Float.NaN, 4000))
    }

    @Test fun onlyNativeTemplateTextIdsReceiveRolesNotIconsActionsOrCustomAppViews() {
        assertEquals(true, BannerContrastPolicy.role("android", "title"))
        assertEquals(false, BannerContrastPolicy.role("android", "text"))
        assertEquals(false, BannerContrastPolicy.role("com.android.systemui", "app_name_text"))
        for (id in listOf("icon", "right_icon", "action0", "action_list", "reply", "progress", "media_actions"))
            assertNull(BannerContrastPolicy.role("android", id))
        assertNull(BannerContrastPolicy.role("example.app", "title"))
    }

    @Test fun nativeRewritesReplaceTheBaselineInsteadOfRestoringStaleTextColorOrShadow() {
        val oldNative = Any(); val currentNative = Any(); val owned = Any(); val foreign = Any()
        val lease = BannerOwnedValue(oldNative)
        lease.wrote(owned)
        assertSame(oldNative, lease.release(owned) { a, b -> a === b })
        assertSame(foreign, lease.release(foreign) { a, b -> a === b })
        lease.nativeWrite(currentNative)
        assertSame(owned, lease.release(owned) { a, b -> a === b })
        lease.wrote(owned)
        assertSame(currentNative, lease.release(owned) { a, b -> a === b })
    }

    @Test fun dismissedOrReplacedBannerSamplesCannotJoinANewSamplingWindow() {
        val window = BannerSampleWindow()
        val old = window.start(1000)
        assertTrue(window.collect(old, .9f, 1010))
        window.cancel()
        assertFalse(window.collect(old, .1f, 1100))
        assertNull(window.finish(old))
        val next = window.start(1750)
        assertFalse(window.collect(old, .1f, 1760))
        assertTrue(window.collect(next, .8f, 1760))
        assertEquals(.8f, window.finish(next)!!, 0f)
        assertFalse(window.collect(next, .1f, 1770))
    }

    @Test fun oneBurstHasAFiniteTimeAreaIndependentBudgetAndAtMostEightScalars() {
        val window = BannerSampleWindow()
        assertEquals(0L, window.delay(0))
        val ticket = window.start(1000)
        assertEquals(750L, window.delay(1000))
        assertEquals(1L, window.delay(1749))
        assertFalse(window.collect(ticket, .5f, 999))
        assertFalse(window.collect(ticket, .5f, 1251))
        assertFalse(window.collect(ticket, Float.NaN, 1010))
        for (i in 0..7) assertTrue(window.collect(ticket, i / 10f, 1010L + i))
        assertFalse(window.collect(ticket, 1f, 1100))
        assertEquals(.4f, window.finish(ticket)!!, .0001f)
        assertNull(window.finish(ticket))
        assertEquals(0L, window.delay(1750))
    }
}
