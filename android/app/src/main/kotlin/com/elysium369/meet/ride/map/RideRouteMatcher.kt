package com.elysium369.meet.ride.map

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RideRouteMatch(
    val segmentIndex: Int,
    val projectedPoint: RideGeoPoint,
    val distanceMeters: Double,
    val alongTrackDistanceMeters: Double,
) {
    init {
        require(segmentIndex >= 0) { "Segment index cannot be negative" }
        require(distanceMeters >= 0.0) { "Distance cannot be negative" }
        require(alongTrackDistanceMeters >= 0.0) { "Along track distance cannot be negative" }
    }
}

data class ReroutePolicy(
    val minOffRouteMeters: Double = 35.0,
    val requiredConsecutiveSamples: Int = 3,
    val maxAccuracyMeters: Double = 25.0,
    val cooldownMillis: Long = 10_000L,
)

class RerouteDetector(
    private val policy: ReroutePolicy = ReroutePolicy(),
) {
    private var consecutiveOffRouteCount = 0
    private var lastRerouteAtEpochMs = 0L

    fun evaluate(
        match: RideRouteMatch?,
        accuracyMeters: Double,
        nowEpochMs: Long,
    ): Boolean {
        if (lastRerouteAtEpochMs > 0L && nowEpochMs - lastRerouteAtEpochMs < policy.cooldownMillis) {
            return false
        }
        if (accuracyMeters > policy.maxAccuracyMeters) {
            consecutiveOffRouteCount = 0
            return false
        }
        val isOffRoute = match == null || match.distanceMeters >= policy.minOffRouteMeters
        if (isOffRoute) {
            consecutiveOffRouteCount++
            if (consecutiveOffRouteCount >= policy.requiredConsecutiveSamples) {
                consecutiveOffRouteCount = 0
                lastRerouteAtEpochMs = nowEpochMs
                return true
            }
        } else {
            consecutiveOffRouteCount = 0
        }
        return false
    }

    fun reset() {
        consecutiveOffRouteCount = 0
    }
}

class RideRouteMatcher(
    private val localWindow: Int = 80,
) {
    private var lastMatchedSegment = 0

    fun reset() {
        lastMatchedSegment = 0
    }

    fun match(
        route: List<RideGeoPoint>,
        location: RideGeoPoint,
    ): RideRouteMatch? {
        if (route.size < 2) return null
        val segmentCount = route.size - 1

        val from = (lastMatchedSegment - localWindow).coerceAtLeast(0)
        val to = (lastMatchedSegment + localWindow).coerceAtMost(segmentCount - 1)

        var best: RideRouteMatch? = null
        for (i in from..to) {
            val candidate = projectToSegment(
                point = location,
                start = route[i],
                end = route[i + 1],
                segmentIndex = i,
            )
            if (best == null || candidate.distanceMeters < best.distanceMeters) {
                best = candidate
            }
        }

        // If local window match is within 50 meters, accept it and advance cursor
        if (best != null && best.distanceMeters <= 50.0) {
            lastMatchedSegment = best.segmentIndex
            return best.withAlongTrackDistance(route)
        }

        // Otherwise fallback to global search over the full geometry
        var globalBest: RideRouteMatch? = null
        for (i in 0 until segmentCount) {
            val candidate = projectToSegment(
                point = location,
                start = route[i],
                end = route[i + 1],
                segmentIndex = i,
            )
            if (globalBest == null || candidate.distanceMeters < globalBest.distanceMeters) {
                globalBest = candidate
            }
        }

        globalBest?.let {
            lastMatchedSegment = it.segmentIndex
            return it.withAlongTrackDistance(route)
        }

        return null
    }

    private fun RideRouteMatch.withAlongTrackDistance(route: List<RideGeoPoint>): RideRouteMatch {
        var distance = 0.0
        for (i in 0 until segmentIndex) {
            distance += distanceMeters(route[i], route[i + 1])
        }
        distance += distanceMeters(route[segmentIndex], projectedPoint)
        return copy(alongTrackDistanceMeters = distance)
    }

    fun remainingDistanceMeters(route: List<RideGeoPoint>, match: RideRouteMatch): Double {
        if (route.size < 2) return 0.0
        var total = 0.0
        total += distanceMeters(match.projectedPoint, route[match.segmentIndex + 1])
        for (i in (match.segmentIndex + 1) until (route.size - 1)) {
            total += distanceMeters(route[i], route[i + 1])
        }
        return total.coerceAtLeast(0.0)
    }

    fun nextManeuver(
        route: RideRoadRoute,
        match: RideRouteMatch,
    ): Pair<RideRouteManeuver, Long>? {
        if (route.maneuvers.isEmpty()) return null

        val matchAlongTrack = match.alongTrackDistanceMeters
        val totalRouteDist = route.distanceMeters

        // Estimate each maneuver's along track distance based on location proximity to route
        var cumulativeDist = 0.0
        for (maneuver in route.maneuvers) {
            cumulativeDist += maneuver.distanceMeters
            val distanceToManeuver = (cumulativeDist - matchAlongTrack).coerceAtLeast(0.0)
            if (cumulativeDist >= matchAlongTrack) {
                return Pair(maneuver, distanceToManeuver.toLong())
            }
        }

        return route.maneuvers.lastOrNull()?.let {
            Pair(it, (totalRouteDist - matchAlongTrack).coerceAtLeast(0.0).toLong())
        }
    }

    companion object {
        fun distanceMeters(a: RideGeoPoint, b: RideGeoPoint): Double {
            val earthRadius = 6_371_008.8
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val haversine = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return 2.0 * earthRadius * atan2(sqrt(haversine), sqrt(1.0 - haversine))
        }

        fun projectToSegment(
            point: RideGeoPoint,
            start: RideGeoPoint,
            end: RideGeoPoint,
            segmentIndex: Int,
        ): RideRouteMatch {
            val midLat = Math.toRadians((start.latitude + end.latitude) / 2.0)
            val cosMidLat = cos(midLat)

            val degToRad = Math.PI / 180.0
            val earthRadius = 6_371_008.8

            // Local Cartesian coordinates in meters
            val xStart = 0.0
            val yStart = 0.0
            val xEnd = (end.longitude - start.longitude) * degToRad * earthRadius * cosMidLat
            val yEnd = (end.latitude - start.latitude) * degToRad * earthRadius

            val xPoint = (point.longitude - start.longitude) * degToRad * earthRadius * cosMidLat
            val yPoint = (point.latitude - start.latitude) * degToRad * earthRadius

            val dx = xEnd - xStart
            val dy = yEnd - yStart
            val segLengthSquared = dx * dx + dy * dy

            val t = if (segLengthSquared <= 1e-6) {
                0.0
            } else {
                ((xPoint * dx + yPoint * dy) / segLengthSquared).coerceIn(0.0, 1.0)
            }

            val projLat = start.latitude + t * (end.latitude - start.latitude)
            val projLon = start.longitude + t * (end.longitude - start.longitude)

            val projectedPoint = RideGeoPoint(
                latitude = projLat,
                longitude = projLon,
                accuracyMeters = null,
                capturedAtEpochMs = point.capturedAtEpochMs,
            )

            val dist = distanceMeters(point, projectedPoint)

            return RideRouteMatch(
                segmentIndex = segmentIndex,
                projectedPoint = projectedPoint,
                distanceMeters = dist,
                alongTrackDistanceMeters = 0.0,
            )
        }
    }
}
