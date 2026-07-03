package com.elysium.vanguard.forge.presentation.navigation

import com.elysium.vanguard.forge.presentation.state.ForgeHomeEvent

/**
 * Mapea cada [ForgeHomeEvent] a la ruta del NavGraph. Devuelve null si el evento
 * no debe disparar navegación (ej. OnRefresh, OnSearch).
 *
 * Centralizado aquí para que sea trivial de testear sin instanciar el NavGraph.
 */
internal fun routeForEvent(event: ForgeHomeEvent): String? = when (event) {
    ForgeHomeEvent.OnCreatePart        -> "forge/part-editor"
    ForgeHomeEvent.OnCreateAssembly    -> "forge/assembly-editor"
    ForgeHomeEvent.OnCreateVehicle     -> "forge/vehicle-builder"
    ForgeHomeEvent.OnOpenSimulation    -> "forge/simulation?assemblyId="
    ForgeHomeEvent.OnOpenEngineRuntime -> "forge/engine-runtime?vehicleId="
    ForgeHomeEvent.OnOpenFailureLab    -> "forge/failure-lab?assemblyId="
    ForgeHomeEvent.OnOpenDiagnostics   -> "forge/diagnostic-report?reportId="
    ForgeHomeEvent.OnOpenManuals       -> "forge/manual?manualId="
    ForgeHomeEvent.OnOpenMaterials     -> "forge/materials"
    ForgeHomeEvent.OnOpenManufacturing  -> "forge/manufacturing"
    ForgeHomeEvent.OnOpenMyArtifacts   -> "forge/my-artifacts"
    is ForgeHomeEvent.OnOpenArtifact    -> "forge/part-editor?partId=${event.artifactId}"
    ForgeHomeEvent.OnRefresh,
    is ForgeHomeEvent.OnSearch          -> null
}
