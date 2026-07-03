package com.elysium369.meet.core.twin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticValue
import com.elysium369.meet.data.local.entities.TwinAnomalyEntity
import com.elysium369.meet.data.local.entities.VehicleTwinProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * UI state for the Digital Twin screen.
 *
 * All three values are wrapped in [DiagnosticValue] so the screen can render
 * the right provenance badge and never silently display simulated data as real.
 */
data class TwinUiState(
    val vehicleId: String,
    val activeProvenance: DiagnosticProvenance,
    val profile: DiagnosticValue<VehicleTwinProfileEntity?> = DiagnosticValue.sinEnlace(),
    val persistedAnomalies: List<DiagnosticValue<TwinAnomalyEntity>> = emptyList(),
    val liveAnomalies: List<DiagnosticValue<TwinAnomalyEntity>> = emptyList()
) {
    val isOperational: Boolean
        get() = profile.value != null

    val healthScore: Int?
        get() = profile.value?.healthScore
}

@HiltViewModel
class VehicleTwinViewModel @Inject constructor(
    private val repository: VehicleTwinRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vehicleId: String = savedStateHandle.get<String>(KEY_VEHICLE_ID) ?: DEFAULT_VEHICLE_ID

    private val activeProvenance = MutableStateFlow(repository.currentProvenance)

    val state: StateFlow<TwinUiState> = combine(
        activeProvenance,
        repository.observeProfile(vehicleId),
        repository.observeAnomalies(vehicleId),
        repository.observeLiveAnomalies(vehicleId)
    ) { prov: DiagnosticProvenance,
        profile: DiagnosticValue<VehicleTwinProfileEntity?>,
        persisted: List<DiagnosticValue<TwinAnomalyEntity>>,
        live: List<DiagnosticValue<TwinAnomalyEntity>> ->
        TwinUiState(
            vehicleId = vehicleId,
            activeProvenance = prov,
            profile = profile,
            persistedAnomalies = persisted,
            liveAnomalies = live
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TwinUiState(
            vehicleId = vehicleId,
            activeProvenance = repository.currentProvenance
        )
    )

    init {
        // Keep activeProvenance in sync if something else flips the source.
        repository.activeSourceFlow
            .onEach { activeProvenance.value = it.provenance }
            .launchIn(viewModelScope)

        // Auto-train profile on first launch with a synthetic history so the user
        // sees a non-empty UI immediately. Real history comes from ObdSession later.
        viewModelScope.launch {
            val history = (1..30).map { t ->
                TwinDemoProfiles.perturbedFrame(
                    baseline = TwinDemoProfiles.healthyBaseline(),
                    t = t
                )
            }
            runCatching {
                repository.trainProfileFor(vehicleId, history)
            }
        }
    }

    fun useRealSource() {
        repository.useRealSource()
    }

    fun useSimulatedSource() {
        repository.useSimulatedSource()
    }

    /**
     * Inject one frame with optional anomaly. Useful for the in-app demo button
     * "Simular anomalía en voltage" / "Simular coolant spike".
     */
    fun injectDemoFrame(pid: String = "0142", anomalousValue: Float = 11.8f) {
        viewModelScope.launch {
            val base = TwinDemoProfiles.healthyBaseline()
            val frame = TwinDemoProfiles.perturbedFrame(
                baseline = base,
                t = 0,
                anomalySeed = pid to anomalousValue
            )
            repository.emitSimulatedFrame(vehicleId, frame)
            repository.ingestFrame(vehicleId, frame)
        }
    }

    /**
     * Inject a noisy-but-normal frame. Used to feed the engine during demos so
     * the user sees anomalies clear and new ones appear.
     */
    fun injectNormalFrame() {
        viewModelScope.launch {
            val t = Random.nextInt(0, 1000)
            val frame = TwinDemoProfiles.perturbedFrame(
                baseline = TwinDemoProfiles.healthyBaseline(),
                t = t
            )
            repository.emitSimulatedFrame(vehicleId, frame)
            repository.ingestFrame(vehicleId, frame)
        }
    }

    fun clearAnomalies() {
        viewModelScope.launch {
            repository.clearAnomalies(vehicleId)
        }
    }

    companion object {
        const val KEY_VEHICLE_ID = "vehicleId"
        const val DEFAULT_VEHICLE_ID = "demo-vehicle"
    }
}