package com.elysium369.meet.core.domain

/**
 * MEET Vehicle Life OS — Canonical Vehicle Context.
 * Passed to all subdomains and view models to ensure unambiguous vehicle identity
 * and prevent cross-contamination between Garage, OBD, Repair, Passport, Parts, Timeline, and Sale Mode.
 */
data class VehicleContext(
    val vehicleId: String,
    val ownerPrincipalId: String,
    val vehicleBindingId: String? = null,
    val vin: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val platform: String? = null,
    val engineCode: String? = null,
    val fuelType: String? = null,
    val transmissionType: String? = null,
    val odometerKm: Int? = null,
    val activeDiagnosticSessionId: String? = null
) {
    val displayName: String
        get() = when {
            !make.isNullOrBlank() && !model.isNullOrBlank() && year != null -> "$make $model ($year)"
            !make.isNullOrBlank() && !model.isNullOrBlank() -> "$make $model"
            !vin.isNullOrBlank() -> "VIN: $maskedVin"
            else -> "Vehículo: $vehicleId"
        }

    val maskedVin: String
        get() = vin?.let {
            if (it.length >= 4) "*".repeat(it.length - 4) + it.takeLast(4) else it
        } ?: "N/A"
}
