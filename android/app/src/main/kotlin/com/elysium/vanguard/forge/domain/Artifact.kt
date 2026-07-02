package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Cabecera común para todos los artefactos Forge (PART, ASSEMBLY, VEHICLE, ...).
 * Identifica, versiona y clasifica la pieza por seguridad.
 */
@Serializable
data class ForgeArtifact(
    val id: String,
    val schemaVersion: Int = 1,
    val name: String,
    val description: String = "",
    val artifactType: ForgeArtifactType,
    val unit: MeasurementUnit = MeasurementUnit.MM,
    val authorId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val version: Int = 1,
    val safetyClassification: SafetyClassification = SafetyClassification.EDUCATIONAL,
    val tags: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "ForgeArtifact.id cannot be blank" }
        require(name.isNotBlank()) { "ForgeArtifact.name cannot be blank" }
        require(schemaVersion >= 1) { "schemaVersion must be >= 1" }
        require(version >= 1) { "version must be >= 1" }
        require(createdAt >= 0L) { "createdAt must be >= 0" }
        require(updatedAt >= 0L) { "updatedAt must be >= 0" }
    }
}

@Serializable
enum class ForgeArtifactType {
    PART,
    ASSEMBLY,
    VEHICLE,
    MATERIAL,
    MANUFACTURING_PROCESS,
    MANUAL,
    SIMULATION_SCENARIO
}

@Serializable
enum class MeasurementUnit { MM, INCH }

/**
 * Clasificación de seguridad para piezas. Regla crítica: NO se debe publicar como
 * "seguro para carretera" una pieza crítica sin certificación.
 *
 * El renderer y los diagnósticos muestran el banner correspondiente.
 */
@Serializable
enum class SafetyClassification(val displayName: String, val isSafetyCritical: Boolean) {
    EDUCATIONAL("Educativo", false),
    DECORATIVE("Decorativo", false),
    PROTOTYPE("Prototipo no estructural", false),
    NON_STRUCTURAL("No estructural", false),
    STRUCTURAL_UNCERTIFIED("Estructural sin certificar", true),
    SAFETY_CRITICAL_UNCERTIFIED("Crítico de seguridad sin certificar", true),
    REQUIRES_PROFESSIONAL_VALIDATION("Requiere validación profesional", true),
    CERTIFIED_PARTNER_ONLY("Solo partner certificado", true);

    val requiresBanner: Boolean
        get() = isSafetyCritical
}