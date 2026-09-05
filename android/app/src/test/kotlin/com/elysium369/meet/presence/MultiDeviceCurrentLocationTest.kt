package com.elysium369.meet.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MultiDeviceCurrentLocationTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `authorized publisher device updates current presence but unleased device does not`() {
        val principalId = "user-carlos"
        val phoneDeviceId = "dev-phone-pixel8"
        val tabletDeviceId = "dev-tablet-ipad"

        val phoneLease = PresencePublisherLease(
            principalId = principalId,
            deviceId = phoneDeviceId,
            streamId = "stream-phone-01",
            grantedAt = now - 60_000L,
            expiresAt = now + 3600_000L,
            priority = 10,
        )

        var snapshot = PresenceSnapshot(
            principalId = principalId,
            state = PresenceState.INACTIVE,
            latestSample = null,
            publisherDeviceId = null,
            lastUpdatedAtEpochMs = 0L,
        )

        // 1. Phone sends fresh sample with active lease
        val phoneSample = PresenceSample(
            sampleId = "samp-phone-1",
            principalId = principalId,
            deviceId = phoneDeviceId,
            streamId = "stream-phone-01",
            sequence = 100L,
            capturedAt = now - 5000L,
            receivedAt = now - 4000L,
            latitude = 9.9333,
            longitude = -84.0833,
            accuracyMeters = 8.0f,
        )

        snapshot = snapshot.updateWithSample(phoneSample, phoneLease, now)
        assertEquals(phoneDeviceId, snapshot.publisherDeviceId)
        assertEquals(9.9333, snapshot.latestSample!!.latitude, 0.0001)
        assertEquals(PresenceState.SHARING, snapshot.state)

        // 2. Tablet sends conflicting sample without valid publisher lease
        val tabletSample = PresenceSample(
            sampleId = "samp-tablet-1",
            principalId = principalId,
            deviceId = tabletDeviceId,
            streamId = "stream-tablet-01",
            sequence = 1L,
            capturedAt = now - 1000L, // Newer capturedAt, but lacks lease!
            receivedAt = now,
            latitude = 10.0000,
            longitude = -84.2000,
            accuracyMeters = 25.0f,
        )

        val updatedSnapshot = snapshot.updateWithSample(tabletSample, phoneLease, now)

        // Snapshot MUST NOT be overwritten by the unleased tablet!
        assertEquals(phoneDeviceId, updatedSnapshot.publisherDeviceId)
        assertEquals(9.9333, updatedSnapshot.latestSample!!.latitude, 0.0001)
    }

    @Test
    fun `offline backfill does not rewind current snapshot marker`() {
        val principalId = "user-carlos"
        val phoneDeviceId = "dev-phone-pixel8"

        val lease = PresencePublisherLease(
            principalId = principalId,
            deviceId = phoneDeviceId,
            streamId = "stream-phone-01",
            grantedAt = now - 7200_000L,
            expiresAt = now + 3600_000L,
        )

        val currentLiveSample = PresenceSample(
            sampleId = "samp-live",
            principalId = principalId,
            deviceId = phoneDeviceId,
            streamId = "stream-phone-01",
            sequence = 500L,
            capturedAt = now - 10_000L,
            receivedAt = now - 9_000L,
            latitude = 9.9350,
            longitude = -84.0850,
            accuracyMeters = 5f,
        )

        var snapshot = PresenceSnapshot(
            principalId = principalId,
            state = PresenceState.SHARING,
            latestSample = currentLiveSample,
            publisherDeviceId = phoneDeviceId,
            lastUpdatedAtEpochMs = now - 9_000L,
            freshness = LocationFreshness.LIVE,
        )

        // Device reconnects and uploads an old backfilled sample captured 1 hour ago
        val oldOfflineSample = PresenceSample(
            sampleId = "samp-backfill-1",
            principalId = principalId,
            deviceId = phoneDeviceId,
            streamId = "stream-phone-01",
            sequence = 200L,
            capturedAt = now - 3600_000L, // 1 hour ago
            receivedAt = now, // Received now
            latitude = 9.9100,
            longitude = -84.0500,
            accuracyMeters = 12f,
            isBackfill = true,
        )

        val afterBackfill = snapshot.updateWithSample(oldOfflineSample, lease, now)

        // Current snapshot must retain live location and not be rewound to the 1-hour-old sample
        assertEquals(500L, afterBackfill.latestSample!!.sequence)
        assertEquals(9.9350, afterBackfill.latestSample!!.latitude, 0.0001)
        assertNotEquals(9.9100, afterBackfill.latestSample!!.latitude, 0.0001)
    }

    @Test
    fun `untrusted clock skew derives unknown freshness`() {
        val sample = PresenceSample(
            sampleId = "samp-skew",
            principalId = "user-carlos",
            deviceId = "dev-phone",
            streamId = "stream-1",
            sequence = 1L,
            capturedAt = now + 100_000L, // Future time on device!
            receivedAt = now,
            latitude = 9.9333,
            longitude = -84.0833,
            accuracyMeters = 5f,
            clockQuality = ClockQuality.UNTRUSTED_SKEW,
        )

        val freshness = sample.deriveFreshness(now)
        assertEquals(LocationFreshness.UNKNOWN, freshness)
    }
}
