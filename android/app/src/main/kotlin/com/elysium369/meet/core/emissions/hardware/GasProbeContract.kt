package com.elysium369.meet.core.emissions.hardware

import kotlinx.coroutines.flow.StateFlow

enum class ProbeStatus {
    DISCONNECTED,
    WARMING_UP,
    READY,
    CALIBRATION_REQUIRED,
    CONDENSATE_DETECTED,
    LOW_SAMPLE_FLOW,
    FAULT
}

data class GasCalibration(
    val sensorSerial: String,
    val zeroTimestampMs: Long,
    val spanTimestampMs: Long,
    val referenceGasFormula: String,
    val coefficients: List<Double>,
    val ambientTempC: Double?,
    val ambientPressureKpa: Double?
)

data class GasProbeSample(
    val timestampMs: Long,
    val coVolPct: Double,
    val hcPpm: Double,
    val co2VolPct: Double,
    val o2VolPct: Double,
    val lambda: Double?,
    val status: ProbeStatus,
    val pumpFlowOk: Boolean,
    val condensateTrapOk: Boolean,
    val benchTemperatureC: Double?
) {
    val isMetrologicallyValid: Boolean
        get() = status == ProbeStatus.READY && pumpFlowOk && condensateTrapOk
}

/**
 * Hardware contract for external physical exhaust gas analyzer (MEET Gas Probe accessory).
 * When connected, promotes virtual model estimates to MEASURED physical truth class.
 */
interface GasProbeDevice {
    val status: StateFlow<ProbeStatus>
    val latestSample: StateFlow<GasProbeSample?>
    suspend fun connect(deviceAddress: String): Boolean
    suspend fun disconnect()
    suspend fun startSampling(): Boolean
    suspend fun stopSampling(): Boolean
    suspend fun performZeroCalibration(): Boolean
    suspend fun performSpanCalibration(spanCoPct: Double, spanHcPpm: Double, spanCo2Pct: Double): Boolean
}
