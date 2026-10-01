package com.elysium369.meet.core.mesh

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MeshLinkBudgetTest {
    @Test fun pendingAndConnectedLinksShareAtomicCapacityUnderConcurrentAttempts() {
        val budget = MeshLinkBudget(4)
        val start = CountDownLatch(1); val attempted = CountDownLatch(32); val release = CountDownLatch(1)
        val admitted = AtomicInteger()
        val workers = (0 until 32).map {
            thread {
                start.await()
                val lease = budget.reserve()
                if (lease != null) admitted.incrementAndGet()
                attempted.countDown()
                release.await()
                lease?.close(); lease?.close() // Duplicate cleanup must never create an extra permit.
            }
        }
        try { start.countDown(); assertTrue(attempted.await(5, TimeUnit.SECONDS)); assertEquals(4, admitted.get()); assertNull(budget.reserve()) }
        finally { release.countDown(); workers.forEach { it.join(5000) } }
        val recovered = (0 until 4).map { budget.reserve().also { assertNotNull(it) } }
        assertNull(budget.reserve()); recovered.forEach { it?.close() }
    }
    @Test fun failedConnectReleaseMakesExactlyOneSlotAvailable() {
        val budget = MeshLinkBudget(1); val failed = budget.reserve()!!
        assertNull(budget.reserve()); failed.close(); failed.close()
        val retried = budget.reserve(); assertNotNull(retried); assertNull(budget.reserve()); retried?.close()
    }
}
