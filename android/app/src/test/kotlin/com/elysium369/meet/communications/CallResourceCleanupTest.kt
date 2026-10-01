package com.elysium369.meet.communications

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallResourceCleanupTest {
    @Test fun unavailableNetworkCannotKeepHardwareRunning() = runTest {
        val events = mutableListOf<String>()
        cleanupCallResources({ events += "hardware stopped" }) {
            events += "network started"
            awaitCancellation()
        }
        assertEquals(listOf("hardware stopped", "network started"), events)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test fun remoteFailureDoesNotUndoLocalTeardown() = runTest {
        var stopped = 0
        cleanupCallResources({ stopped++ }) { error("Network unavailable") }
        assertEquals(1, stopped)
    }

    @Test fun cancelledSessionStillReleasesLocalResources() = runTest {
        var stopped = false
        val session = launch {
            try { awaitCancellation() }
            finally { cleanupCallResources({ stopped = true }) { awaitCancellation() } }
        }
        testScheduler.runCurrent()
        session.cancelAndJoin()
        assertTrue(stopped)
        assertTrue(session.isCancelled)
        assertEquals(5_000L, testScheduler.currentTime)
    }
}
