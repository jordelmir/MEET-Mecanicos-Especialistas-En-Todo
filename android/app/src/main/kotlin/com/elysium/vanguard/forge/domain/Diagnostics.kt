package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Tipos de señales diagnósticas que ForgeDiagnosticEngine reconoce.
 */
enum class DiagnosticSignalType {
    RPM_VARIATION,
    TEMPERATURE_HIGH,
    PRESSURE_LOW,
    VIBRATION_HIGH,
    TORQUE_DROP,
    ELECTRICAL_FAULT,
    VISUAL_DAMAGE,
    LEAK_DETECTED,
    NOISE,
    DTC_CODE,
    USER_SYMPTOM
}

/**
 * Señal observada (o esperada) que alimenta el motor diagnóstico.
 */
@Serializable
data class DiagnosticSignal(
    val id: String,
    val signalType: DiagnosticSignalType,
    val name: String,
    val expectedRange: ValueRange? = null,
    val observedValue: Double? = null,
    val confidence: Double = 0.5
) {
    init {
        observedValue?.let { require(it.isFinite()) { "observedValue must be finite" } }
        require(confidence.isFinite() && confidence in 0.0..1.0) { "confidence must be in [0,1]" }
    }
}

/**
 * Modo de falla específico de una pieza: síntomas, causas, consecuencias, severidad.
 * Catálogo educativo — no usar para diagnóstico automotriz profesional real.
 */
@Serializable
data class FailureMode(
    val id: String,
    val partId: String,
    val title: String,
    val damageType: DamageType,
    val symptoms: List<String>,
    val causes: List<String>,
    val consequences: List<String>,
    val diagnosticSignals: List<DiagnosticSignal> = emptyList(),
    val severity: DamageSeverity = DamageSeverity.MEDIUM,
    val repairProcedureId: String? = null,
    val replacementProcedureId: String? = null,
    val relatedDtcCodes: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "FailureMode id cannot be blank" }
        require(partId.isNotBlank()) { "FailureMode partId cannot be blank" }
    }
}

enum class ProcedureDifficulty {
    EASY, MEDIUM, HARD, EXPERT_ONLY
}

@Serializable
data class ToolRequirement(
    val id: String,
    val name: String,
    val size: String? = null,
    val purpose: String,
    val safetyNote: String? = null
)

@Serializable
data class TorqueSpec(
    val fastenerName: String,
    val torqueNm: Double,
    val sequenceOrder: Int? = null,
    val note: String? = null
) {
    init {
        require(torqueNm.isFinite() && torqueNm >= 0.0) { "torqueNm must be non-negative finite" }
    }
}

@Serializable
data class ProcedureStep(
    val order: Int,
    val title: String,
    val description: String,
    val targetPartInstanceId: String? = null,
    val cameraFocus: Vector3Data? = null,
    val animationRef: String? = null,
    val requiredToolIds: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    init {
        require(order >= 1) { "step order must be >= 1" }
        require(title.isNotBlank()) { "step title cannot be blank" }
    }
}

/**
 * Procedimiento de reparación (no reemplazo) de una pieza.
 */
@Serializable
data class RepairProcedure(
    val id: String,
    val title: String,
    val partId: String,
    val difficulty: ProcedureDifficulty = ProcedureDifficulty.MEDIUM,
    val estimatedTimeMinutes: Int? = null,
    val requiredTools: List<ToolRequirement> = emptyList(),
    val requiredMaterials: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val steps: List<ProcedureStep> = emptyList(),
    val inspectionChecklist: List<String> = emptyList(),
    val torqueSpecs: List<TorqueSpec> = emptyList(),
    val finalValidationSteps: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "RepairProcedure id cannot be blank" }
        estimatedTimeMinutes?.let {
            require(it in 1..100_000) { "estimatedTimeMinutes out of range" }
        }
        // Regla crítica de seguridad: todo procedimiento debe tener al menos un safetyWarning.
        // (Validado en ForgeManualEngine.validateManualCompleteness, no aquí para permitir seeds.)
    }
}

/**
 * Procedimiento de reemplazo completo de una pieza.
 */
@Serializable
data class ReplacementProcedure(
    val id: String,
    val title: String,
    val partId: String,
    val difficulty: ProcedureDifficulty = ProcedureDifficulty.MEDIUM,
    val estimatedTimeMinutes: Int? = null,
    val requiredTools: List<ToolRequirement> = emptyList(),
    val requiredMaterials: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val steps: List<ProcedureStep> = emptyList(),
    val inspectionChecklist: List<String> = emptyList(),
    val torqueSpecs: List<TorqueSpec> = emptyList(),
    val finalValidationSteps: List<String> = emptyList()
)

/**
 * Tipos de manuales que ForgeManualEngine puede generar.
 */
enum class ForgeManualType {
    FABRICATION_MANUAL,
    ASSEMBLY_MANUAL,
    DIAGNOSTIC_MANUAL,
    REPAIR_MANUAL,
    REPLACEMENT_MANUAL,
    MAINTENANCE_MANUAL,
    INSPECTION_MANUAL
}

/**
 * Manual técnico: cabecera + lista de procedimientos + pasos 3D enlazados.
 */
@Serializable
data class ForgeManual(
    val id: String,
    val artifact: ForgeArtifact,
    val manualType: ForgeManualType,
    val scope: String = "",
    val tools: List<ToolRequirement> = emptyList(),
    val materials: List<String> = emptyList(),
    val torqueSpecs: List<TorqueSpec> = emptyList(),
    val steps: List<ProcedureStep> = emptyList(),
    val inspectionChecklist: List<String> = emptyList(),
    val commonMistakes: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val finalValidationSteps: List<String> = emptyList(),
    val relatedDtcCodes: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Manual id cannot be blank" }
        require(artifact.artifactType == ForgeArtifactType.MANUAL) {
            "ForgeManual.artifact must be of type MANUAL"
        }
    }
}