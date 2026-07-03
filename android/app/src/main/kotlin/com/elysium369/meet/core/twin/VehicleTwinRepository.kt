package com.elysium369.meet.core.twin

import com.elysium369.meet.data.local.dao.VehicleTwinDao
import com.elysium369.meet.data.local.entities.TwinAnomalyEntity
import com.elysium369.meet.data.local.entities.VehicleTwinProfileEntity
import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository layer for the Digital Twin.
 *
 * Owns:
 * - The [VehicleTwinEngine] (pure math + state).
 * - The persistence layer ([VehicleTwinDao]) — previously reached directly by callers.
 * - The [TwinFrameSource] selection (real OBD vs simulated).
 *
 * Every datum the UI consumes is wrapped in [DiagnosticValue] so the UI MUST
 * surface provenance badges. There is no public API to retrieve a raw
 * [TwinAnomalyEntity] / [VehicleTwinProfileEntity] without provenance.
 *
 * Why a repository?
 * - Decouples ViewModel from Room (testable with fakes).
 * - Centralizes the REAL / SIMULATED switch.
 * - Lets us later add caching, sync, multi-source aggregation without touching
 *   the engine or the UI.
 */
@Singleton
class VehicleTwinRepository @Inject constructor(
    private val engine: VehicleTwinEngine,
    private val dao: VehicleTwinDao,
    private val realSource: ObdTwinFrameSource,
    private val simulatedSource: SimulatedTwinFrameSource
) {

    /**
     * Currently active source. Defaults to SIMULATED so the feature works
     * offline / in demos / before a device is paired. The UI must show
     * `currentProvenance.displayLabel` somewhere prominent.
     */
    private val activeSource = MutableStateFlow<TwinFrameSource>(simulatedSource)

    /** Hot stream of the active source so the UI can react when we switch. */
    val activeSourceFlow = activeSource.asStateFlow()

    /** Convenience: provenance of the active source. */
    val currentProvenance: DiagnosticProvenance
        get() = activeSource.value.provenance

    /** Switch to the real OBD source (when a device is connected). */
    fun useRealSource() {
        activeSource.value = realSource
    }

    /** Switch back to simulated (default; safe offline). */
    fun useSimulatedSource() {
        activeSource.value = simulatedSource
    }

    /** Stream of frames for the UI, already provenance-wrapped. */
    fun frameStream(vehicleId: String): Flow<DiagnosticValue<TwinLiveFrame>> =
        activeSource.value.frames(vehicleId)

    // ─────────────────────────────────────────────────────────────────────
    // Profile & anomaly observation — always wrapped in DiagnosticValue
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Observe the persisted twin profile for [vehicleId].
     *
     * If a profile exists in the DAO we wrap it with [DiagnosticProvenance.Offline]
     * (it's persisted data, not a live OBD read). If it doesn't exist, we return
     * [DiagnosticProvenance.SinEnlace] with a null value so the UI knows it must
     * call [trainProfileFor] before showing metrics.
     */
    fun observeProfile(vehicleId: String): Flow<DiagnosticValue<VehicleTwinProfileEntity?>> =
        dao.getTwinProfileFlow(vehicleId).map { profile ->
            if (profile == null) {
                DiagnosticValue.sinEnlace<VehicleTwinProfileEntity?>()
            } else {
                DiagnosticValue.offline(profile)
            }
        }

    /**
     * Observe anomalies for [vehicleId], each wrapped with [DiagnosticProvenance.Offline]
     * because the anomaly stream is the result of past engine runs over past frames.
     */
    fun observeAnomalies(vehicleId: String): Flow<List<DiagnosticValue<TwinAnomalyEntity>>> =
        dao.getAnomaliesForVehicle(vehicleId).map { list ->
            list.map { DiagnosticValue.offline(it) }
        }

    /**
     * Observe the live anomaly stream the engine maintains in memory
     * (newest anomalies only, capped to the last 40).
     */
    fun observeLiveAnomalies(vehicleId: String): Flow<List<DiagnosticValue<TwinAnomalyEntity>>> =
        engine.liveAnomalies.map { list ->
            list.filter { it.vehicleId == vehicleId }.map {
                DiagnosticValue(it, currentProvenance, it.timestamp)
            }
        }

    // ─────────────────────────────────────────────────────────────────────
    // Frame ingestion
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Send a frame through the engine. The frame itself already has a provenance
     * (set by the source); we pass the raw values to the engine and the
     * resulting anomalies inherit [currentProvenance] when surfaced via
     * [observeLiveAnomalies].
     */
    suspend fun ingestFrame(vehicleId: String, pidValues: Map<String, Float>) {
        engine.evaluateFrame(vehicleId, pidValues)
    }

    /**
     * Train or refresh the profile for [vehicleId] using [history] (oldest first).
     * If history is empty and a profile already exists, returns the existing one.
     */
    suspend fun trainProfileFor(vehicleId: String, history: List<Map<String, Float>>): VehicleTwinProfileEntity =
        engine.trainOrInitializeProfile(vehicleId, history)

    /**
     * Pump a simulated frame through the simulated source. Useful for demos
     * and for the in-app "Simular anomalía" button.
     */
    suspend fun emitSimulatedFrame(vehicleId: String, pidValues: Map<String, Float>) {
        simulatedSource.emit(vehicleId, pidValues)
    }

    /**
     * Clear all persisted anomalies for a vehicle.
     */
    suspend fun clearAnomalies(vehicleId: String) {
        dao.clearAnomaliesForVehicle(vehicleId)
        engine.clearLiveAnomalies()
    }
}

/**
 * Hot Flow of the persisted twin profile.
 *
 * The DAO does not currently expose a Flow variant for getTwinProfile (only the
 * suspend one-shot). For the repository's `observeProfile` contract we wrap the
 * one-shot in a `flow { emit(...) }` so consumers still get a Flow.
 *
 * If we later need reactive updates we can add a `getTwinProfileFlow` to the DAO
 * and switch this to `dao.getTwinProfileFlow(vehicleId)` directly.
 */
private fun VehicleTwinDao.getTwinProfileFlow(vehicleId: String): Flow<VehicleTwinProfileEntity?> =
    kotlinx.coroutines.flow.flow {
        emit(getTwinProfile(vehicleId))
    }