package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.forge.assembly.ForgeAssemblyEngine
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.domain.CompletenessResult
import com.elysium.vanguard.forge.domain.ForgeVehicle
import com.elysium.vanguard.forge.domain.VehicleSystemNode
import com.elysium.vanguard.forge.domain.VehicleSystemType
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgeVehicleBuilderViewModel(
    private val vehicleId: String? = null,
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository(),
    private val engine: ForgeAssemblyEngine = ForgeAssemblyEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ForgeVehicle>>(UiState.Loading)
    val uiState: StateFlow<UiState<ForgeVehicle>> = _uiState.asStateFlow()

    private val _completeness = MutableStateFlow<CompletenessResult?>(null)
    val completeness: StateFlow<CompletenessResult?> = _completeness.asStateFlow()

    private var current: ForgeVehicle? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val v = vehicleId?.let { repository.getVehicle(it) } ?: createBlank()
            current = v
            _uiState.value = if (v != null) UiState.Ready(v) else UiState.Empty
            recomputeCompleteness()
        }
    }

    fun onEvent(event: ForgeVehicleBuilderEvent) {
        when (event) {
            is ForgeVehicleBuilderEvent.OnRename -> rename(event.name)
            is ForgeVehicleBuilderEvent.OnToggleSystem -> toggleSystem(event.systemType, event.assemblyId)
            ForgeVehicleBuilderEvent.OnSave -> save()
        }
    }

    private fun rename(name: String) {
        val c = current ?: return
        val updated = c.copy(
            artifact = c.artifact.copy(name = name, updatedAt = System.currentTimeMillis())
        )
        current = updated
        _uiState.value = UiState.Ready(updated)
    }

    private fun toggleSystem(systemType: VehicleSystemType, assemblyId: String?) {
        val c = current ?: return
        val existing = c.systems.firstOrNull { it.systemType == systemType }
        val newSystems = if (existing != null) {
            c.systems.filterNot { it.systemType == systemType }
        } else {
            c.systems + VehicleSystemNode(
                id = "sys_${systemType.name}",
                systemType = systemType,
                assemblyId = assemblyId ?: "asm_${systemType.name.lowercase()}",
                name = systemType.name,
                isComplete = assemblyId != null
            )
        }
        val updated = c.copy(systems = newSystems)
        current = updated
        _uiState.value = UiState.Ready(updated)
        recomputeCompleteness()
    }

    private fun recomputeCompleteness() {
        val c = current ?: return
        viewModelScope.launch {
            _completeness.value = engine.computeAssemblyCompleteness(c)
        }
    }

    private fun save() {
        val c = current ?: return
        viewModelScope.launch { repository.saveVehicle(c) }
    }

    private fun createBlank(): ForgeVehicle = ForgeVehicle(
        artifact = com.elysium.vanguard.forge.domain.ForgeArtifact(
            id = "veh_${System.currentTimeMillis()}",
            name = "Vehículo genérico",
            artifactType = com.elysium.vanguard.forge.domain.ForgeArtifactType.VEHICLE
        ),
        rootAssemblyId = "root"
    )
}

sealed class ForgeVehicleBuilderEvent {
    data class OnRename(val name: String) : ForgeVehicleBuilderEvent()
    data class OnToggleSystem(
        val systemType: VehicleSystemType,
        val assemblyId: String?
    ) : ForgeVehicleBuilderEvent()
    data object OnSave : ForgeVehicleBuilderEvent()
}