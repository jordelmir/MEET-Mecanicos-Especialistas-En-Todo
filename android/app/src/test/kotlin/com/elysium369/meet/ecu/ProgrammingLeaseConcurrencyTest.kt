package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.lease.LeaseAcquisitionResult
import com.elysium369.meet.ecu.lease.ProgrammingLeaseManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Section 146 Critical Test: Concurrent Programmers Race.
 * Simulates N concurrent execution actors (Android UI, Desktop Agent, Background Worker, etc.)
 * racing to acquire the single active programming lease on the same ECU.
 */
class ProgrammingLeaseConcurrencyTest {

    @Before
    @After
    fun setup() {
        ProgrammingLeaseManager.clearAllForTest()
    }

    @Test
    fun `concurrent lease acquisition race yields exactly 1 granted lease and N minus 1 rejections`() {
        val nThreads = 10
        val executor = Executors.newFixedThreadPool(nThreads)
        val barrier = CyclicBarrier(nThreads)
        val latch = CountDownLatch(nThreads)

        val grantedCount = AtomicInteger(0)
        val rejectedCount = AtomicInteger(0)

        val vehicleId = "VEH-TEST-RACE"
        val ecuPhysicalAddress = "0x7E0"

        for (i in 0 until nThreads) {
            val actorId = "executor-$i"
            val sessionId = "session-$i"
            executor.submit {
                try {
                    barrier.await() // Synchronize all threads at the exact same instant
                    val result = ProgrammingLeaseManager.acquire(
                        vehicleId = vehicleId,
                        ecuPhysicalAddress = ecuPhysicalAddress,
                        adapterFingerprint = "ADAPTER_J2534_$i",
                        executorId = actorId,
                        principalId = "user_$i",
                        sessionId = sessionId,
                    )
                    when (result) {
                        is LeaseAcquisitionResult.Granted -> grantedCount.incrementAndGet()
                        is LeaseAcquisitionResult.Rejected -> rejectedCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("All threads must finish within timeout", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("Exactly 1 lease must be granted", 1, grantedCount.get())
        assertEquals("Exactly N-1 attempts must be rejected with typed conflict", nThreads - 1, rejectedCount.get())
    }

    @Test
    fun `same session can idempotently renew active lease without conflict`() {
        val vehicleId = "VEH-IDEMPOTENT"
        val ecuAddress = "0x7E0"
        val sessionId = "session-alpha"
        val executorId = "executor-alpha"

        val initial = ProgrammingLeaseManager.acquire(
            vehicleId = vehicleId,
            ecuPhysicalAddress = ecuAddress,
            adapterFingerprint = "FINGERPRINT_1",
            executorId = executorId,
            principalId = "user_1",
            sessionId = sessionId,
        )
        assertTrue(initial is LeaseAcquisitionResult.Granted)

        // Renew with same session
        val renewed = ProgrammingLeaseManager.acquire(
            vehicleId = vehicleId,
            ecuPhysicalAddress = ecuAddress,
            adapterFingerprint = "FINGERPRINT_1",
            executorId = executorId,
            principalId = "user_1",
            sessionId = sessionId,
        )
        assertTrue("Idempotent renewal must succeed", renewed is LeaseAcquisitionResult.Granted)

        // Competing session fails
        val competitor = ProgrammingLeaseManager.acquire(
            vehicleId = vehicleId,
            ecuPhysicalAddress = ecuAddress,
            adapterFingerprint = "FINGERPRINT_2",
            executorId = "executor-beta",
            principalId = "user_2",
            sessionId = "session-beta",
        )
        assertTrue("Competing session must be rejected", competitor is LeaseAcquisitionResult.Rejected)
    }

    @Test
    fun `explicit release frees ECU lease for subsequent acquisition`() {
        val vehicleId = "VEH-RELEASE"
        val ecuAddress = "0x7E0"
        val sessionId = "session-primary"

        ProgrammingLeaseManager.acquire(
            vehicleId = vehicleId,
            ecuPhysicalAddress = ecuAddress,
            adapterFingerprint = "FINGERPRINT_1",
            executorId = "executor-1",
            principalId = "user_1",
            sessionId = sessionId,
        )

        val released = ProgrammingLeaseManager.release(sessionId, vehicleId, ecuAddress)
        assertTrue(released)

        // Subsequent session can now acquire
        val next = ProgrammingLeaseManager.acquire(
            vehicleId = vehicleId,
            ecuPhysicalAddress = ecuAddress,
            adapterFingerprint = "FINGERPRINT_2",
            executorId = "executor-2",
            principalId = "user_2",
            sessionId = "session-secondary",
        )
        assertTrue("Lease acquisition after release must succeed", next is LeaseAcquisitionResult.Granted)
    }
}
