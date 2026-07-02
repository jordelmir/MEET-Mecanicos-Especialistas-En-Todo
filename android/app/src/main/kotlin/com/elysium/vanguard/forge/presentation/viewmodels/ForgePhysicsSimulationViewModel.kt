package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.physics.ForgeEducationalPhysicsEngine
import com.elysium.vanguard.forge.domain.PhysicsWorldConfig
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgePhysicsSimulationViewModel(
    private val assemblyId: String? = null,
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository(),
    private val physics: ForgeEducationalPhysicsEngine = ForgeEducationalPhysicsEngine()
) : ViewModel() {

    enum class SimulationState { STOPPED, RUNNING, PAUSED }

    private val _assembly = MutableStateFlow<UiState<ForgeAssembly>>(UiState.Loading)
    val assembly: StateFlow<UiState<ForgeAssembly>> = _assembly.asStateFlow()

    private val _simState = MutableStateFlow(SimulationState.STOPPED)
    val simState: StateFlow<SimulationState> = _simState.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1.0)
    val speedMultiplier: StateFlow<Double> = _speedMultiplier.asStateFlow()

    private val _gravityEnabled = MutableStateFlow(false)
    val gravityEnabled: StateFlow<Boolean> = _gravityEnabled.asStateFlow()

    private val _jointStates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val jointStates: StateFlow<Map<String, Double>> = _jointStates.asStateFlow()

    private var simJob: Job? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _assembly.value = UiState.Loading
            val asm = assemblyId?.let { repository.getAssembly(it) }
            if (asm == null) {
                _assembly.value = UiState.Empty
            } else {
                _assembly.value = UiState.Ready(asm)
                physics.initializeWorld(PhysicsWorldConfig())
                physics.loadAssembly(asm)
            }
        }
    }

    fun play() {
        if (_simState.value != SimulationState.STOPPED) return
        _simState.value = SimulationState.RUNNING
        simJob = viewModelScope.launch {
            val dt = 1.0 / 60.0
            while (_simState.value == SimulationState.RUNNING) {
                val result = physics.stepSimulation(dt * _speedMultiplier.value)
                _jointStates.value = result.partStates.mapValues { 0.0 } // placeholder; runtime state en jointStates
                delay((dt * 1000).toLong())
            }
        }
    }

    fun pause() {
        _simState.value = SimulationState.PAUSED
        simJob?.cancel()
    }

    fun stop() {
        _simState.value = SimulationState.STOPPED
        simJob?.cancel()
    }

    fun setSpeed(s: Double) { _speedMultiplier.value = s.coerceIn(0.1, 5.0) }
    fun setGravity(enabled: Boolean) { _gravityEnabled.value = enabled }

    override fun onCleared() {
        simJob?.cancel()
        physics.dispose()
    }
}