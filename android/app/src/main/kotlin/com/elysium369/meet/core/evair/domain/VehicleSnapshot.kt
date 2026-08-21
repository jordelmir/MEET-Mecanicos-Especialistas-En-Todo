package com.elysium369.meet.core.evair.domain

import kotlinx.serialization.Serializable

/**
 * VehicleSnapshot — Immutable, serializable, deterministic representation
 * of the complete vehicle state at a point in time.
 *
 * This is the single source of truth that EVAIR components consume.
 * It is composed from ObdSession's scattered StateFlows, never from
 * direct bus access.
 *
 * Contract:
 * - Immutable (data class, no var)
 * - Serializable (Kotlin Serialization)
 * - Reproducible (same inputs → same snapshot)
 * - Testeable (no side effects, no I/O)
 */
@Serializable
data class VehicleSnapshot(
    val timestampMs: Long,
    val monotonicTimestampNs: Long,
    val vehicle: VehicleIdentity,
    val connection: ConnectionSnapshot,
    val engine: EngineSnapshot,
    val electrical: ElectricalSnapshot,
    val fuel: FuelSnapshot,
    val transmission: TransmissionSnapshot?,
    val emissions: EmissionsSnapshot,
    val dtcs: List<DtcSnapshot>,
    val readiness: Map<String, String>,
    val activeWarnings: List<String>,
    val dataSource: VehicleDataSource,
)

@Serializable
data class VehicleIdentity(
    val vehicleId: String,
    val vin: String?,
    val make: String?,
    val model: String?,
    val year: Int?,
    val engineType: String?,
    val transmissionType: String?,
    val label: String?,
)

@Serializable
data class ConnectionSnapshot(
    val phase: String,
    val hasRealEcuLink: Boolean,
    val protocol: String?,
    val adapterQuality: String?,
    val transport: String?,
    val latencyMs: Long?,
)

@Serializable
data class EngineSnapshot(
    val rpm: Double? = null,
    val coolantTempC: Double? = null,
    val intakeTempC: Double? = null,
    val engineLoadPct: Double? = null,
    val timingAdvanceDeg: Double? = null,
    val mapKpa: Double? = null,
    val throttlePct: Double? = null,
    val mafGps: Double? = null,
    val speedKph: Double? = null,
)

@Serializable
data class ElectricalSnapshot(
    val controlModuleVoltage: Double? = null,
    val batteryVoltage: Double? = null,
)

@Serializable
data class FuelSnapshot(
    val stftBank1Pct: Double? = null,
    val ltftBank1Pct: Double? = null,
    val stftBank2Pct: Double? = null,
    val ltftBank2Pct: Double? = null,
    val commandedEquivalenceRatio: Double? = null,
    val fuelPressureKpa: Double? = null,
    val fuelLevelPct: Double? = null,
)

@Serializable
data class TransmissionSnapshot(
    val speedKph: Double? = null,
    val gearEstimated: Int? = null,
)

@Serializable
data class EmissionsSnapshot(
    val catalystTempC: Double? = null,
    val o2B1S1Voltage: Double? = null,
    val o2B1S2Voltage: Double? = null,
    val o2B2S1Voltage: Double? = null,
    val o2B2S2Voltage: Double? = null,
)

@Serializable
data class DtcSnapshot(
    val code: String,
    val category: DtcCategory,
    val description: String?,
    val firstSeenMs: Long? = null,
    val occurrenceCount: Int = 1,
)

@Serializable
enum class DtcCategory {
    CONFIRMED,
    PENDING,
    PERMANENT,
}

@Serializable
enum class VehicleDataSource {
    REAL_OBD,
    SIMULATED,
    REPLAY,
    OFFLINE,
}
