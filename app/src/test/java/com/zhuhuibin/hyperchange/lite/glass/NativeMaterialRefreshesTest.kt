// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import com.zhuhuibin.hyperchange.lite.scope.NotificationRole
import com.zhuhuibin.hyperchange.lite.scope.NotificationScopeToken
import org.junit.Assert.*
import org.junit.Test

class NativeMaterialRefreshesTest {
    private val unlocked = NotificationScopeToken(0, NotificationRole.ORDINARY)

    @Test fun firstEnableRefreshesANewBinderRootWithoutAnyPreviousSetterBaseline() {
        val refreshes = NativeMaterialRefreshes<Any, NotificationScopeToken>()
        val ordinary = Any()
        val media = Any()
        assertNotNull(refreshes.claim(ordinary, 1, unlocked))
        assertNotNull(refreshes.claim(media, 1, NotificationScopeToken(0, NotificationRole.MEDIA)))
    }

    @Test fun inventoryAndDelayedEngineEntryShareOneRequestForTheSameSnapshot() {
        val refreshes = NativeMaterialRefreshes<Any, NotificationScopeToken>()
        val root = Any()
        assertNotNull(refreshes.claim(root, 2, unlocked))
        assertNull(refreshes.claim(root, 2, unlocked))
        assertNull(refreshes.claim(root, 2, unlocked.copy()))
    }

    @Test fun aNewPresetOrSceneCanRecomputeTheSameRoot() {
        val refreshes = NativeMaterialRefreshes<Any, NotificationScopeToken>()
        val root = Any()
        assertNotNull(refreshes.claim(root, 3, unlocked))
        assertNotNull(refreshes.claim(root, 4, unlocked))
        assertNotNull(refreshes.claim(root, 4, unlocked.copy(generation = 1)))
        assertNotNull(refreshes.claim(root, 4, NotificationScopeToken(0, NotificationRole.HEADS_UP, 1)))
    }

    @Test fun aDeferredRequestCanRetryButItsLateFailureCannotEraseANewerRequest() {
        val refreshes = NativeMaterialRefreshes<Any, NotificationScopeToken>()
        val root = Any()
        val deferred = requireNotNull(refreshes.claim(root, 5, unlocked))
        refreshes.failed(root, deferred)
        val retried = requireNotNull(refreshes.claim(root, 5, unlocked))
        assertNotNull(refreshes.claim(root, 6, unlocked))
        refreshes.failed(root, retried)
        assertNull(refreshes.claim(root, 6, unlocked))
    }
}
