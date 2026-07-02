package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Especificación de material para ForgeMaterialRepository.
 * Valores típicos de referencia educativa — NO usar para cálculo FEA real.
 */
@Serializable
data class MaterialSpec(
    val id: String,
    val displayName: String,
    val category: String,
    val densityKgM3: Double,
    val youngModulusGPa: Double,
    val yieldStrengthMPa: Double,
    val tensileStrengthMPa: Double,
    val thermalExpansion: Double,
    val thermalConductivity: Double,
    val maxOperatingTempC: Double,
    val corrosionResistance: String,
    val fatigueResistance: String,
    val manufacturability: String,
    val costLevel: Int,
    val compatibleProcesses: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Material id cannot be blank" }
        require(densityKgM3 > 0.0) { "density must be > 0" }
        require(youngModulusGPa > 0.0) { "youngModulus must be > 0" }
        require(yieldStrengthMPa >= 0.0) { "yieldStrength must be >= 0" }
        require(tensileStrengthMPa >= yieldStrengthMPa) { "tensile must be >= yield" }
        require(maxOperatingTempC.isFinite()) { "maxOperatingTempC must be finite" }
        require(costLevel in 1..5) { "costLevel must be in 1..5" }
    }
}

/**
 * Especificación de proceso de fabricación para ForgeManufacturingRepository.
 */
@Serializable
data class ManufacturingProcess(
    val id: String,
    val displayName: String,
    val category: String,
    val description: String,
    val compatibleMaterials: List<String> = emptyList(),
    val machines: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val commonDefects: List<String> = emptyList(),
    val qualityControls: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val costLevel: Int,
    val typicalPrecisionMm: Double,
    val notes: String = ""
) {
    init {
        require(id.isNotBlank()) { "Process id cannot be blank" }
        require(costLevel in 1..5) { "costLevel must be in 1..5" }
        require(typicalPrecisionMm.isFinite() && typicalPrecisionMm >= 0.0) {
            "typicalPrecisionMm must be non-negative finite"
        }
    }
}