package com.elysium369.meet.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ApproximationLeakAndAntiAveragingTest {

    private val exactLat = 9.9333296
    private val exactLon = -84.0833282
    private val now = System.currentTimeMillis()

    @Test
    fun `approximate mode snaps to coarse grid and hides exact coordinates`() {
        val sample = PresenceSample(
            sampleId = "samp-001",
            principalId = "user-alice",
            deviceId = "dev-alice-phone",
            streamId = "str-1",
            sequence = 1L,
            capturedAt = now,
            receivedAt = now,
            latitude = exactLat,
            longitude = exactLon,
            accuracyMeters = 8.5f,
            altitude = 1172.0,
            speed = 1.2f,
            heading = 180f,
            batteryState = 85,
        )

        val grant = LocationShareGrant(
            grantId = "grant-001",
            ownerPrincipalId = "user-alice",
            audienceType = AudienceType.CIRCLE,
            audienceId = "circle-family",
            mode = LocationShareMode.APPROXIMATE,
            validFrom = now - 1000L,
            validUntil = now + 3600_000L,
            shareBattery = true,
            shareMotion = false, // Motion should be stripped
        )

        val projected = PrivacyProjectionEngine.project(sample, grant, now)
        assertTrue(projected is ProjectedPresence.Approximate)
        val approx = projected as ProjectedPresence.Approximate

        // Coarse coordinates must differ from exact coordinates
        assertNotEquals(exactLat, approx.coarseLatitude, 0.0000001)
        assertNotEquals(exactLon, approx.coarseLongitude, 0.0000001)

        // Precision is coarsened to 1500m
        assertEquals(1500f, approx.accuracyMeters, 0.1f)
        assertEquals(85, approx.batteryPercentage)
    }

    @Test
    fun `anti-averaging law - multiple noisy samples within grid cell yield identical coarse centroid`() {
        val grant = LocationShareGrant(
            grantId = "grant-001",
            ownerPrincipalId = "user-alice",
            audienceType = AudienceType.CIRCLE,
            audienceId = "circle-family",
            mode = LocationShareMode.APPROXIMATE,
            validFrom = now - 1000L,
            validUntil = now + 3600_000L,
        )

        val centroids = mutableSetOf<Pair<Double, Double>>()

        // Simulate 100 observations with GPS jitter (+/- 20 meters ~= 0.00018 degrees)
        for (i in 0 until 100) {
            val jitterLat = exactLat + (i % 5 - 2) * 0.00005
            val jitterLon = exactLon + (i % 7 - 3) * 0.00005

            val sample = PresenceSample(
                sampleId = "samp-$i",
                principalId = "user-alice",
                deviceId = "dev-alice-phone",
                streamId = "str-1",
                sequence = i.toLong(),
                capturedAt = now + i * 1000L,
                receivedAt = now + i * 1000L,
                latitude = jitterLat,
                longitude = jitterLon,
                accuracyMeters = 10f,
            )

            val projected = PrivacyProjectionEngine.project(sample, grant, now + i * 1000L) as ProjectedPresence.Approximate
            centroids.add(Pair(projected.coarseLatitude, projected.coarseLongitude))
        }

        // Must collapse to exactly 1 deterministic centroid, preventing averaging triangulation!
        assertEquals(1, centroids.size)
    }

    @Test
    fun `place only mode completely strips coordinates`() {
        val sample = PresenceSample(
            sampleId = "samp-001",
            principalId = "user-alice",
            deviceId = "dev-alice-phone",
            streamId = "str-1",
            sequence = 1L,
            capturedAt = now,
            receivedAt = now,
            latitude = exactLat,
            longitude = exactLon,
            accuracyMeters = 5f,
        )

        val grant = LocationShareGrant(
            grantId = "grant-002",
            ownerPrincipalId = "user-alice",
            audienceType = AudienceType.CIRCLE,
            audienceId = "circle-friends",
            mode = LocationShareMode.PLACE_ONLY,
            validFrom = now - 1000L,
            validUntil = now + 3600_000L,
        )

        val projected = PrivacyProjectionEngine.project(sample, grant, now, currentPlaceName = "Casa")
        assertTrue(projected is ProjectedPresence.SemanticPlaceOnly)
        val placeOnly = projected as ProjectedPresence.SemanticPlaceOnly
        assertEquals("Casa", placeOnly.placeName)
    }

    @Test
    fun `expired or off grant returns hidden`() {
        val sample = PresenceSample(
            sampleId = "samp-001",
            principalId = "user-alice",
            deviceId = "dev-alice-phone",
            streamId = "str-1",
            sequence = 1L,
            capturedAt = now,
            receivedAt = now,
            latitude = exactLat,
            longitude = exactLon,
            accuracyMeters = 5f,
        )

        val expiredGrant = LocationShareGrant(
            grantId = "grant-003",
            ownerPrincipalId = "user-alice",
            audienceType = AudienceType.CIRCLE,
            audienceId = "circle-friends",
            mode = LocationShareMode.PRECISE,
            validFrom = now - 10_000L,
            validUntil = now - 1000L, // Expired
        )

        val projected = PrivacyProjectionEngine.project(sample, expiredGrant, now)
        assertEquals(ProjectedPresence.Hidden, projected)
    }

    @Test
    fun `self authority enforcement - third party cannot create or modify grant`() {
        // Attempting to create a grant where createdBy != ownerPrincipalId throws exception
        assertThrows(IllegalArgumentException::class.java) {
            LocationShareGrant(
                grantId = "grant-bad",
                ownerPrincipalId = "user-alice",
                audienceType = AudienceType.CIRCLE,
                audienceId = "circle-family",
                mode = LocationShareMode.PRECISE,
                validFrom = now,
                createdBy = "admin-bob", // Illegal escalation
            )
        }

        val validGrant = LocationShareGrant(
            grantId = "grant-ok",
            ownerPrincipalId = "user-alice",
            audienceType = AudienceType.CIRCLE,
            audienceId = "circle-family",
            mode = LocationShareMode.APPROXIMATE,
            validFrom = now,
            createdBy = "user-alice",
        )

        // Attempting to broaden mode by admin-bob fails
        assertThrows(IllegalArgumentException::class.java) {
            validGrant.updateMode(LocationShareMode.PRECISE, actorPrincipalId = "admin-bob")
        }
    }
}
