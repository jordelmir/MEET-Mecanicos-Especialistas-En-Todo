package com.elysium369.meet.core.services.rating

import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.core.services.kernel.ServiceVertical
import java.util.UUID

enum class RatingDimension(val displayName: String, val weight: Double) {
    TECHNICAL_QUALITY("Calidad Técnica", 0.30),
    COMEBACK_AVOIDANCE("Solución Confirmada (Sin Reincidencia)", 0.25),
    QUOTE_ACCURACY("Exactitud de la Cotización", 0.15),
    TIME_COMPLIANCE("Cumplimiento de Tiempo / ETA", 0.10),
    COMMUNICATION("Comunicación y Claridad", 0.10),
    DOCUMENTATION_QUALITY("Calidad de Evidencia y Reporte", 0.10),
}

data class ServiceRatingSubmission(
    val ratingId: UUID = UUID.randomUUID(),
    val transactionId: UUID,
    val workOrderId: UUID?,
    val raterProfileId: UUID,
    val ratedProviderId: UUID,
    val raterRole: ServiceRole,
    val serviceVertical: ServiceVertical,
    val dimensionalScores: Map<RatingDimension, Int>, // 1..5 stars per dimension
    val comment: String?,
    val isVerifiedTransaction: Boolean,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    init {
        require(isVerifiedTransaction) { "Ratings can only be submitted for verified, completed transactions" }
        require(dimensionalScores.isNotEmpty()) { "At least one rating dimension must be scored" }
        dimensionalScores.forEach { (dim, score) ->
            require(score in 1..5) { "Score for $dim must be between 1 and 5 (was $score)" }
        }
    }

    val weightedScore: Double
        get() {
            var totalWeight = 0.0
            var weightedSum = 0.0
            for ((dim, score) in dimensionalScores) {
                totalWeight += dim.weight
                weightedSum += score * dim.weight
            }
            return if (totalWeight > 0) weightedSum / totalWeight else 5.0
        }
}
