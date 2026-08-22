package com.elysium369.meet.core.services.serviceos

import java.util.UUID

enum class ProviderType(val displayName: String) {
    INDEPENDENT_MECHANIC("Mecánico Independiente"),
    WORKSHOP("Taller Mecánico"),
    MOBILE_MECHANIC("Mecánico a Domicilio"),
    AUTO_ELECTRICIAN("Electricidad y Redes CAN"),
    TOW_SERVICE("Servicio de Grúa"),
    PARTS_DISTRIBUTOR("Repuestera / Distribuidor"),
    TIRE_BATTERY_SPECIALIST("Llantera y Baterías"),
    AC_COOLING_SPECIALIST("Aire Acondicionado y Radiadores"),
    TRANSMISSION_SPECIALIST("Transmisiones Automáticas"),
    INSPECTION_CENTER("Centro de Peritaje e Inspección")
}

enum class OrganizationRole {
    OWNER,
    SHOP_MANAGER,
    SERVICE_ADVISOR,
    TECHNICIAN,
    DIAGNOSTICIAN,
    PARTS_SPECIALIST,
    CASHIER,
    VIEW_ONLY
}

data class EquipmentItem(
    val id: String,
    val name: String,
    val isVerifiedByMeet: Boolean = false,
    val serialNumberMasked: String? = null
)

data class BayFacility(
    val bayId: String = UUID.randomUUID().toString(),
    val name: String,
    val isOccupied: Boolean = false,
    val currentVehicleLabel: String? = null,
    val currentWorkOrderId: String? = null,
    val assignedTechnicianName: String? = null
)

data class CredentialCertificate(
    val credentialId: String = UUID.randomUUID().toString(),
    val documentType: String,
    val issuer: String,
    val validUntilMs: Long,
    val isVerified: Boolean = false
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > validUntilMs
}

data class ProviderOrganization(
    val id: String = UUID.randomUUID().toString(),
    val legalName: String,
    val commercialName: String,
    val type: ProviderType,
    val isVerifiedByMeet: Boolean = false,
    val physicalAddress: String?,
    val mobileCoverageRadiusKm: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val specialties: List<String> = emptyList(),
    val supportedMakes: List<String> = emptyList(),
    val verifiedEquipments: List<EquipmentItem> = emptyList(),
    val bays: List<BayFacility> = emptyList(),
    val credentials: List<CredentialCertificate> = emptyList(),
    val customerRating: Double = 5.0,
    val totalVerifiedRepairsCount: Int = 0,
    val onTimeRatePercent: Int = 100,
    val isEmergencyModeActive: Boolean = false
)
