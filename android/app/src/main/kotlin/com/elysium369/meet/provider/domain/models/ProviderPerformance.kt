package com.elysium369.meet.provider.domain.models

data class ProviderPerformance(
    val ratingAverage: Double,
    val totalRatingsCount: Int,
    val acceptanceRatePct: Double,
    val cancellationRatePct: Double,
    val totalCompletedOrders: Int,
    val trustTier: String,
) {
    init {
        require(ratingAverage in 0.0..5.0) { "Rating average must be in 0.0..5.0, got $ratingAverage" }
        require(totalRatingsCount >= 0) { "Total ratings count must be non-negative, got $totalRatingsCount" }
        require(acceptanceRatePct in 0.0..100.0) { "Acceptance rate must be in 0..100, got $acceptanceRatePct" }
        require(cancellationRatePct in 0.0..100.0) { "Cancellation rate must be in 0..100, got $cancellationRatePct" }
        require(totalCompletedOrders >= 0) { "Total completed orders must be non-negative, got $totalCompletedOrders" }
        require(trustTier.isNotBlank()) { "Trust tier cannot be blank" }
    }
}
