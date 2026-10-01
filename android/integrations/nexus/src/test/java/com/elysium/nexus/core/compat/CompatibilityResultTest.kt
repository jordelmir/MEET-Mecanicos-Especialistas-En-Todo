package com.elysium.nexus.core.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CompatibilityResult] + [CompatibilityStatus].
 *
 * The §33 spec is explicit about the data shape and the
 * invariants. The tests pin both.
 */
class CompatibilityResultTest {

    private fun sampleResult(
        status: CompatibilityStatus,
        failures: List<String> = emptyList(),
        confidence: Int = 100,
        tester: String = "lab"
    ) = CompatibilityResult(
        deviceId = "honor-magic-v2",
        deviceModel = "Honor Magic V2",
        androidVersion = "14",
        oemFirmware = "MagicOS 7.2",
        transport = "BluetoothClassicHID",
        targetPlatform = "ANDROID_TV",
        targetOsFirmware = "Android TV 14",
        game = "Stardew Valley",
        capabilitiesTested = listOf("buttons", "sticks", "triggers"),
        capabilitiesPassed = listOf("buttons", "sticks", "triggers"),
        capabilitiesFailed = failures,
        latencyP50Ns = 4_000_000L,
        latencyP95Ns = 8_000_000L,
        tester = tester,
        date = "2026-07-31",
        evidence = "/var/log/elysium/test-2026-07-31.log",
        confidence = confidence,
        status = status
    )

    @Test
    fun allStatusesAreDistinct() {
        assertEquals(6, CompatibilityStatus.values().size)
    }

    @Test
    fun onlyVerifiedLabIsAMeasurement() {
        assertTrue(CompatibilityStatus.VERIFIED_LAB.isMeasurement())
        for (s in CompatibilityStatus.values()) {
            if (s == CompatibilityStatus.VERIFIED_LAB) continue
            assertTrue("expected $s not to be a measurement", !s.isMeasurement())
        }
    }

    @Test
    fun verifiedLabWithoutFailuresIsAccepted() {
        // A record with no failures and confidence 100 is
        // the happy path.
        val r = sampleResult(CompatibilityStatus.VERIFIED_LAB)
        assertEquals(CompatibilityStatus.VERIFIED_LAB, r.status)
    }

    @Test
    fun verifiedLabWithFailuresIsRejected() {
        try {
            sampleResult(
                status = CompatibilityStatus.VERIFIED_LAB,
                failures = listOf("triggers")
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun verifiedLabWithLowConfidenceIsRejected() {
        try {
            sampleResult(
                status = CompatibilityStatus.VERIFIED_LAB,
                confidence = 50
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun partiallyVerifiedAllowsFailures() {
        // PARTIALLY_VERIFIED is the spec state for "some
        // pass, some fail". The data class must accept
        // it.
        val r = sampleResult(
            status = CompatibilityStatus.PARTIALLY_VERIFIED,
            failures = listOf("gyro")
        )
        assertEquals(CompatibilityStatus.PARTIALLY_VERIFIED, r.status)
    }

    @Test
    fun regressionIsAccepted() {
        val r = sampleResult(
            status = CompatibilityStatus.REGRESSION,
            failures = listOf("buttons"),
            confidence = 80
        )
        assertEquals(CompatibilityStatus.REGRESSION, r.status)
    }

    @Test
    fun blockedIsAccepted() {
        // BLOCKED is the state for "we cannot run this
        // because it requires a vendor license we do
        // not have". The record is otherwise normal;
        // the database records what is *known* about
        // the combination.
        val r = sampleResult(
            status = CompatibilityStatus.BLOCKED,
            failures = emptyList(),
            confidence = 100,
            tester = "lab"
        )
        assertEquals(CompatibilityStatus.BLOCKED, r.status)
    }

    @Test
    fun unverifiedIsAccepted() {
        val r = sampleResult(
            status = CompatibilityStatus.UNVERIFIED,
            failures = emptyList(),
            confidence = 0,
            tester = "community"
        )
        assertEquals(CompatibilityStatus.UNVERIFIED, r.status)
    }

    @Test
    fun emptyDeviceIdIsRejected() {
        try {
            sampleResult(
                status = CompatibilityStatus.VERIFIED_LAB
            ).copy(deviceId = "")
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun emptyCapabilitiesTestedIsRejected() {
        try {
            sampleResult(
                status = CompatibilityStatus.VERIFIED_LAB
            ).copy(capabilitiesTested = emptyList())
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun outOfRangeConfidenceIsRejected() {
        try {
            sampleResult(
                status = CompatibilityStatus.VERIFIED_LAB
            ).copy(confidence = 150)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            sampleResult(
                status = CompatibilityStatus.VERIFIED_LAB
            ).copy(confidence = -1)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
