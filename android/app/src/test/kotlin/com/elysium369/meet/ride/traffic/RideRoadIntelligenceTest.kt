package com.elysium369.meet.ride.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRoadIntelligenceTest {
    private val now = 1_000_000L

    @Test
    fun `one uncorroborated closure cannot block routing`() {
        val report = incident(
            type = RideRoadIncidentType.ROAD_CLOSED,
            confirmations = 0,
            reliability = 1.0,
        )

        val evidence = RideRoadIncidentPolicy.evaluate(report, now, 90f)

        assertFalse(evidence.mayBlockRouting)
    }

    @Test
    fun `fresh corroborated closure can block matching route`() {
        val report = incident(
            type = RideRoadIncidentType.ROAD_CLOSED,
            confirmations = 4,
            reliability = 1.0,
            observedSpeedRatio = 0.05,
        )

        val evidence = RideRoadIncidentPolicy.evaluate(report, now, 90f)

        assertTrue(evidence.mayBlockRouting)
        assertTrue(evidence.mayAffectEta)
    }

    @Test
    fun `expired report has no eta effect`() {
        val expired = incident(
            type = RideRoadIncidentType.VERY_SLOW_TRAFFIC,
            confirmations = 5,
            reliability = 1.0,
            createdAt = now - 20_000,
            expiresAt = now - 1,
        )
        val segment = segment(incidents = listOf(expired))

        val estimate = RideCollaborativeEtaEstimator.estimate(listOf(segment), now)

        assertEquals(RideEtaEvidenceLevel.BASELINE_ONLY, estimate.evidenceLevel)
        assertEquals(100L, estimate.durationSeconds)
    }

    @Test
    fun `robust live speed and community incident increase eta within bounds`() {
        val slow = incident(
            type = RideRoadIncidentType.VERY_SLOW_TRAFFIC,
            confirmations = 3,
            reliability = 0.9,
            observedSpeedRatio = 0.3,
        )
        val segment = segment(
            speeds = listOf(5.0, 5.5, 6.0, 90.0),
            incidents = listOf(slow),
        )

        val estimate = RideCollaborativeEtaEstimator.estimate(listOf(segment), now)

        assertEquals(RideEtaEvidenceLevel.COMMUNITY_CORROBORATED, estimate.evidenceLevel)
        assertTrue(estimate.durationSeconds > 160L)
        assertTrue(estimate.durationSeconds < 715L)
    }

    private fun segment(
        speeds: List<Double> = emptyList(),
        incidents: List<RideRoadIncident> = emptyList(),
    ) = RideEtaSegment(
        id = "segment-1",
        distanceMeters = 1_000.0,
        baselineSpeedMetersPerSecond = 10.0,
        bearingDegrees = 90f,
        speedSamples = speeds.map { RideSegmentSpeedSample(it, now - 1_000) },
        incidents = incidents,
    )

    private fun incident(
        type: RideRoadIncidentType,
        confirmations: Int,
        reliability: Double,
        observedSpeedRatio: Double? = 0.2,
        createdAt: Long = now - 1_000,
        expiresAt: Long = now + 60_000,
    ) = RideRoadIncident(
        id = "incident-1",
        roadSegmentId = "segment-1",
        type = type,
        side = RideRoadSide.CENTER,
        severity = 3,
        latitude = 9.93,
        longitude = -84.08,
        bearingDegrees = 90f,
        accuracyMeters = 4f,
        reporterReliability = reliability,
        independentConfirmations = confirmations,
        independentDenials = 0,
        observedSpeedRatio = observedSpeedRatio,
        createdAtEpochMs = createdAt,
        expiresAtEpochMs = expiresAt,
    )
}
