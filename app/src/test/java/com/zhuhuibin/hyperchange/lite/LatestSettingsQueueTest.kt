// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class LatestSettingsQueueTest {
    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.remove().run() }
    }

    @Test fun slowPublicationKeepsOnlyTheLatestValueForEveryKey() {
        val executor = ManualExecutor()
        val queue = LatestSettingsQueue<String>(executor) { throw AssertionError(it) }
        val writes = mutableListOf<String>()
        for (value in 1..100) queue.update("blur") { writes += "blur=$value" }
        queue.update("scale") { writes += "scale=20" }
        queue.update("opacity") { writes += "opacity=73" }
        queue.update("blur") { writes += "blur=101-final" }
        executor.drain()
        assertEquals(listOf("blur=101-final", "scale=20", "opacity=73"), writes)
    }

    @Test fun importResetAndExportAreBarriersBetweenEditBatches() {
        val executor = ManualExecutor()
        val queue = LatestSettingsQueue<String>(executor) { throw AssertionError(it) }
        var blur = 0
        val exported = mutableListOf<Int>()
        queue.update("blur") { blur = 20 }
        queue.execute { exported += blur }
        queue.execute { blur = 80 } // Import/reset applies before the next user edit.
        queue.update("blur") { blur = 90 }
        queue.update("blur") { blur = 100 }
        queue.execute { exported += blur }
        executor.drain()
        assertEquals(listOf(20, 100), exported)
    }

    @Test fun anEditArrivingDuringPublicationRetainsItsFinalValueAfterABarrier() {
        val executor = ManualExecutor()
        val queue = LatestSettingsQueue<String>(executor) { throw AssertionError(it) }
        val writes = mutableListOf<String>()
        queue.update("blur") {
            writes += "first"
            queue.update("blur") { writes += "before-import-final" }
            queue.execute { writes += "import" }
            queue.update("blur") { writes += "after-import" }
            queue.update("blur") { writes += "after-import-final" }
        }
        executor.drain()
        assertEquals(listOf("first", "before-import-final", "import", "after-import-final"), writes)
    }

    @Test fun oneFailedWriteDoesNotDiscardOtherKeysOrTheFinalEdit() {
        val executor = ManualExecutor()
        val errors = mutableListOf<Throwable>()
        val queue = LatestSettingsQueue<String>(executor) { errors += it }
        val writes = mutableListOf<String>()
        queue.update("blur") { throw IllegalStateException("storage unavailable") }
        queue.update("scale") { writes += "scale" }
        queue.execute { writes += "export" }
        queue.update("blur") { writes += "blur-final" }
        executor.drain()
        assertEquals(1, errors.size)
        assertEquals(listOf("scale", "export", "blur-final"), writes)
    }
}
