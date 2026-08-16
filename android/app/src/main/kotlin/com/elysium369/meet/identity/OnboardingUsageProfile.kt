package com.elysium369.meet.identity

/**
 * Stable cross-layer identifiers for the first experience selected by a user.
 *
 * This is an intent/profile choice, not a verification result. In particular,
 * [RIDE_DRIVER] must still pass the independent driver and vehicle review gates.
 */
enum class OnboardingUsageProfile(
    val storageId: String,
    val displayLabel: String,
    val description: String,
    val platformRole: String,
    val mobilityRole: String? = null,
    val requiresProviderVerification: Boolean = false,
) {
    OWNER(
        storageId = "owner",
        displayLabel = "Dueño de carro",
        description = "simple y guiado",
        platformRole = "driver",
    ),
    MECHANIC(
        storageId = "mechanic",
        displayLabel = "Mecánico",
        description = "diagnóstico técnico",
        platformRole = "mechanic",
        requiresProviderVerification = true,
    ),
    WORKSHOP(
        storageId = "workshop",
        displayLabel = "Taller",
        description = "clientes y órdenes",
        platformRole = "workshop_owner",
        requiresProviderVerification = true,
    ),
    FLEET(
        storageId = "fleet",
        displayLabel = "Flota",
        description = "riesgo y mantenimiento",
        platformRole = "fleet_manager",
    ),
    RIDE_PASSENGER(
        storageId = "ride_passenger",
        displayLabel = "Usuario de viajes",
        description = "solicitar viajes y soporte",
        platformRole = "ride_passenger",
        mobilityRole = "PASSENGER",
    ),
    RIDE_DRIVER(
        storageId = "ride_driver",
        displayLabel = "Conductor",
        description = "ofrecer viajes tras verificación",
        platformRole = "ride_driver",
        mobilityRole = "DRIVER",
        requiresProviderVerification = true,
    );

    companion object {
        fun fromStorageId(value: String?): OnboardingUsageProfile? =
            entries.firstOrNull { it.storageId == value }
    }
}
