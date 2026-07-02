package com.elysium.vanguard.forge.data

import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ForgeVehicle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Documento Forge versionado. Wrapper de serialización que cubre los 7 tipos de artefactos.
 * Regla crítica: el JSON guarda intención paramétrica, NO malla.
 * La malla es derivada (ForgeGeometryCompiler).
 */
@Serializable
sealed class ForgeArtifactDocument {
    abstract val schemaVersion: Int
    abstract val id: String

    @Serializable
    @SerialName("PART")
    data class PartDocument(
        override val schemaVersion: Int = 1,
        override val id: String,
        val part: ForgePart
    ) : ForgeArtifactDocument()

    @Serializable
    @SerialName("ASSEMBLY")
    data class AssemblyDocument(
        override val schemaVersion: Int = 1,
        override val id: String,
        val assembly: ForgeAssembly
    ) : ForgeArtifactDocument()

    @Serializable
    @SerialName("VEHICLE")
    data class VehicleDocument(
        override val schemaVersion: Int = 1,
        override val id: String,
        val vehicle: ForgeVehicle
    ) : ForgeArtifactDocument()

    @Serializable
    @SerialName("MATERIAL")
    data class MaterialDocument(
        override val schemaVersion: Int = 1,
        override val id: String,
        val material: com.elysium.vanguard.forge.domain.MaterialSpec
    ) : ForgeArtifactDocument()

    @Serializable
    @SerialName("MANUFACTURING_PROCESS")
    data class ProcessDocument(
        override val schemaVersion: Int = 1,
        override val id: String,
        val process: com.elysium.vanguard.forge.domain.ManufacturingProcess
    ) : ForgeArtifactDocument()

    @Serializable
    @SerialName("MANUAL")
    data class ManualDocument(
        override val schemaVersion: Int = 1,
        override val id: String,
        val manual: com.elysium.vanguard.forge.domain.ForgeManual
    ) : ForgeArtifactDocument()

    @Serializable
    @SerialName("SIMULATION_SCENARIO")
    data class ScenarioDocument(
        override val schemaVersion: Int = 1,
        override val id: String,
        val name: String,
        val description: String = "",
        val failureModeIds: List<String> = emptyList()
    ) : ForgeArtifactDocument()
}

/**
 * Listado de artefactos contenido en un seed JSON.
 * Permite cargar varios documentos a la vez desde un único archivo.
 */
@Serializable
data class ForgeArtifactBundle(
    val bundleId: String,
    val schemaVersion: Int = 1,
    val documents: List<ForgeArtifactDocument>
)