package com.elysium369.meet.safety.domain

data class ContradictionCandidate(
    val claimA: ContradictionInput,
    val claimB: ContradictionInput,
    val type: ContradictionType,
    val rationale: String,
)

enum class ContradictionType {
    TEMPORAL_IMPOSSIBILITY,
    LOCATION_IMPOSSIBILITY,
    IDENTITY_CONFLICT,
    NARRATIVE_INCONSISTENCY,
}

data class ContradictionInput(
    val claimId: String,
    val subjectRef: String?,
    val occurredAtEpochMs: Long?,
    val endedAtEpochMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
)

object SafetyContradictionEngine {

    private const val MAX_SIMULTANEOUS_DISTANCE_KM = 50.0
    private const val EARTH_RADIUS_KM = 6371.0

    fun detect(claims: List<ContradictionInput>): List<ContradictionCandidate> {
        val results = mutableListOf<ContradictionCandidate>()
        for (i in claims.indices) {
            for (j in i + 1 until claims.size) {
                val a = claims[i]
                val b = claims[j]
                if (a.subjectRef == null || b.subjectRef == null || a.subjectRef != b.subjectRef) continue
                detectTemporalImpossibility(a, b)?.let { results.add(it) }
                detectLocationImpossibility(a, b)?.let { results.add(it) }
            }
        }
        return results
    }

    private fun detectTemporalImpossibility(
        a: ContradictionInput, b: ContradictionInput
    ): ContradictionCandidate? {
        val aStart = a.occurredAtEpochMs ?: return null
        val aEnd = a.endedAtEpochMs ?: aStart
        val bStart = b.occurredAtEpochMs ?: return null
        val bEnd = b.endedAtEpochMs ?: bStart
        val overlap = aStart <= bEnd && bStart <= aEnd
        if (!overlap) return null
        val aLat = a.latitude ?: return null
        val aLng = a.longitude ?: return null
        val bLat = b.latitude ?: return null
        val bLng = b.longitude ?: return null
        val distKm = haversine(aLat, aLng, bLat, bLng)
        if (distKm <= MAX_SIMULTANEOUS_DISTANCE_KM) return null
        return ContradictionCandidate(
            claimA = a, claimB = b,
            type = ContradictionType.TEMPORAL_IMPOSSIBILITY,
            rationale = "Subject ${a.subjectRef} claimed at two locations ${String.format("%.1f", distKm)} km apart during overlapping time window",
        )
    }

    private fun detectLocationImpossibility(
        a: ContradictionInput, b: ContradictionInput
    ): ContradictionCandidate? {
        val aStart = a.occurredAtEpochMs ?: return null
        val bStart = b.occurredAtEpochMs ?: return null
        val aLat = a.latitude ?: return null
        val aLng = a.longitude ?: return null
        val bLat = b.latitude ?: return null
        val bLng = b.longitude ?: return null
        val timeDiffHours = kotlin.math.abs(bStart - aStart) / 3_600_000.0
        if (timeDiffHours <= 0.0 || timeDiffHours > 24.0) return null
        val distKm = haversine(aLat, aLng, bLat, bLng)
        val maxSpeedKph = 900.0
        val maxDistKm = maxSpeedKph * timeDiffHours
        if (distKm <= maxDistKm) return null
        return ContradictionCandidate(
            claimA = a, claimB = b,
            type = ContradictionType.LOCATION_IMPOSSIBILITY,
            rationale = "Subject ${a.subjectRef} claimed at ${String.format("%.1f", distKm)} km apart within ${String.format("%.1f", timeDiffHours)} hours (max feasible: ${String.format("%.0f", maxDistKm)} km)",
        )
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * kotlin.math.asin(kotlin.math.sqrt(a))
    }
}
