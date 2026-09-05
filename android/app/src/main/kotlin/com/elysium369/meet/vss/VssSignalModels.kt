package com.elysium369.meet.vss

import com.elysium369.meet.authority.VerificationLevel

/**
 * SignalProtocol — Identifies the underlying transport protocol.
 */
enum class SignalProtocol {
    OBD2_STANDARD,          // Standard SAE J1979 / ISO 15031-5 PID mode
    UDS_DIAGNOSTIC,         // ISO 14229 Unified Diagnostic Services DID
    CAN_RAW,                // Raw CAN frame (ISO 11898)
    OEM_TELEMATICS_API,     // OEM cloud or REST telemetry endpoint
    SIMULATED_REPLAY,       // Digital Twin or laboratory deterministic replay
}

/**
 * SignalProvenance — Preserves the exact physical source and hardware trace.
 */
data class SignalProvenance(
    val protocol: SignalProtocol,
    val rawIdentifier: String,      // e.g. "010D" for speed, "F190" for VIN DID, "0x280" for CAN ID
    val rawPayloadHex: String,      // Raw hex representation of bytes captured on bus
    val ecuAddress: String? = null, // e.g. "7E0" (Engine ECM), "7E1" (TCM)
    val busSpeedBps: Int? = 500000, // e.g. 500kbps High Speed CAN
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
    val sampleSequence: Long = 0L,
)

/**
 * VssDataType — Supported COVESA VSS signal types.
 */
enum class VssDataType {
    FLOAT,
    DOUBLE,
    INT32,
    INT64,
    BOOLEAN,
    STRING,
    STRING_ARRAY,
}

/**
 * VssSignalMetadata — Static specification of a COVESA VSS 4.1 signal.
 */
data class VssSignalMetadata(
    val path: String,
    val dataType: VssDataType,
    val unit: String,
    val description: String,
    val min: Double? = null,
    val max: Double? = null,
)

/**
 * Canonical COVESA VSS 4.1 Standard Paths and catalog.
 */
object VssStandardPaths {
    const val VEHICLE_SPEED = "Vehicle.Speed"
    const val ENGINE_SPEED = "Vehicle.Powertrain.CombustionEngine.Speed"
    const val ENGINE_ECT = "Vehicle.Powertrain.CombustionEngine.ECT"
    const val ENGINE_MAP = "Vehicle.Powertrain.CombustionEngine.MAP"
    const val ENGINE_MAF = "Vehicle.Powertrain.CombustionEngine.MAF"
    const val ENGINE_TPS = "Vehicle.Powertrain.CombustionEngine.TPS"
    const val ENGINE_STFT_B1 = "Vehicle.Powertrain.CombustionEngine.FuelTrim.ShortTermBank1"
    const val ENGINE_LTFT_B1 = "Vehicle.Powertrain.CombustionEngine.FuelTrim.LongTermBank1"
    const val BATTERY_SOC = "Vehicle.Powertrain.TractionBattery.StateOfCharge"
    const val BATTERY_VOLTAGE = "Vehicle.Powertrain.TractionBattery.NetVoltage"
    const val TIRE_PRESSURE_FL = "Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.Pressure"
    const val OBD_DTC_LIST = "Vehicle.OBD.DTCList"
    const val LOCATION_LATITUDE = "Vehicle.CurrentLocation.Latitude"
    const val LOCATION_LONGITUDE = "Vehicle.CurrentLocation.Longitude"
    const val LOCATION_HEADING = "Vehicle.CurrentLocation.Heading"
    const val LOCATION_ALTITUDE = "Vehicle.CurrentLocation.Altitude"

    val CATALOG: Map<String, VssSignalMetadata> = mapOf(
        VEHICLE_SPEED to VssSignalMetadata(VEHICLE_SPEED, VssDataType.FLOAT, "km/h", "Vehicle speed", 0.0, 400.0),
        ENGINE_SPEED to VssSignalMetadata(ENGINE_SPEED, VssDataType.FLOAT, "rpm", "Engine crankshaft rotation speed", 0.0, 16383.75),
        ENGINE_ECT to VssSignalMetadata(ENGINE_ECT, VssDataType.FLOAT, "celsius", "Engine coolant temperature", -40.0, 215.0),
        ENGINE_MAP to VssSignalMetadata(ENGINE_MAP, VssDataType.FLOAT, "kPa", "Manifold absolute pressure", 0.0, 255.0),
        ENGINE_MAF to VssSignalMetadata(ENGINE_MAF, VssDataType.FLOAT, "g/s", "Mass air flow sensor reading", 0.0, 655.35),
        ENGINE_TPS to VssSignalMetadata(ENGINE_TPS, VssDataType.FLOAT, "%", "Throttle position sensor", 0.0, 100.0),
        ENGINE_STFT_B1 to VssSignalMetadata(ENGINE_STFT_B1, VssDataType.FLOAT, "%", "Short term fuel trim bank 1", -100.0, 99.2),
        ENGINE_LTFT_B1 to VssSignalMetadata(ENGINE_LTFT_B1, VssDataType.FLOAT, "%", "Long term fuel trim bank 1", -100.0, 99.2),
        BATTERY_SOC to VssSignalMetadata(BATTERY_SOC, VssDataType.FLOAT, "%", "High voltage battery state of charge", 0.0, 100.0),
        BATTERY_VOLTAGE to VssSignalMetadata(BATTERY_VOLTAGE, VssDataType.FLOAT, "V", "Electrical net voltage", 0.0, 1000.0),
        TIRE_PRESSURE_FL to VssSignalMetadata(TIRE_PRESSURE_FL, VssDataType.FLOAT, "kPa", "Front left tire pressure", 0.0, 500.0),
        OBD_DTC_LIST to VssSignalMetadata(OBD_DTC_LIST, VssDataType.STRING_ARRAY, "", "Active diagnostic trouble codes"),
        LOCATION_LATITUDE to VssSignalMetadata(LOCATION_LATITUDE, VssDataType.DOUBLE, "degrees", "Current latitude", -90.0, 90.0),
        LOCATION_LONGITUDE to VssSignalMetadata(LOCATION_LONGITUDE, VssDataType.DOUBLE, "degrees", "Current longitude", -180.0, 180.0),
        LOCATION_HEADING to VssSignalMetadata(LOCATION_HEADING, VssDataType.FLOAT, "degrees", "Current heading relative to true north", 0.0, 360.0),
        LOCATION_ALTITUDE to VssSignalMetadata(LOCATION_ALTITUDE, VssDataType.DOUBLE, "m", "Altitude above sea level"),
    )
}

/**
 * VssSignalSnapshot — Realtime value of a VSS signal node with full provenance and verification level.
 */
data class VssSignalSnapshot(
    val path: String,
    val value: Any,
    val metadata: VssSignalMetadata,
    val provenance: SignalProvenance,
    val verificationLevel: VerificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
    val timestampEpochMs: Long = System.currentTimeMillis(),
) {
    fun asFloat(): Float? = (value as? Number)?.toFloat()
    fun asDouble(): Double? = (value as? Number)?.toDouble()
    fun asInt(): Int? = (value as? Number)?.toInt()
    fun asString(): String = value.toString()
    @Suppress("UNCHECKED_CAST")
    fun asStringList(): List<String>? = value as? List<String>
}
