package com.elysium369.meet.core.services.tow

import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.services.kernel.Money
import java.util.UUID

/**
 * Hard physical capabilities supported by towing units and operators.
 * Used for deterministic matching: an incompatible truck is NEVER dispatched.
 */
enum class TowCapabilities(val displayName: String) {
    FLATBED("Plataforma Hidráulica"),
    WHEEL_LIFT("Arrastre por Eje / Wheel Lift"),
    HEAVY_DUTY("Carga Pesada / Camiones"),
    MOTORCYCLE("Soporte para Motocicletas"),
    EV_COMPATIBLE("Compatible con Vehículos Eléctricos"),
    LOW_CLEARANCE("Perfil Bajo / Autos Deportivos"),
    LOCKED_WHEELS("Patines para Ruedas Bloqueadas"),
    NON_ROLLING_VEHICLE("Vehículo Inmovilizado / Sin Rodar"),
    ACCIDENT_RECOVERY("Rescate en Accidente / Vuelco"),
    WINCH("Cabrestante / Winche de Alta Capacidad"),
    OFFROAD_RECOVERY("Rescate 4x4 / Fuera de Camino"),
    UNDERGROUND_PARKING("Acceso a Sótanos / Parqueos Subterráneos"),
}

/**
 * Physical tow rig representation.
 * Invariant: TowOperator != TowUnit (an operator may operate different physical rigs).
 */
data class TowUnit(
    val towUnitId: String,
    val operatorId: UUID,
    val brandModel: String,
    val licensePlate: String,
    val capabilities: Set<TowCapabilities>,
    val maxWeightKg: Int = 3500,
    val isVerified: Boolean = true,
    val isAvailable: Boolean = true,
)

/**
 * Verification milestone along vehicle custody handoff.
 */
enum class TowCustodyCheckpoint(val displayName: String) {
    PRE_LOAD("Inspección Previa a Carga"),
    LOADED_SECURED("Vehículo Cargado y Asegurado"),
    DESTINATION_ARRIVAL("Llegada al Destino"),
    POST_UNLOAD("Inspección Posterior a Descarga"),
    DELIVERED("Entrega Confirmada"),
}

/**
 * Immutable forensic record linking tow operations to cryptographically attested evidence.
 */
data class TowCustodyRecord(
    val checkpoint: TowCustodyCheckpoint,
    val evidenceHash: String,
    val recordedAtEpochMs: Long,
    val recordedByActorId: UUID,
    val notes: String? = null,
) {
    init {
        require(evidenceHash.isNotBlank()) { "Custody evidence hash cannot be blank" }
        require(recordedAtEpochMs > 0) { "Custody timestamp must be positive" }
    }
}

/**
 * Authoritative domain aggregate for towing and roadside recovery operations.
 * Maintained by TowStateEngine and TowCommandRepository.
 */
data class TowJob(
    val jobId: UUID,
    val customerId: UUID,
    val customerName: String,
    val customerPhone: String,
    val vehicleVin: String? = null,
    val vehicleSummary: String,
    val pickupLocation: GeoPoint,
    val pickupAddress: String,
    val destinationLocation: GeoPoint? = null,
    val destinationAddress: String? = null,
    val requiredCapabilities: Set<TowCapabilities> = setOf(TowCapabilities.FLATBED),
    val assignedUnit: TowUnit? = null,
    val assignedOperatorName: String? = null,
    val assignedOperatorPhone: String? = null,
    val state: TowState = TowState.REQUESTED,
    val estimatedPrice: Money? = null,
    val quotedPrice: Money? = null,
    val authorizedPrice: Money? = null,
    val finalSettlement: Money? = null,
    val custodyRecords: List<TowCustodyRecord> = emptyList(),
    val serverVersion: Long = 1L,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    init {
        require(customerName.isNotBlank()) { "Customer name cannot be blank" }
        require(vehicleSummary.isNotBlank()) { "Vehicle summary cannot be blank" }
    }
}
