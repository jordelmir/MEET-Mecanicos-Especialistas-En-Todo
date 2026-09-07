package com.elysium369.meet.provider.domain.models

data class ProviderPreferences(
    val autoAcceptDispatches: Boolean = false,
    val maxRadiusKm: Double = 25.0,
    val preferredNavigationApp: String = "GOOGLE_MAPS",
    val audioAlertsEnabled: Boolean = true,
) {
    init {
        require(maxRadiusKm in 1.0..500.0) { "Max radius km must be between 1.0 and 500.0, got $maxRadiusKm" }
        require(preferredNavigationApp.isNotBlank()) { "Preferred navigation app cannot be blank" }
    }
}
