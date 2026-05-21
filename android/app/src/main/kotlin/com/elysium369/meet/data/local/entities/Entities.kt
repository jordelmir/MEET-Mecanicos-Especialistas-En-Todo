package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val year: Int,
    val make: String,
    val model: String,
    val engine: String, // Keep for backward compatibility or as summary
    val displacementCc: Int,
    val engineTech: String, // VVT, Turbo, etc
    val transmissionType: String, // Manual, Automatic
    val transmissionSubtype: String, // CVT, DSG, 6AT
    val fuelType: String,
    val vin: String,
    val plate: String,
    val photoPath: String?,
    val odometerKm: Long,
    val createdAt: Long,
    val syncedAt: Long?,
    val businessId: String? = null,
    val fleetId: String? = null,
    val assignedDriverId: String? = null
)

@Entity(tableName = "diagnostic_sessions")
data class DiagnosticSessionEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val adapterFingerprint: String,
    val protocolUsed: String,
    val startedAt: Long,
    val endedAt: Long?,
    val dtcSnapshot: String, // JSON
    val liveDataSummary: String, // JSON
    val synced: Boolean
)

@Entity(tableName = "dtc_events")
data class DtcEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val vehicleId: String,
    val code: String,
    val description: String,
    val severity: String,
    val status: String, // ACTIVE/PENDING/PERMANENT
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val resolvedAt: Long?,
    val occurrenceCount: Int,
    val freezeFrameJson: String?,
    val synced: Boolean = false
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceKm: Float,
    val durationSeconds: Long,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val maxRpm: Float,
    val avgRpm: Float,
    val maxTempC: Float,
    val fuelEfficiency: Float?,
    val ecoScore: Int,
    val gpsTrackJson: String?,
    val synced: Boolean
)

@Entity(tableName = "adapter_profiles")
data class AdapterProfileEntity(
    @PrimaryKey val deviceAddress: String,
    val deviceName: String,
    val chipVersion: String,
    val isClone: Boolean,
    val optimalBaudRate: Int,
    val commandDelayMs: Long,
    val supportsSTN: Boolean,
    val lastUsedAt: Long,
    val successfulConnections: Int,
    val failedConnections: Int
)

@Entity(
    tableName = "dtc_definitions",
    primaryKeys = ["code", "manufacturer"]
)
data class DtcDefinitionEntity(
    val code: String,
    val manufacturer: String = "GENERIC",
    val descriptionEs: String,
    val descriptionEn: String,
    val system: String,
    val severity: String,
    val possibleCauses: String,
    val urgency: String
)

@Entity(tableName = "maintenance_alerts")
data class MaintenanceAlertEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val type: String, // OIL/FILTER/TIMING/etc
    val intervalKm: Long,
    val lastDoneKm: Long,
    val nextDueKm: Long,
    val notes: String?
)

@Entity(tableName = "ai_consults")
data class AiConsultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val dtcCodes: String,
    val prompt: String,
    val response: String,
    val model: String,
    val createdAt: Long,
    val exportedAsPdf: Boolean
)

/**
 * Registro de mantenimiento preventivo — Aceite, filtros, bujías, etc.
 * Cada entrada = un servicio realizado con fecha, km y detalles.
 */
@Entity(tableName = "maintenance_logs")
data class MaintenanceLogEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val category: String,       // OIL, AIR_FILTER, CABIN_FILTER, SPARK_PLUGS, COOLANT, BRAKE_FLUID, TRANSMISSION, TIMING_BELT, TIRES, BATTERY, WIPERS, FUEL_FILTER, PCV_VALVE, DIFFERENTIAL, POWER_STEERING
    val description: String,    // "Cambio de aceite sintético 5W-30"
    val brand: String,          // "Mobil 1", "Castrol", etc.
    val specification: String,  // "5W-30 API SP", "NGK Iridium", etc.
    val datePerformed: Long,    // timestamp
    val odometerAtService: Long, // km al momento del servicio
    val intervalKm: Int,        // intervalo recomendado en km
    val intervalMonths: Int,    // intervalo en meses
    val nextDueKm: Long,        // próximo servicio en km
    val nextDueDate: Long,      // próximo servicio (timestamp)
    val cost: Float,            // costo del servicio
    val currency: String,       // MXN, USD, etc.
    val workshopName: String,   // "Taller Pepito", "Autozone", "Yo mismo"
    val notes: String,          // notas adicionales
    val receiptPhotoPath: String?, // foto del recibo
    val createdAt: Long
)

/**
 * Registro de reparaciones correctivas — Piezas cambiadas por daño o desgaste.
 * Incluye vida útil esperada y si es pieza de reemplazo periódico.
 */
@Entity(tableName = "repair_history")
data class RepairHistoryEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val partCategory: String,   // ENGINE, SUSPENSION, BRAKES, ELECTRICAL, COOLING, FUEL, EXHAUST, TRANSMISSION, STEERING, BODY, AC, IGNITION
    val partName: String,       // "Bomba de agua", "Alternador", "Sensor O2 B1S1"
    val partNumber: String,     // número de parte OEM/aftermarket
    val brand: String,          // "Denso", "Bosch", "ACDelco"
    val isOem: Boolean,         // si es pieza original
    val reason: String,         // "Daño", "Desgaste", "Preventivo", "Mejora"
    val relatedDtc: String?,    // DTC que causó la reparación "P0420"
    val datePerformed: Long,
    val odometerAtRepair: Long,
    val expectedLifeKm: Int?,   // vida útil esperada (null si no aplica)
    val expectedLifeMonths: Int?, // vida útil en meses
    val nextReplacementKm: Long?, // cuándo reemplazar de nuevo
    val isPeriodic: Boolean,    // si se cambia periódicamente (ej: pastillas de freno = sí, alternador = no)
    val laborCost: Float,
    val partCost: Float,
    val totalCost: Float,
    val currency: String,
    val workshopName: String,
    val warrantyMonths: Int,    // garantía de la pieza/trabajo
    val warrantyKm: Int,
    val notes: String,
    val photoPath: String?,
    val createdAt: Long
)

@Entity(tableName = "business_profiles")
data class BusinessProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val taxId: String?,
    val planType: String,
    val maxVehicles: Int,
    val createdAt: Long,
    val ownerUserId: String
)

@Entity(tableName = "fleets")
data class FleetEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val name: String,
    val description: String?,
    val inviteCode: String = "",
    val createdAt: Long
)

@Entity(tableName = "fleet_members")
data class FleetMemberEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val userId: String,
    val role: String,
    val email: String,
    val inviteStatus: String,
    val joinedAt: Long?,
    val fleetId: String? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val senderId: String,
    val receiverId: String,
    val messageText: String?,
    val messageType: String, // "TEXT", "AUDIO", "FILE", "DTC_ALERT"
    val fileLocalPath: String?,
    val fileRemoteUrl: String?,
    val durationSeconds: Int, // for voice notes
    val timestamp: Long,
    val status: String // "PENDING", "SENT", "DELIVERED", "READ"
)

@Entity(tableName = "chat_blocklist")
data class ChatBlocklistEntity(
    @PrimaryKey val id: String, // businessId_blockerId_blockedId
    val businessId: String,
    val blockerUserId: String,
    val blockedUserId: String,
    val blockedAt: Long
)

@Entity(tableName = "dvir_reports")
data class DvirReportEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val driverId: String,
    val timestamp: Long,
    val brakesOk: Boolean,
    val lightsOk: Boolean,
    val tiresOk: Boolean,
    val fluidsOk: Boolean,
    val batteryOk: Boolean,
    val remarks: String?,
    val signaturePath: String?
)
