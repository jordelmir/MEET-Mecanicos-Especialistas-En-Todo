package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.SafetyLocationSample
import com.elysium369.meet.safety.domain.SafetyLocationValidator
import org.junit.Assert.*
import org.junit.Test

class SafetyLocationValidationTest {

    private fun sample(
        latitude: Double = 45.0,
        longitude: Double = 90.0,
        accuracyMeters: Float = 10f,
        capturedAtEpochMs: Long = 123L,
        provider: String? = "gps",
    ) = SafetyLocationSample(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        capturedAtEpochMs = capturedAtEpochMs,
        provider = provider,
    )

    @Test
    fun `Valid sample passes`() {
        val s = sample(latitude = 45.0, longitude = 90.0, accuracyMeters = 10f)
        assertTrue(SafetyLocationValidator.validate(s).isSuccess)
    }

    @Test
    fun `Latitude greater than 90 fails`() {
        val s = sample(latitude = 91.0, longitude = 90.0)
        assertTrue(SafetyLocationValidator.validate(s).isFailure)
    }

    @Test
    fun `Latitude less than -90 fails`() {
        val s = sample(latitude = -91.0, longitude = 90.0)
        assertTrue(SafetyLocationValidator.validate(s).isFailure)
    }

    @Test
    fun `Longitude greater than 180 fails`() {
        val s = sample(latitude = 45.0, longitude = 181.0)
        assertTrue(SafetyLocationValidator.validate(s).isFailure)
    }

    @Test
    fun `Longitude less than -180 fails`() {
        val s = sample(latitude = 45.0, longitude = -181.0)
        assertTrue(SafetyLocationValidator.validate(s).isFailure)
    }

    @Test
    fun `Accuracy equal to 0 fails`() {
        val s = sample(accuracyMeters = 0f)
        assertTrue(SafetyLocationValidator.validate(s).isFailure)
    }

    @Test
    fun `Accuracy is NaN fails`() {
        val s = sample(accuracyMeters = Float.NaN)
        assertTrue(SafetyLocationValidator.validate(s).isFailure)
    }

    @Test
    fun `Accuracy is -1 fails`() {
        val s = sample(accuracyMeters = -1f)
        assertTrue(SafetyLocationValidator.validate(s).isFailure)
    }

    @Test
    fun `Accuracy is Infinity fails`() {
        val s = sample(accuracyMeters = Float.POSITIVE_INFINITY)
        assertTrue(SafetyLocationValidator.validate(s).isFailure)
    }

    @Test
    fun `Coordinate 0,0 with valid accuracy passes`() {
        val s = sample(latitude = 0.0, longitude = 0.0, accuracyMeters = 10f)
        assertTrue(SafetyLocationValidator.validate(s).isSuccess)
    }
}
