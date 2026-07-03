package com.elysium.vanguard.forge.presentation.state

import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgeVehicle
import com.elysium.vanguard.forge.domain.MaterialSpec
import com.elysium.vanguard.forge.domain.ManufacturingProcess

/**
 * Estados UiState del ForgeHomeViewModel y otras pantallas Forge.
 * Patrón Loading / Ready / Empty / Error con sealed classes.
 */
sealed class ForgeUiState {

    data class ForgeLibrary(
        val parts: List<ForgePart>,
        val assemblies: List<ForgeAssembly>,
        val vehicles: List<ForgeVehicle>,
        val materials: List<MaterialSpec>,
        val processes: List<ManufacturingProcess>,
        val partCount: Int = parts.size,
        val assemblyCount: Int = assemblies.size,
        val vehicleCount: Int = vehicles.size
    ) {
        val totalArtifacts: Int get() = partCount + assemblyCount + vehicleCount
        val isEmpty: Boolean get() = parts.isEmpty() && assemblies.isEmpty() && vehicles.isEmpty()
    }

    data object Loading : ForgeUiState()

    data class Ready(
    val library: ForgeLibrary,
    val bootstrapReport: ForgeArtifactRepository.BootstrapReport? = null
) : ForgeUiState()

    data object Empty : ForgeUiState()

    data class Error(val message: String, val recoverable: Boolean = true) : ForgeUiState()
}

/**
 * Tipos de artefactos seleccionables para navegación.
 */
sealed class ForgeDestination {
    data object Home : ForgeDestination()
    data object PartEditor : ForgeDestination()
    data class PartEditorWithId(val partId: String?) : ForgeDestination()
    data object AssemblyEditor : ForgeDestination()
    data class AssemblyEditorWithId(val assemblyId: String?) : ForgeDestination()
    data object VehicleBuilder : ForgeDestination()
    data class VehicleBuilderWithId(val vehicleId: String?) : ForgeDestination()
    data class Simulation(val assemblyId: String) : ForgeDestination()
    data class EngineRuntime(val vehicleId: String) : ForgeDestination()
    data class FailureLab(val assemblyId: String) : ForgeDestination()
    data class DiagnosticReport(val reportId: String) : ForgeDestination()
    data class Manual(val manualId: String) : ForgeDestination()
    data object Materials : ForgeDestination()
    data object Manufacturing : ForgeDestination()
    data object MyArtifacts : ForgeDestination()
    data object ForgeAbout : ForgeDestination()
}

/**
 * Cardinalidad agregada por tipo para badges.
 */
data class ForgeTypeCounters(
    val parts: Int = 0,
    val assemblies: Int = 0,
    val vehicles: Int = 0,
    val materials: Int = 0,
    val processes: Int = 0
) {
    val total: Int get() = parts + assemblies + vehicles
}

/**
 * Acciones del ForgeHomeViewModel que la pantalla dispara.
 */
sealed class ForgeHomeEvent {
    data object OnCreatePart : ForgeHomeEvent()
    data object OnCreateAssembly : ForgeHomeEvent()
    data object OnCreateVehicle : ForgeHomeEvent()
    data object OnOpenSimulation : ForgeHomeEvent()
    data object OnOpenEngineRuntime : ForgeHomeEvent()
    data object OnOpenFailureLab : ForgeHomeEvent()
    data object OnOpenDiagnostics : ForgeHomeEvent()
    data object OnOpenManuals : ForgeHomeEvent()
    data object OnOpenMaterials : ForgeHomeEvent()
    data object OnOpenManufacturing : ForgeHomeEvent()
    data object OnOpenMyArtifacts : ForgeHomeEvent()
    data class OnOpenArtifact(val artifactId: String, val type: ForgeArtifactType) : ForgeHomeEvent()
    data class OnSearch(val query: String) : ForgeHomeEvent()
    data object OnRefresh : ForgeHomeEvent()
}