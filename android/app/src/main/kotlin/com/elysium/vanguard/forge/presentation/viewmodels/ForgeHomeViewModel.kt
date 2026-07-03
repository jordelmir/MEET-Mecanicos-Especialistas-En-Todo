package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.presentation.state.ForgeHomeEvent
import com.elysium.vanguard.forge.presentation.state.ForgeUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ForgeHomeViewModel — coordina la biblioteca Forge (parts, assemblies, vehicles, materials, processes).
 *
 * Reglas:
 * - Inmutable UiState (sealed class).
 * - Sin I/O en main thread — collect sobre StateFlow del repo.
 * - Sin GlobalScope.
 * - Sin referencia fuerte a Context.
 */
class ForgeHomeViewModel(
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForgeUiState>(ForgeUiState.Loading)
    val uiState: StateFlow<ForgeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ForgeHomeEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        observeRepository()
    }

    private fun observeRepository() {
        // combine() tiene overload hasta 5 flows typed; con 6 usamos vararg.
        combine(
            repository.parts,
            repository.assemblies,
            repository.vehicles,
            repository.materials,
            repository.processes,
            repository.bootstrapReport
        ) { array ->
            @Suppress("UNCHECKED_CAST")
            val parts = array[0] as Map<String, com.elysium.vanguard.forge.domain.ForgePart>
            @Suppress("UNCHECKED_CAST")
            val assemblies = array[1] as Map<String, com.elysium.vanguard.forge.domain.ForgeAssembly>
            @Suppress("UNCHECKED_CAST")
            val vehicles = array[2] as Map<String, com.elysium.vanguard.forge.domain.ForgeVehicle>
            @Suppress("UNCHECKED_CAST")
            val materials = array[3] as Map<String, com.elysium.vanguard.forge.domain.MaterialSpec>
            @Suppress("UNCHECKED_CAST")
            val processes = array[4] as Map<String, com.elysium.vanguard.forge.domain.ManufacturingProcess>
            val bootstrapReport = array[5] as com.elysium.vanguard.forge.data.ForgeArtifactRepository.BootstrapReport?

            val library = ForgeUiState.ForgeLibrary(
                parts = parts.values.toList(),
                assemblies = assemblies.values.toList(),
                vehicles = vehicles.values.toList(),
                materials = materials.values.toList(),
                processes = processes.values.toList()
            )
            library to bootstrapReport
        }.onEach { (library, bootstrapReport) ->
            _uiState.value = if (library.isEmpty) {
                ForgeUiState.Empty
            } else {
                ForgeUiState.Ready(library, bootstrapReport)
            }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: ForgeHomeEvent) {
        when (event) {
            ForgeHomeEvent.OnCreatePart -> emit(event)
            ForgeHomeEvent.OnCreateAssembly -> emit(event)
            ForgeHomeEvent.OnCreateVehicle -> emit(event)
            ForgeHomeEvent.OnOpenSimulation -> emit(event)
            ForgeHomeEvent.OnOpenEngineRuntime -> emit(event)
            ForgeHomeEvent.OnOpenFailureLab -> emit(event)
            ForgeHomeEvent.OnOpenDiagnostics -> emit(event)
            ForgeHomeEvent.OnOpenManuals -> emit(event)
            ForgeHomeEvent.OnOpenMaterials -> emit(event)
            ForgeHomeEvent.OnOpenManufacturing -> emit(event)
            ForgeHomeEvent.OnOpenMyArtifacts -> emit(event)
            is ForgeHomeEvent.OnOpenArtifact -> emit(event)
            is ForgeHomeEvent.OnSearch -> {
                _searchQuery.value = event.query
            }
            ForgeHomeEvent.OnRefresh -> emit(event)
        }
    }

    private fun emit(event: ForgeHomeEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
}