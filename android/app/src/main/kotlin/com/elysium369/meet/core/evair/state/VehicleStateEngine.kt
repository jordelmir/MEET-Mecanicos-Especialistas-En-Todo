package com.elysium369.meet.core.evair.state

import android.os.SystemClock
import com.elysium369.meet.core.evair.domain.ConnectionSnapshot
import com.elysium369.meet.core.evair.domain.DataQuality
import com.elysium369.meet.core.evair.domain.DtcCategory
import com.elysium369.meet.core.evair.domain.DtcSnapshot
import com.elysium369.meet.core.evair.domain.ElectricalSnapshot
import com.elysium369.meet.core.evair.domain.EmissionsSnapshot
import com.elysium369.meet.core.evair.domain.EngineSnapshot
import com.elysium369.meet.core.evair.domain.EventSeverity
import com.elysium369.meet.core.evair.domain.FuelSnapshot
import com.elysium369.meet.core.evair.domain.SignalFeatures
import com.elysium369.meet.core.evair.domain.TelemetryWindow
import com.elysium369.meet.core.evair.domain.TransmissionSnapshot
import com.elysium369.meet.core.evair.domain.VehicleDataSource
import com.elysium369.meet.core.evair.domain.VehicleEvent
import com.elysium369.meet.core.evair.domain.VehicleIdentity
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import com.elysium369.meet.core.evair.telemetry.AnomalyDetector
import com.elysium369.meet.core.evair.telemetry.TelemetryCollector
import com.elysium369.meet.core.obd.ObdDataSource
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.obd.TelemetrySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleStateEngine @Inject constructor(
    private val obdSession: ObdSession,
    private val telemetryCollector: TelemetryCollector,
    private val anomalyDetector: AnomalyDetector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow(createInitialSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<VehicleEvent> = _events.asSharedFlow()

    private var previousDtcs = emptySet<String>()

    init {
        observeObdStreams()
    }

    private fun observeObdStreams() {
        scope.launch {
            obdSession.liveData.collectLatest { liveDataMap ->
                val samples = obdSession.telemetrySamples.value
                val newSnapshot = buildSnapshot(liveDataMap, samples)
                _snapshot.value = newSnapshot

                // Check for real-time telemetry events
                evaluateTelemetryEvents(newSnapshot, liveDataMap)
            }
        }
    }

    fun currentSnapshot(): VehicleSnapshot = _snapshot.value

    fun getTelemetryWindow(pid: String, durationSeconds: Int = 30): TelemetryWindow {
        return telemetryCollector.getTelemetryWindow(pid, durationSeconds)
    }

    fun getSignalFeatures(pid: String, durationSeconds: Int = 30): SignalFeatures {
        return telemetryCollector.getSignalFeatures(pid, durationSeconds)
    }

    fun emitEvent(event: VehicleEvent) {
        _events.tryEmit(event)
    }

    private fun evaluateTelemetryEvents(snap: VehicleSnapshot, liveData: Map<String, Float>) {
        val nowMs = System.currentTimeMillis()

        // 1. Overheat Risk
        snap.engine.coolantTempC?.let { coolant ->
            if (coolant >= 108.0) {
                _events.tryEmit(
                    VehicleEvent.OverheatRisk(
                        timestampMs = nowMs,
                        severity = if (coolant >= 115.0) EventSeverity.CRITICAL else EventSeverity.WARNING,
                        coolantTempC = coolant,
                        risingRateCPerMinute = 0.0
                    )
                )
            }
        }

        // 2. Charging System Anomaly
        snap.electrical.controlModuleVoltage?.let { volt ->
            if (snap.engine.rpm != null && snap.engine.rpm > 500.0) {
                if (volt < 12.8 || volt > 15.2) {
                    _events.tryEmit(
                        VehicleEvent.ChargingSystemAnomaly(
                            timestampMs = nowMs,
                            severity = if (volt < 11.8 || volt > 15.8) EventSeverity.CRITICAL else EventSeverity.WARNING,
                            voltage = volt,
                            rpm = snap.engine.rpm,
                            trend = if (volt < 12.8) "Bajo voltaje con motor en marcha" else "Sobrecarga del alternador"
                        )
                    )
                }
            }
        }

        // 3. Fuel Trim Anomaly
        val stft = snap.fuel.stftBank1Pct
        val ltft = snap.fuel.ltftBank1Pct
        if (stft != null && ltft != null) {
            val totalTrim = stft + ltft
            if (Math.abs(totalTrim) > 18.0) {
                _events.tryEmit(
                    VehicleEvent.FuelTrimAnomaly(
                        timestampMs = nowMs,
                        severity = EventSeverity.WARNING,
                        stft = stft,
                        ltft = ltft,
                        bank = 1
                    )
                )
            }
        }

        // 4. DTC Changes
        val currentDtcCodes = snap.dtcs.map { it.code }.toSet()
        val newCodes = currentDtcCodes - previousDtcs
        val clearedCodes = previousDtcs - currentDtcCodes

        for (code in newCodes) {
            val dtcSnap = snap.dtcs.find { it.code == code }
            _events.tryEmit(
                VehicleEvent.DtcAppeared(
                    timestampMs = nowMs,
                    severity = EventSeverity.WARNING,
                    code = code,
                    category = dtcSnap?.category ?: DtcCategory.CONFIRMED,
                    description = dtcSnap?.description
                )
            )
        }

        for (code in clearedCodes) {
            _events.tryEmit(
                VehicleEvent.DtcDisappeared(
                    timestampMs = nowMs,
                    severity = EventSeverity.INFO,
                    code = code
                )
            )
        }

        previousDtcs = currentDtcCodes

        // 5. Periodic Anomaly Engine scan
        scope.launch {
            val recentWindows = telemetryCollector.getAllRecentWindows(15)
            val vehicleId = snap.vehicle.vehicleId
            val report = anomalyDetector.evaluateFrame(vehicleId, liveData, recentWindows)
            if (report.hasAnomaly && report.overallSeverity >= EventSeverity.WARNING) {
                report.isolationForestScore?.let { ifScore ->
                    if (ifScore > 0.65) {
                        _events.tryEmit(
                            VehicleEvent.IsolationForestAnomaly(
                                timestampMs = nowMs,
                                severity = report.overallSeverity,
                                anomalyScore = ifScore,
                                contributingPids = report.contributingPids
                            )
                        )
                    }
                }
            }
        }
    }

    private fun buildSnapshot(
        liveData: Map<String, Float>,
        samples: Map<String, TelemetrySample>,
    ): VehicleSnapshot {
        val nowMs = System.currentTimeMillis()
        val nowMonoNs = SystemClock.elapsedRealtimeNanos()
        val obdState = obdSession.state.value
        val vin = obdSession.vin.value

        val isConnected = obdState == ObdState.CONNECTED
        val detectedProto = obdSession.detectedProtocol.takeIf { it.isNotBlank() && it != "NONE" }

        return VehicleSnapshot(
            timestampMs = nowMs,
            monotonicTimestampNs = nowMonoNs,
            vehicle = VehicleIdentity(
                vehicleId = vin ?: "SESSION_${obdState.name}",
                vin = vin,
                make = null,
                model = null,
                year = null,
                engineType = null,
                transmissionType = null,
                label = if (vin != null) "Vehículo ($vin)" else "Enlace OBD (${obdState.name})"
            ),
            connection = ConnectionSnapshot(
                phase = obdState.name,
                hasRealEcuLink = isConnected,
                protocol = detectedProto,
                adapterQuality = if (isConnected) "CONNECTED" else null,
                transport = if (isConnected) "ACTIVE_TRANSPORT" else null,
                latencyMs = null // Strictly null when not actively timed
            ),
            engine = EngineSnapshot(
                rpm = getDoubleValue("010C", liveData, samples),
                coolantTempC = getDoubleValue("0105", liveData, samples),
                intakeTempC = getDoubleValue("010F", liveData, samples),
                engineLoadPct = getDoubleValue("0104", liveData, samples),
                timingAdvanceDeg = getDoubleValue("010E", liveData, samples),
                mapKpa = getDoubleValue("010B", liveData, samples),
                throttlePct = getDoubleValue("0111", liveData, samples),
                mafGps = getDoubleValue("0110", liveData, samples),
                speedKph = getDoubleValue("010D", liveData, samples)
            ),
            electrical = ElectricalSnapshot(
                controlModuleVoltage = getDoubleValue("0142", liveData, samples),
                batteryVoltage = getDoubleValue("0142", liveData, samples)
            ),
            fuel = FuelSnapshot(
                stftBank1Pct = getDoubleValue("0106", liveData, samples),
                ltftBank1Pct = getDoubleValue("0107", liveData, samples),
                stftBank2Pct = getDoubleValue("0108", liveData, samples),
                ltftBank2Pct = getDoubleValue("0109", liveData, samples),
                fuelPressureKpa = getDoubleValue("010A", liveData, samples),
                fuelLevelPct = getDoubleValue("012F", liveData, samples)
            ),
            transmission = TransmissionSnapshot(
                speedKph = getDoubleValue("010D", liveData, samples)
            ),
            emissions = EmissionsSnapshot(
                o2B1S1Voltage = getDoubleValue("0114", liveData, samples),
                o2B1S2Voltage = getDoubleValue("0115", liveData, samples)
            ),
            dtcs = emptyList(),
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = if (isConnected) VehicleDataSource.REAL_OBD else VehicleDataSource.OFFLINE
        )
    }

    private fun getDoubleValue(
        pid: String,
        liveData: Map<String, Float>,
        samples: Map<String, TelemetrySample>,
    ): Double? {
        val sampleVal = samples[pid]?.value ?: samples[pid.lowercase()]?.value
        if (sampleVal != null) return sampleVal
        val liveVal = liveData[pid] ?: liveData[pid.lowercase()]
        return liveVal?.toDouble()
    }

    private fun createInitialSnapshot(): VehicleSnapshot {
        return VehicleSnapshot(
            timestampMs = System.currentTimeMillis(),
            monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
            vehicle = VehicleIdentity(
                vehicleId = "UNINITIALIZED",
                vin = null,
                make = null,
                model = null,
                year = null,
                engineType = null,
                transmissionType = null,
                label = "Sin inicializar"
            ),
            connection = ConnectionSnapshot(
                phase = "DISCONNECTED",
                hasRealEcuLink = false,
                protocol = null,
                adapterQuality = null,
                transport = null,
                latencyMs = null
            ),
            engine = EngineSnapshot(),
            electrical = ElectricalSnapshot(),
            fuel = FuelSnapshot(),
            transmission = null,
            emissions = EmissionsSnapshot(),
            dtcs = emptyList(),
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = VehicleDataSource.OFFLINE
        )
    }
}
