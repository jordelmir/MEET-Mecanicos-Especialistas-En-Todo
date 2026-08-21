package com.elysium369.meet.core.evair.bridge

import com.elysium369.meet.core.evair.baseline.DistributionSnapshot
import com.elysium369.meet.core.evair.baseline.VehicleBaselineEngine
import com.elysium369.meet.core.evair.domain.DtcSnapshot
import com.elysium369.meet.core.evair.domain.SignalFeatures
import com.elysium369.meet.core.evair.domain.TelemetryWindow
import com.elysium369.meet.core.evair.domain.VehicleIdentity
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import com.elysium369.meet.core.evair.state.VehicleStateEngine
import com.elysium369.meet.core.evair.telemetry.AnomalyDetector
import com.elysium369.meet.core.evair.telemetry.AnomalyReport
import com.elysium369.meet.core.health.PredictiveHealthEngine
import com.elysium369.meet.core.obd.ObdSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class EvairHealthSummary(
    val overallScore: Int,
    val engineScore: Int,
    val fuelScore: Int,
    val coolingScore: Int,
    val electricalScore: Int,
    val emissionsScore: Int,
    val electricalDiagnosis: String? = null,
    val alertCount: Int = 0,
    val dataPointCount: Int = 0,
)

/**
 * VehicleToolFacade — Safe, bounded, 100% READ-ONLY boundary interface.
 *
 * This is the sole gateway through which AI Agents, MCP Tools, and CLI commands
 * query vehicle state. It guarantees that the AI cannot directly access the OBD transport
 * or inject uncontrolled CAN frames.
 */
interface VehicleToolFacade {
    suspend fun identity(): VehicleIdentity
    suspend fun snapshot(): VehicleSnapshot
    suspend fun dtcs(): List<DtcSnapshot>
    suspend fun freezeFrame(): Map<String, String>
    suspend fun readiness(): Map<String, String>
    suspend fun mode06(): Map<String, String>
    suspend fun telemetryWindow(pid: String, seconds: Int = 30): TelemetryWindow
    suspend fun telemetryFeatures(pid: String, seconds: Int = 30): SignalFeatures
    suspend fun detectAnomalies(): AnomalyReport
    suspend fun baseline(pid: String): DistributionSnapshot?
    suspend fun allBaselines(): Map<String, DistributionSnapshot>
    suspend fun healthSummary(): EvairHealthSummary
}

@Singleton
class DefaultVehicleToolFacade @Inject constructor(
    private val obdSession: ObdSession,
    private val vehicleStateEngine: VehicleStateEngine,
    private val baselineEngine: VehicleBaselineEngine,
    private val anomalyDetector: AnomalyDetector,
    private val healthEngine: PredictiveHealthEngine,
) : VehicleToolFacade {

    override suspend fun identity(): VehicleIdentity = withContext(Dispatchers.IO) {
        vehicleStateEngine.currentSnapshot().vehicle
    }

    override suspend fun snapshot(): VehicleSnapshot = withContext(Dispatchers.IO) {
        vehicleStateEngine.currentSnapshot()
    }

    override suspend fun dtcs(): List<DtcSnapshot> = withContext(Dispatchers.IO) {
        vehicleStateEngine.currentSnapshot().dtcs
    }

    override suspend fun freezeFrame(): Map<String, String> = withContext(Dispatchers.IO) {
        obdSession.freezeFrame.value
    }

    override suspend fun readiness(): Map<String, String> = withContext(Dispatchers.IO) {
        vehicleStateEngine.currentSnapshot().readiness
    }

    override suspend fun mode06(): Map<String, String> = withContext(Dispatchers.IO) {
        emptyMap()
    }

    override suspend fun telemetryWindow(pid: String, seconds: Int): TelemetryWindow = withContext(Dispatchers.IO) {
        val clampedSeconds = seconds.coerceIn(1, 120)
        vehicleStateEngine.getTelemetryWindow(pid, clampedSeconds)
    }

    override suspend fun telemetryFeatures(pid: String, seconds: Int): SignalFeatures = withContext(Dispatchers.IO) {
        val clampedSeconds = seconds.coerceIn(1, 120)
        vehicleStateEngine.getSignalFeatures(pid, clampedSeconds)
    }

    override suspend fun detectAnomalies(): AnomalyReport = withContext(Dispatchers.IO) {
        val snap = vehicleStateEngine.currentSnapshot()
        val liveData = obdSession.liveData.value
        anomalyDetector.evaluateFrame(snap.vehicle.vehicleId, liveData)
    }

    override suspend fun baseline(pid: String): DistributionSnapshot? = withContext(Dispatchers.IO) {
        val snap = vehicleStateEngine.currentSnapshot()
        baselineEngine.getBaselineDistribution(snap.vehicle.vehicleId, pid)
    }

    override suspend fun allBaselines(): Map<String, DistributionSnapshot> = withContext(Dispatchers.IO) {
        val snap = vehicleStateEngine.currentSnapshot()
        baselineEngine.getAllBaselines(snap.vehicle.vehicleId)
    }

    override suspend fun healthSummary(): EvairHealthSummary = withContext(Dispatchers.IO) {
        val snap = vehicleStateEngine.currentSnapshot()
        val liveData = obdSession.liveData.value
        val report = healthEngine.computeHealthReport(
            vehicleId = snap.vehicle.vehicleId,
            currentLiveData = liveData,
            activeDtcCount = snap.dtcs.size,
            pendingDtcCount = 0,
            anomalyCount = snap.activeWarnings.size
        )

        EvairHealthSummary(
            overallScore = report.overallScore,
            engineScore = report.engineScore,
            fuelScore = report.fuelScore,
            coolingScore = report.coolingScore,
            electricalScore = report.electricalScore,
            emissionsScore = report.emissionsScore,
            electricalDiagnosis = report.electricalDiagnosis?.let { "${it.alternatorState} / ${it.batteryState}: ${it.recommendation}" },
            alertCount = report.alerts.size,
            dataPointCount = report.dataPointCount
        )
    }
}
