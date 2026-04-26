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
    val engine: String,
    val vin: String,
    val plate: String,
    val photoPath: String?,
    val odometerKm: Long,
    val createdAt: Long,
    val syncedAt: Long?
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
    val resolvedAt: Long?,
    val occurrenceCount: Int,
    val freezeFrameJson: String?
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

@Entity(tableName = "dtc_definitions")
data class DtcDefinitionEntity(
    @PrimaryKey val code: String,
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
