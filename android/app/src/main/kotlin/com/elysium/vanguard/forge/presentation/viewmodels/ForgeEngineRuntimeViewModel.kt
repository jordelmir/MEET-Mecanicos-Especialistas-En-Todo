package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.PowertrainDefinition
import com.elysium.vanguard.forge.physics.ForgeEducationalPhysicsEngine
import com.elysium.vanguard.forge.physics.ForgeEngineSimulator
import com.elysium.vanguard.forge.domain.PhysicsWorldConfig
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgeEngineRuntimeViewModel(
    private val vehicleId: String? = null,
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository(),
    private val physics: ForgeEducationalPhysicsEngine = ForgeEducationalPhysicsEngine(),
    private val simulator: ForgeEngineSimulator = ForgeEngineSimulator(physics)
) : ViewModel() {

    private val _vehicle = MutableStateFlow<UiState<ForgeAssembly>>(UiState.Loading)
    val vehicle: StateFlow<UiState<ForgeAssembly>> = _vehicle.asStateFlow()

    private val _throttle = MutableStateFlow(0.0)
    val throttle: StateFlow<Double> = _throttle.asStateFlow()

    private val _rpm = MutableStateFlow(0.0)
    val rpm: StateFlow<Double> = _rpm.asStateFlow()

    private val _coolantC = MutableStateFlow(25.0)
    val coolantC: StateFlow<Double> = _coolantC.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _warnings = MutableStateFlow<List<String>>(emptyList())
    val warnings: StateFlow<List<String>> = _warnings.asStateFlow()

    private var simJob: Job? = null
    private var powertrain: PowertrainDefinition? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _vehicle.value = UiState.Loading
            val veh = vehicleId?.let { repository.getVehicle(it) }
            if (veh == null) {
                _vehicle.value = UiState.Empty
                return@launch
            }
            val engineAsmId = veh.powertrain?.engineAssemblyId
            val asm = engineAsmId?.let { repository.getAssembly(it) }
            if (asm == null) {
                _vehicle.value = UiState.Empty
                return@launch
            }
            _vehicle.value = UiState.Ready(asm)
            powertrain = veh.powertrain
            physics.initializeWorld(PhysicsWorldConfig())
            physics.loadAssembly(asm)
        }
    }

    fun ignitionOn() { simulator.updateThrottle(_throttle.value) }

    fun engageStarter() {
        val pt = powertrain ?: return
        val asm = (_vehicle.value as? UiState.Ready)?.data ?: return
        viewModelScope.launch {
            val validation = simulator.startEngine(pt, asm)
            simulator.engageStarter()
            _warnings.value = if (validation.canStart) emptyList() else listOf(validation.message)
            startLoop(asm)
        }
    }

    fun setThrottle(t: Double) {
        _throttle.value = t.coerceIn(0.0, 1.0)
        simulator.updateThrottle(_throttle.value)
    }

    fun stopEngine() {
        simulator.stopEngine()
        simJob?.cancel()
        _running.value = false
        _rpm.value = 0.0
        _coolantC.value = 25.0
        _warnings.value = emptyList()
    }

    private fun startLoop(asm: ForgeAssembly) {
        simJob?.cancel()
        _running.value = true
        simJob = viewModelScope.launch {
            val dt = 1.0 / 30.0
            while (_running.value) {
                val snapshot = simulator.stepEngine(dt, asm)
                _rpm.value = snapshot.state.rpm
                _coolantC.value = snapshot.state.coolantTempC
                _warnings.value = snapshot.warnings.takeLast(5)
                delay((dt * 1000).toLong())
            }
        }
    }

    override fun onCleared() {
        simJob?.cancel()
        physics.dispose()
    }
}