package com.elysium369.meet.ride.concurrency

import com.elysium369.meet.ride.domain.RideMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Section 41 Release-Blocking Concurrency Race Test.
 *
 * Verifies that under concurrent barrier-synchronized execution of N drivers
 * observing the exact same Ride version:
 * 1. Exactly one driver wins exclusive assignment.
 * 2. Exactly N - 1 drivers receive a typed conflict (ALREADY_ASSIGNED / VERSION_CONFLICT).
 * 3. Exactly one commission reservation is created.
 * 4. Retrying with identical idempotency key yields idempotent replay.
 * 5. Retrying with new idempotency key yields typed conflict.
 */
class RideAcceptanceConcurrencyRaceTest {

    sealed interface AtomicAcceptResult {
        data class Won(val tripId: String, val assignedDriverId: String, val newVersion: Long) : AtomicAcceptResult
        data class Rejected(val code: String, val message: String, val currentVersion: Long) : AtomicAcceptResult
    }

    /**
     * In-memory simulator of PostgreSQL row-level locking (FOR UPDATE)
     * and idempotency receipts as implemented by ride_accept_offer_v2 / ride_claim_request_v2.
     */
    class AuthoritativeRideServer(
        private val initialTripId: String,
        private val initialVersion: Long = 1L,
        private val fareMinor: Long = 5000L,
    ) {
        private val lock = ReentrantLock()
        var currentVersion = initialVersion
            private set
        var state = "SEARCHING"
            private set
        var assignedDriverId: String? = null
            private set

        private val receipts = ConcurrentHashMap<String, AtomicAcceptResult>()
        val commissionReservations = ConcurrentHashMap<String, RideMoney>()

        fun acceptOrClaim(
            tripId: String,
            driverId: String,
            expectedVersion: Long,
            idempotencyKey: String,
        ): AtomicAcceptResult {
            // 1. Idempotency replay check
            receipts[idempotencyKey]?.let { return it }

            // 2. Transactional row lock simulation (equivalent to SELECT ... FOR UPDATE)
            return lock.withLock {
                // Double check idempotency under lock
                receipts[idempotencyKey]?.let { return it }

                if (tripId != initialTripId) {
                    return AtomicAcceptResult.Rejected("NOT_FOUND", "Trip not found", currentVersion)
                }
                if (state != "SEARCHING" || assignedDriverId != null) {
                    val rejected = AtomicAcceptResult.Rejected(
                        "ALREADY_ASSIGNED",
                        "El viaje ya fue asignado a otro conductor",
                        currentVersion,
                    )
                    receipts[idempotencyKey] = rejected
                    return rejected
                }
                if (currentVersion != expectedVersion) {
                    val conflict = AtomicAcceptResult.Rejected(
                        "VERSION_CONFLICT",
                        "La versión del viaje cambió",
                        currentVersion,
                    )
                    receipts[idempotencyKey] = conflict
                    return conflict
                }

                // 3. Atomically assign winner and advance version
                assignedDriverId = driverId
                state = "ASSIGNED"
                currentVersion += 1L

                // 4. Reserve 5% commission in ledger
                val commissionAmount = (fareMinor * 500L) / 10000L
                commissionReservations[driverId] = RideMoney.of(commissionAmount, "CRC")

                val winnerResult = AtomicAcceptResult.Won(tripId, driverId, currentVersion)
                receipts[idempotencyKey] = winnerResult
                winnerResult
            }
        }
    }

    @Test
    fun `barrier race with N drivers produces exactly one winner and N minus one typed conflicts`() {
        val nDrivers = 10
        val iterations = 25

        for (iter in 1..iterations) {
            val tripId = "trip-race-$iter"
            val server = AuthoritativeRideServer(initialTripId = tripId, initialVersion = 1L)

            val barrier = CyclicBarrier(nDrivers)
            val latch = CountDownLatch(nDrivers)
            val executor = Executors.newFixedThreadPool(nDrivers)

            val winCount = AtomicInteger(0)
            val rejectCount = AtomicInteger(0)
            val results = ConcurrentHashMap<String, AtomicAcceptResult>()

            for (i in 1..nDrivers) {
                val driverId = "driver-$i"
                val idempotencyKey = "idemp-$tripId-$driverId"

                executor.submit {
                    try {
                        barrier.await(5, TimeUnit.SECONDS)
                        val res = server.acceptOrClaim(
                            tripId = tripId,
                            driverId = driverId,
                            expectedVersion = 1L,
                            idempotencyKey = idempotencyKey,
                        )
                        results[driverId] = res
                        when (res) {
                            is AtomicAcceptResult.Won -> winCount.incrementAndGet()
                            is AtomicAcceptResult.Rejected -> rejectCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue("Threads finished within timeout", latch.await(10, TimeUnit.SECONDS))
            executor.shutdownNow()

            // Invariants
            assertEquals("Iteration $iter must have exactly 1 winner", 1, winCount.get())
            assertEquals("Iteration $iter must have exactly N-1 rejected", nDrivers - 1, rejectCount.get())
            assertEquals("Final state must be ASSIGNED", "ASSIGNED", server.state)
            assertEquals("Version must have incremented exactly once to 2", 2L, server.currentVersion)
            assertNotNull("Assigned driver must be non-null", server.assignedDriverId)

            val winnerDriverId = server.assignedDriverId!!
            val winnerResult = results[winnerDriverId]
            assertTrue("Winner result must be Won instance", winnerResult is AtomicAcceptResult.Won)
            assertEquals(
                "Winner result must reference winning driver",
                winnerDriverId,
                (winnerResult as AtomicAcceptResult.Won).assignedDriverId,
            )

            // Verify commission reservation exists ONLY for the winning driver
            assertEquals("Exactly 1 commission reservation must exist", 1, server.commissionReservations.size)
            assertTrue("Commission reservation must belong to winner", server.commissionReservations.containsKey(winnerDriverId))
            assertEquals(250L, server.commissionReservations[winnerDriverId]!!.minorUnits) // 5% of 5000

            // Idempotent retry: winner retries with same key -> gets identical won result
            val retryResult = server.acceptOrClaim(
                tripId = tripId,
                driverId = winnerDriverId,
                expectedVersion = 1L,
                idempotencyKey = "idemp-$tripId-$winnerDriverId",
            )
            assertEquals("Winner retry with same idempotency key must replay identical Won effect", winnerResult, retryResult)

            // Non-idempotent retry: winner calls again with NEW idempotency key -> gets ALREADY_ASSIGNED
            val newKeyResult = server.acceptOrClaim(
                tripId = tripId,
                driverId = winnerDriverId,
                expectedVersion = 1L,
                idempotencyKey = "new-key-$tripId-$winnerDriverId",
            )
            assertTrue("Winner call with new key on already assigned trip must be rejected", newKeyResult is AtomicAcceptResult.Rejected)
            assertEquals("ALREADY_ASSIGNED", (newKeyResult as AtomicAcceptResult.Rejected).code)
        }
    }
}
