package com.elysium369.meet.ride.traffic

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToLong

enum class RideRoadIncidentType {
    SLOW_TRAFFIC,
    VERY_SLOW_TRAFFIC,
    STALLED_VEHICLE,
    POTHOLE,
    OBSTACLE,
    ROAD_CLOSED,
    WRONG_WAY_HAZARD,
    POLICE_PRESENCE,
    TRAFFIC_CONTROL,
    PUBLIC_POLICE,
    TRAFFIC_POLICE,
    SPEED_BUMP,
    FLOODING,
}

enum class RideRoadSide {
    LEFT,
    CENTER,
    RIGHT,
    NOT_APPLICABLE,
}

data class RideRoadReportAvailability(
    val allowed: Boolean,
    val message: String,
)

/**
 * Fail-closed gate for collaborative road reports.
 *
 * A report is operational evidence, so a local screen state is not enough:
 * the driver role, local lifecycle and authoritative server projection must all
 * confirm that the trip is currently moving toward its final destination.
 */
object RideRoadReportAvailabilityPolicy {
    fun evaluate(
        isDriver: Boolean,
        localStatus: String,
        serverState: String?,
        serverVersion: Long,
        hasCurrentGps: Boolean,
    ): RideRoadReportAvailability = when {
        !isDriver -> RideRoadReportAvailability(
            allowed = false,
            message = "Los reportes viales están reservados al conductor asignado.",
        )
        localStatus != "IN_PROGRESS" -> RideRoadReportAvailability(
            allowed = false,
            message = "Los reportes viales se habilitan únicamente después de iniciar la ruta.",
        )
        serverVersion <= 0L || serverState != "IN_PROGRESS" -> RideRoadReportAvailability(
            allowed = false,
            message = "Esperando confirmación del servidor para habilitar reportes en ruta.",
        )
        !hasCurrentGps -> RideRoadReportAvailability(
            allowed = false,
            message = "Se necesita una ubicación GPS actual para situar el reporte.",
        )
        else -> RideRoadReportAvailability(
            allowed = true,
            message = "Reporte vial disponible durante la ruta activa.",
        )
    }
}

data class RideRoadIncident(
    val id: String,
    val roadSegmentId: String,
    val type: RideRoadIncidentType,
    val side: RideRoadSide,
    val severity: Int,
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float?,
    val accuracyMeters: Float?,
    val reporterReliability: Double,
    val independentConfirmations: Int,
    val independentDenials: Int,
    val observedSpeedRatio: Double?,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank())
        require(roadSegmentId.isNotBlank())
        require(severity in 1..3)
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(accuracyMeters == null || accuracyMeters >= 0)
        require(reporterReliability in 0.0..1.0)
        require(independentConfirmations >= 0)
        require(independentDenials >= 0)
        require(observedSpeedRatio == null || observedSpeedRatio >= 0.0)
        require(createdAtEpochMs <= expiresAtEpochMs)
    }

    fun isExpired(nowEpochMs: Long): Boolean = nowEpochMs >= expiresAtEpochMs
}

data class RideRoadIncidentEvidence(
    val confidence: Double,
    val mayAffectEta: Boolean,
    val mayBlockRouting: Boolean,
)

object RideRoadIncidentPolicy {
    private const val ETA_CONFIDENCE_THRESHOLD = 0.32
    private const val HARD_CLOSURE_CONFIDENCE_THRESHOLD = 0.78

    fun evaluate(
        incident: RideRoadIncident,
        nowEpochMs: Long,
        routeBearingDegrees: Float?,
    ): RideRoadIncidentEvidence {
        if (incident.isExpired(nowEpochMs)) {
            return RideRoadIncidentEvidence(0.0, mayAffectEta = false, mayBlockRouting = false)
        }

        val lifetime = (incident.expiresAtEpochMs - incident.createdAtEpochMs).coerceAtLeast(1L)
        val age = (nowEpochMs - incident.createdAtEpochMs).coerceAtLeast(0L)
        val freshness = exp(-2.2 * age.toDouble() / lifetime.toDouble()).coerceIn(0.0, 1.0)
        val accuracy = when (val meters = incident.accuracyMeters) {
            null -> 0.55
            else -> (1.0 - (meters / 120.0)).coerceIn(0.20, 1.0)
        }
        val direction = directionMatch(incident.bearingDegrees, routeBearingDegrees)
        val votes = (
            0.45 +
                incident.independentConfirmations.coerceAtMost(5) * 0.13 -
                incident.independentDenials.coerceAtMost(5) * 0.16
            ).coerceIn(0.0, 1.0)
        val speedEvidence = incident.observedSpeedRatio?.let { ratio ->
            when {
                ratio <= 0.25 -> 1.0
                ratio <= 0.55 -> 0.82
                ratio <= 0.80 -> 0.58
                else -> 0.28
            }
        } ?: 0.50
        val confidence = (
            freshness * 0.24 +
                incident.reporterReliability * 0.18 +
                accuracy * 0.14 +
                direction * 0.12 +
                votes * 0.22 +
                speedEvidence * 0.10
            ).coerceIn(0.0, 1.0)

        val hardHazard = incident.type in setOf(
            RideRoadIncidentType.ROAD_CLOSED,
            RideRoadIncidentType.WRONG_WAY_HAZARD,
            RideRoadIncidentType.FLOODING,
        )
        val corroborated = incident.independentConfirmations >= 2
        return RideRoadIncidentEvidence(
            confidence = confidence,
            mayAffectEta = confidence >= ETA_CONFIDENCE_THRESHOLD,
            mayBlockRouting = hardHazard && corroborated &&
                confidence >= HARD_CLOSURE_CONFIDENCE_THRESHOLD,
        )
    }

    private fun directionMatch(report: Float?, route: Float?): Double {
        if (report == null || route == null) return 0.65
        val difference = abs(report - route) % 360f
        val shortest = minOf(difference, 360f - difference)
        return (1.0 - shortest / 120.0).coerceIn(0.0, 1.0)
    }
}

data class RideSegmentSpeedSample(
    val speedMetersPerSecond: Double,
    val capturedAtEpochMs: Long,
) {
    init {
        require(speedMetersPerSecond >= 0.0)
        require(capturedAtEpochMs >= 0)
    }
}

data class RideEtaSegment(
    val id: String,
    val distanceMeters: Double,
    val baselineSpeedMetersPerSecond: Double,
    val bearingDegrees: Float?,
    val speedSamples: List<RideSegmentSpeedSample>,
    val incidents: List<RideRoadIncident>,
) {
    init {
        require(id.isNotBlank())
        require(distanceMeters >= 0.0)
        require(baselineSpeedMetersPerSecond > 0.0)
    }
}

enum class RideEtaEvidenceLevel {
    BASELINE_ONLY,
    LIVE_SPEED,
    COMMUNITY_CORROBORATED,
}

data class RideEtaEstimate(
    val durationSeconds: Long,
    val evidenceLevel: RideEtaEvidenceLevel,
    val blockingSegmentIds: Set<String>,
)

object RideCollaborativeEtaEstimator {
    private const val SAMPLE_MAX_AGE_MS = 10 * 60 * 1000L

    fun estimate(
        segments: List<RideEtaSegment>,
        nowEpochMs: Long,
    ): RideEtaEstimate {
        var seconds = 0.0
        var hasLiveSpeed = false
        var hasCommunityEvidence = false
        val blocking = linkedSetOf<String>()

        segments.forEach { segment ->
            val freshSpeeds = segment.speedSamples
                .filter { nowEpochMs - it.capturedAtEpochMs in 0..SAMPLE_MAX_AGE_MS }
                .map { it.speedMetersPerSecond }
            val observedSpeed = robustMedian(freshSpeeds)
            hasLiveSpeed = hasLiveSpeed || observedSpeed != null
            val liveFactor = observedSpeed
                ?.div(segment.baselineSpeedMetersPerSecond)
                ?.coerceIn(0.25, 1.25)
                ?: 1.0

            var incidentFactor = 1.0
            segment.incidents.forEach { incident ->
                val evidence = RideRoadIncidentPolicy.evaluate(
                    incident = incident,
                    nowEpochMs = nowEpochMs,
                    routeBearingDegrees = segment.bearingDegrees,
                )
                if (evidence.mayBlockRouting) blocking += segment.id
                if (evidence.mayAffectEta) {
                    hasCommunityEvidence = true
                    val floor = incidentSpeedFloor(incident)
                    val weighted = 1.0 - evidence.confidence * (1.0 - floor)
                    incidentFactor = minOf(incidentFactor, weighted.coerceIn(floor, 1.0))
                }
            }

            val effectiveSpeed = (
                segment.baselineSpeedMetersPerSecond * liveFactor * incidentFactor
                ).coerceIn(1.4, segment.baselineSpeedMetersPerSecond * 1.25)
            seconds += segment.distanceMeters / effectiveSpeed
        }

        val level = when {
            hasCommunityEvidence -> RideEtaEvidenceLevel.COMMUNITY_CORROBORATED
            hasLiveSpeed -> RideEtaEvidenceLevel.LIVE_SPEED
            else -> RideEtaEvidenceLevel.BASELINE_ONLY
        }
        return RideEtaEstimate(
            durationSeconds = max(0L, seconds.roundToLong()),
            evidenceLevel = level,
            blockingSegmentIds = blocking,
        )
    }

    private fun robustMedian(values: List<Double>): Double? {
        if (values.size < 3) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun incidentSpeedFloor(incident: RideRoadIncident): Double = when (incident.type) {
        RideRoadIncidentType.SLOW_TRAFFIC -> 0.68
        RideRoadIncidentType.VERY_SLOW_TRAFFIC -> 0.32
        RideRoadIncidentType.STALLED_VEHICLE -> if (incident.side == RideRoadSide.CENTER) 0.45 else 0.72
        RideRoadIncidentType.POTHOLE -> 0.78
        RideRoadIncidentType.OBSTACLE -> if (incident.side == RideRoadSide.CENTER) 0.48 else 0.72
        RideRoadIncidentType.ROAD_CLOSED -> 0.12
        RideRoadIncidentType.WRONG_WAY_HAZARD -> 0.18
        RideRoadIncidentType.POLICE_PRESENCE -> 0.92
        RideRoadIncidentType.TRAFFIC_CONTROL -> 0.70
        RideRoadIncidentType.PUBLIC_POLICE -> 0.92
        RideRoadIncidentType.TRAFFIC_POLICE -> 0.84
        RideRoadIncidentType.SPEED_BUMP -> 0.72
        RideRoadIncidentType.FLOODING -> 0.16
    }.let { base -> (base - (incident.severity - 1) * 0.08).coerceAtLeast(0.10) }
}
