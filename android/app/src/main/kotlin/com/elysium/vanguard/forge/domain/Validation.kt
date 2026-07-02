package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Tipos de errores de validación.
 * Cadena textual estable — se usa como identificador en mensajes UI.
 */
enum class ForgeValidationError {
    DIMENSION_MISSING,
    DIMENSION_INVALID,
    MATERIAL_MISSING,
    PROCESS_MISSING,
    CONNECTION_PORT_MISSING,
    JOINT_INCOMPATIBLE,
    FLOATING_PART,
    INTERFERENCE_DETECTED,
    SAFETY_CLASSIFICATION_MISSING,
    MANUAL_MISSING,
    REPAIR_PROCEDURE_MISSING,
    DAMAGE_UNRESOLVED,
    PHYSICS_UNSTABLE,
    CYCLE_IN_ASSEMBLY_GRAPH,
    UNSUPPORTED_FEATURE,
    OEM_DATA_NOT_LICENSED
}

enum class ForgeValidationWarning {
    EDUCATIONAL_ONLY,
    LOW_CONFIDENCE_DIAGNOSIS,
    GENERIC_DATA,
    REQUIRES_PROFESSIONAL_VALIDATION,
    HIGH_COMPLEXITY_ARTIFACT,
    PERFORMANCE_RISK
}

/**
 * Item de validación: error o warning contextualizado.
 */
@Serializable
data class ForgeValidationIssue(
    val code: ForgeValidationError?,
    val warning: ForgeValidationWarning?,
    val message: String,
    val relatedInstanceId: String? = null,
    val relatedJointId: String? = null,
    val relatedFeatureId: String? = null
) {
    init {
        require((code != null) xor (warning != null)) {
            "Exactly one of code or warning must be non-null"
        }
    }
}

@Serializable
data class ForgeValidationResult(
    val isValid: Boolean,
    val issues: List<ForgeValidationIssue> = emptyList()
) {
    val errors: List<ForgeValidationIssue> get() = issues.filter { it.code != null }
    val warnings: List<ForgeValidationIssue> get() = issues.filter { it.warning != null }

    companion object {
        val OK = ForgeValidationResult(true)

        fun from(issues: List<ForgeValidationIssue>): ForgeValidationResult =
            ForgeValidationResult(isValid = issues.none { it.code != null }, issues = issues)
    }
}

/**
 * Resultado de validación de ensamblaje (más rico que ForgeValidationResult).
 */
@Serializable
data class AssemblyValidationResult(
    val isValid: Boolean,
    val floatingInstanceIds: List<String> = emptyList(),
    val interferencePairs: List<InterferenceResult> = emptyList(),
    val incompatibleJoints: List<String> = emptyList(),
    val criticalMissingManuals: List<String> = emptyList(),
    val issues: List<ForgeValidationIssue> = emptyList()
)

@Serializable
data class InterferenceResult(
    val instanceIdA: String,
    val instanceIdB: String,
    val penetrationMm: Double,
    val contactNormal: Vector3Data
) {
    init {
        require(penetrationMm.isFinite() && penetrationMm >= 0.0) {
            "penetrationMm must be non-negative finite"
        }
    }
}

/**
 * Resultado de completeness de vehículo.
 */
@Serializable
data class CompletenessResult(
    val vehicleId: String,
    val overallPercent: Double,
    val missingSystems: List<VehicleSystemType>,
    val invalidSystems: List<VehicleSystemType>,
    val readyToSimulate: Boolean
)

/**
 * Vista explotada.
 */
@Serializable
data class ExplodedViewResult(
    val instanceIdToOffset: Map<String, Vector3Data>,
    val axis: Vector3Data = Vector3Data.UNIT_Y
)