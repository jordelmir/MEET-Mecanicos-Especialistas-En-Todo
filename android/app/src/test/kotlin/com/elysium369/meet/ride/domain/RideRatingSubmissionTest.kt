package com.elysium369.meet.ride.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RideRatingSubmissionTest {
    private var calls = 0
    private var writes = 0
    private var remote = Result.success(true)

    private fun submit(
        actor: String? = "passenger",
        state: String = "COMPLETED",
        version: Long = 1,
        driver: String? = "driver",
        passengerRating: Boolean = true,
        stars: Double = 5.0,
    ): Result<Unit> = runBlocking {
        RideRatingSubmission.submit(
            actor, "passenger", driver, state, version, passengerRating, stars,
            confirm = { calls++; remote },
            persistConfirmed = { writes++ },
        )
    }

    @Test fun `failed authority leaves rating retryable and successful retry persists`() {
        remote = Result.failure(IllegalStateException("offline"))
        assertTrue(submit().isFailure)
        assertEquals(0, writes)
        remote = Result.success(true)
        assertTrue(submit().isSuccess)
        assertEquals(2, calls)
        assertEquals(1, writes)
    }

    @Test fun `unconfirmed response cannot become local success`() {
        remote = Result.success(false)
        assertTrue(submit().isFailure)
        assertEquals(0, writes)
    }

    @Test fun `missing and unrelated identity never reach authority or persistence`() {
        listOf(null, "", "other", "driver").forEach { assertTrue(submit(actor = it).isFailure) }
        assertEquals(0, calls)
        assertEquals(0, writes)
    }

    @Test fun `only authoritative completed assigned rides permit rating`() {
        listOf("OPEN", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS", "CANCELLED").forEach {
            assertTrue(submit(state = it).isFailure)
        }
        assertTrue(submit(version = 0).isFailure)
        assertTrue(submit(driver = null).isFailure)
        assertTrue(submit(passengerRating = false).isFailure)
        assertEquals(0, calls)
        assertEquals(0, writes)
    }

    @Test fun `invalid ratings never get silently clamped or truncated`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 2.5, 6.0).forEach {
            assertTrue(submit(stars = it).isFailure)
        }
        assertEquals(0, calls)
        assertEquals(0, writes)
        (1..5).forEach { assertTrue(submit(stars = it.toDouble()).isSuccess) }
        assertEquals(5, writes)
    }
}
