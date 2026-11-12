// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import org.junit.Assert.*
import org.junit.Test

class NotificationMaterialPolicyTest {
    @Test fun mediaGateAlsoCoversForcedRecipeWithTuningOff() {
        val policy = NotificationMaterialPolicy(ordinary = true, media = false, tuning = false, unify = true)
        for (scene in listOf(false, true)) {
            assertFalse(policy.range(isMedia = true, scene))
            assertFalse(policy.force(isMedia = true, scene, headsUp = false, focus = false))
        }
        assertTrue(policy.force(isMedia = false, sceneAllowed = true, headsUp = false, focus = true))
    }

    @Test fun independentMusicRangeBypassesOrdinaryLockscreenRange() {
        val policy = NotificationMaterialPolicy(ordinary = false, media = true, tuning = true, unify = true)
        assertTrue(policy.tune(isMedia = true, sceneAllowed = false))
        assertTrue(policy.force(isMedia = true, sceneAllowed = false, headsUp = false, focus = false))
        assertFalse(policy.tune(isMedia = false, sceneAllowed = true))
    }

    @Test fun ordinaryGateCoversTuningAndUnificationWhileHeadsUpKeepsItsRecipe() {
        val policy = NotificationMaterialPolicy(true, true, true, true)
        assertFalse(policy.tune(isMedia = false, sceneAllowed = false))
        assertFalse(policy.force(isMedia = false, sceneAllowed = false, headsUp = false, focus = true))
        // The scene resolver explicitly permits heads-up independently of keyguard.
        assertTrue(policy.tune(isMedia = false, sceneAllowed = true))
        assertFalse(policy.force(isMedia = false, sceneAllowed = true, headsUp = true, focus = true))
    }

    @Test fun allPermissionCombinationsObeyRangeBeforeFeatureFlags() {
        for (ordinary in listOf(false, true)) for (media in listOf(false, true))
            for (tuning in listOf(false, true)) for (unify in listOf(false, true))
                for (isMedia in listOf(false, true)) for (scene in listOf(false, true)) {
                    val policy = NotificationMaterialPolicy(ordinary, media, tuning, unify)
                    val allowed = (if (isMedia) media else ordinary) && (isMedia || scene)
                    assertEquals(allowed && tuning, policy.tune(isMedia, scene))
                    assertEquals(allowed && unify, policy.force(isMedia, scene, headsUp = false, focus = true))
                }
    }

    @Test fun queuedWorkDoesNotSurviveDisableThenReenableOrNewScene() {
        val generation = MaterialGeneration()
        val old = generation.value
        assertTrue(generation.current(old, true))
        generation.invalidate()
        assertFalse(generation.current(old, false))
        generation.invalidate()
        assertFalse(generation.current(old, true))
        val fresh = generation.value
        assertFalse(generation.current(fresh, false))
        assertTrue(generation.current(fresh, true))
    }

    @Test fun oemNotificationMediaIsDistinctFromPluginAndIslandMedia() {
        assertTrue(GlassSurfaces.mediaOwner("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView"))
        assertFalse(GlassSurfaces.mediaOwner("miui.systemui.controlcenter.panel.main.media.MediaPlayerPanel"))
        assertFalse(GlassSurfaces.mediaOwner("com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaView"))
        assertFalse(GlassSurfaces.mediaOwner("com.zhuhuibin.hypermusiccover.duo.MiuiMediaOverlay"))
    }

    @Test fun foreignWriteRevokesQueuedReleaseWithoutDependingOnBackgroundIdentity() {
        val clock = MaterialWriteClock<Any>()
        val mediaRoot = Any()
        val anotherRoot = Any()
        clock.touch(mediaRoot)
        val release = clock.ticket(mediaRoot)
        // Neutral blur/blend setters in the same admitted OEM pass retain ownership
        // of the already tuned recipe; they must not suppress its eventual cleanup.
        clock.observe(mediaRoot, permitted = true)
        clock.observe(mediaRoot, permitted = true)
        assertTrue(clock.current(release))
        clock.touch(anotherRoot)
        assertTrue(clock.current(release))
        // A disabled setter still observes HMC's new write on the same drawable.
        clock.observe(mediaRoot, permitted = false)
        assertFalse(clock.current(release))
        val newOwnership = clock.ticket(mediaRoot)
        assertTrue(clock.current(newOwnership))
    }

    @Test fun releaseSurvivesCancelledFrameThenOrdinaryParameterEdit() {
        val releases = MaterialReleaseLedger<Any>()
        val root = Any()
        var enabled = false
        var stillOwned = true
        val guard = { !enabled && stillOwned }
        releases.offer(root, guard)
        // An unrelated ordinary-notification edit cancels the old Runnable. The
        // ownership ledger is separate, so the next frame must deliver it once.
        val requeued = releases.snapshot().getValue(root)
        assertTrue(releases.take(root, requeued))
        assertFalse(releases.take(root, requeued))
        releases.offer(root, guard)
        enabled = true
        assertFalse(releases.take(root, guard))
        enabled = false
        releases.offer(root, guard)
        stillOwned = false
        assertFalse(releases.take(root, guard))
    }

    @Test fun disablingBeforeAnAdmittedRefreshDrainsStillTransfersTuningAndRecipeOwnership() {
        // Both the setter baseline and a forced-recipe owner use this same retention
        // decision. Neither may disappear just because a current-state refresh queued.
        for (owner in listOf("tuned setter", "forced recipe")) {
            val generation = MaterialGeneration()
            val releases = MaterialReleaseLedger<String>()
            var retained = true
            val queuedRefresh = generation.value
            if (canForgetMaterialState(allowed = true, stillOwned = true, releaseTransferred = true)) retained = false
            generation.invalidate() // User disables before the admitted refresh runs.
            assertFalse(generation.current(queuedRefresh, permitted = false))
            assertTrue("$owner must survive cancellation", retained)
            val release = { true }
            releases.offer(owner, release)
            if (canForgetMaterialState(allowed = false, stillOwned = retained, releaseTransferred = true)) retained = false
            assertFalse(retained)
            assertTrue(releases.take(owner, release))
            assertFalse(releases.take(owner, release))
        }
        // A missing current binder does not silently drop valid release ownership.
        assertFalse(canForgetMaterialState(allowed = false, stillOwned = true, releaseTransferred = false))
        assertTrue(canForgetMaterialState(allowed = false, stillOwned = false, releaseTransferred = false))
    }
}
