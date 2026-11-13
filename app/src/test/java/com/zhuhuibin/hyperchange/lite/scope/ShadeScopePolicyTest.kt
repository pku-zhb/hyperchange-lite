// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.scope

import org.junit.Assert.*
import org.junit.Test

class ShadeScopePolicyTest {
    private enum class Kind { NOTIFICATION, CONTROL_CENTER }
    @Test fun pinnedOrAnimatingHeadsUpWithoutPanelExpansionCannotAuthorizeTheWindow() {
        // Native isExpanded() is true in these states; the actual fraction remains zero.
        assertFalse(ShadeScopePolicy.notification(0f, false, 0, true))
        assertFalse(ShadeScopePolicy.notification(0f, true, 0, true))
        assertTrue(ShadeScopePolicy.notification(.01f, true, 0, true))
    }

    @Test fun trackingInstantExpansionAndLightRevealNeedAPositiveRealFraction() {
        assertFalse(ShadeScopePolicy.notification(0f, true, 0, true))
        assertFalse(ShadeScopePolicy.notification(.5f, true, 0, false))
        assertFalse(ShadeScopePolicy.notification(.5f, true, 1, true))
        assertFalse(ShadeScopePolicy.notification(.5f, false, 0, true))
    }

    @Test fun unknownAndNonFiniteExpansionEvidenceAlwaysStaysNative() {
        for (fraction in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 0f)) {
            assertFalse(ShadeScopePolicy.notification(fraction, true, 0, true))
            assertFalse(ShadeScopePolicy.controlCenter(fraction, true))
        }
        assertFalse(ShadeScopePolicy.notification(.5f, null, 0, true))
        assertFalse(ShadeScopePolicy.notification(.5f, true, null, true))
    }

    @Test fun controlCenterRemainsActiveDuringItsPositiveCollapseFraction() {
        // Appearance is intentionally absent: it becomes false before expansion reaches zero.
        for (fraction in listOf(1f, .5f, .001f)) assertTrue(ShadeScopePolicy.controlCenter(fraction, true))
        assertFalse(ShadeScopePolicy.controlCenter(0f, true))
        assertFalse(ShadeScopePolicy.controlCenter(.5f, false))
    }

    @Test fun expansionOfAnotherWindowNeverAuthorizesThisRoot() {
        val activeRoot = Any()
        val inactiveRoot = Any()
        assertTrue(ShadeScopePolicy.owns(activeRoot, activeRoot, activeRoot, true, true, false, false))
        assertFalse(ShadeScopePolicy.owns(inactiveRoot, activeRoot, activeRoot, true, true, false, false))
        assertFalse(ShadeScopePolicy.owns(inactiveRoot, inactiveRoot, inactiveRoot, true, false, false, false))
    }

    @Test fun rootReplacementDetachAndLifecycleShutdownRevokePreviouslyActiveOwnership() {
        val oldRoot = Any()
        val newRoot = Any()
        assertFalse(ShadeScopePolicy.owns(oldRoot, oldRoot, newRoot, true, true, false, false))
        assertFalse(ShadeScopePolicy.owns(newRoot, oldRoot, newRoot, true, true, false, false))
        assertFalse(ShadeScopePolicy.owns(oldRoot, oldRoot, oldRoot, false, true, false, false))
        assertFalse(ShadeScopePolicy.owns(oldRoot, oldRoot, oldRoot, true, true, true, false))
        assertFalse(ShadeScopePolicy.owns(oldRoot, oldRoot, oldRoot, true, true, false, true))
        assertFalse(ShadeScopePolicy.owns(null, null, null, true, true, false, false))
    }

    @Test fun rootIdentityCannotBeReplacedByStructuralEquality() {
        data class Root(val display: Int)
        val first = Root(0)
        val second = Root(0)
        assertEquals(first, second)
        assertFalse(ShadeScopePolicy.owns(second, first, first, true, true, false, false))
    }

    @Test fun positiveAnimationFramesKeepOneEpochUntilARealScopeChange() {
        val lease = ShadeScopeLease()
        assertFalse(lease.sample(valid = true, openSignal = false))
        assertFalse(lease.active)
        assertTrue(lease.sample(valid = true, openSignal = true))
        repeat(30) {
            assertFalse(lease.sample(valid = true, openSignal = true))
            assertFalse(lease.sample(valid = true, openSignal = false))
        }
        assertTrue(lease.sample(valid = false, openSignal = true))
        assertFalse(lease.active)
        assertFalse(lease.sample(valid = false, openSignal = false))
    }

    @Test fun shutdownAndReattachmentCannotReviveAStalePositiveGetter() {
        val lease = ShadeScopeLease()
        assertTrue(lease.sample(valid = true, openSignal = true))
        assertTrue(lease.revoke())
        assertFalse(lease.revoke())
        // The getter can remain positive after CC onStop or a root replacement.
        assertFalse(lease.sample(valid = true, openSignal = false))
        assertFalse(lease.active)
        // A nested setter inside the close transaction still cannot open ownership.
        assertFalse(lease.sample(valid = false, openSignal = true))
        assertFalse(lease.active)
        assertTrue(lease.sample(valid = true, openSignal = true))
        assertTrue(lease.active)
    }

    @Test fun notificationAndControlCenterOwnersSurviveInsideTheSameWindow() {
        val root = Any()
        val otherRoot = Any()
        val notifications = Any()
        val controlCenter = Any()
        val registry = ShadeScopeRegistry<Any, Kind, Any>()
        assertTrue(registry.bind(root, Kind.NOTIFICATION, notifications).accepted)
        val binding = registry.bind(root, Kind.CONTROL_CENTER, controlCenter)
        assertTrue(binding.accepted)
        assertNull(binding.replaced)
        assertSame(notifications, registry.owner(root, Kind.NOTIFICATION))
        assertSame(controlCenter, registry.owner(root, Kind.CONTROL_CENTER))
        assertEquals(setOf(notifications, controlCenter), registry.owners(root).toSet())
        assertTrue(registry.owners(otherRoot).isEmpty())
        assertTrue(registry.remove(root, Kind.NOTIFICATION, notifications))
        assertSame(controlCenter, registry.owner(root, Kind.CONTROL_CENTER))
        assertEquals(listOf(controlCenter), registry.owners(root))
    }

    @Test fun sameKindReplacementRetiresOnlyItsOwnOldControllerAndRejectsLateCallbacks() {
        val root = Any()
        val oldNotifications = Any()
        val newNotifications = Any()
        val controlCenter = Any()
        val registry = ShadeScopeRegistry<Any, Kind, Any>()
        registry.bind(root, Kind.NOTIFICATION, oldNotifications)
        registry.bind(root, Kind.CONTROL_CENTER, controlCenter)
        val replacement = registry.bind(root, Kind.NOTIFICATION, newNotifications)
        assertSame(oldNotifications, replacement.replaced)
        assertSame(controlCenter, registry.owner(root, Kind.CONTROL_CENTER))
        assertFalse(registry.bind(root, Kind.NOTIFICATION, oldNotifications).accepted)
        assertFalse(registry.remove(root, Kind.NOTIFICATION, oldNotifications))
        assertSame(newNotifications, registry.owner(root, Kind.NOTIFICATION))
        assertSame(controlCenter, registry.owner(root, Kind.CONTROL_CENTER))
        assertFalse(registry.bind(root, Kind.CONTROL_CENTER, controlCenter).changed)
    }
}
