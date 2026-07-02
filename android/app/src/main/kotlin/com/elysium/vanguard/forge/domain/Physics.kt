package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Estado runtime de una PartInstance alimentado por ForgePhysicsEngine.
 * No se serializa a JSON de usuario — es estado interno.
 */
@Serializable
data class PartRuntimeState(
    val instanceId: String,
    val transform: TransformData = TransformData(),
    val linearVelocity: Vector3Data = Vector3Data.ZERO,
    val angularVelocityDeg: Vector3Data = Vector3Data.ZERO,
    val damageState: DamageState = DamageState()
)

/**
 * Resultado de un step de simulación.
 */
@Serializable
data class PhysicsStepResult(
    val stepIndex: Long,
    val elapsedSec: Double,
    val partStates: Map<String, PartRuntimeState>,
    val jointViolations: List<String> = emptyList(),
    val collisions: List<InterferenceResult> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Estado de runtime del motor (UI-facing), con severity agregada y warnings.
 */
@Serializable
data class EngineRuntimeSnapshot(
    val state: EngineRuntimeState,
    val lifecycle: EngineLifecycleState,
    val warnings: List<String>,
    val detectedFailures: List<String>,
    val crankshaftAngleDeg: Double,
    val pistonPositionsMm: Map<String, Double>
) {
    init {
        require(crankshaftAngleDeg.isFinite()) { "crankshaftAngleDeg must be finite" }
    }
}

/**
 * Validación previa al encendido del motor.
 */
@Serializable
data class EngineStartValidation(
    val canStart: Boolean,
    val missingComponents: List<String>,
    val damagedComponents: List<String>,
    val message: String
) {
    companion object {
        fun ok() = EngineStartValidation(
            canStart = true,
            missingComponents = emptyList(),
            damagedComponents = emptyList(),
            message = "Componentes mínimos presentes. Motor listo para arrancar."
        )
        fun blocked(missing: List<String>, damaged: List<String> = emptyList()): EngineStartValidation {
            val reasons = mutableListOf<String>()
            if (missing.isNotEmpty()) reasons += "faltan: ${missing.joinToString(", ")}"
            if (damaged.isNotEmpty()) reasons += "dañados: ${damaged.joinToString(", ")}"
            val msg = if (reasons.isEmpty()) "No se puede encender."
            else "No se puede encender: ${reasons.joinToString("; ")}"
            return EngineStartValidation(false, missing, damaged, msg)
        }
    }
}

/**
 * Falla detectada durante el runtime del motor.
 */
@Serializable
data class FailureDetection(
    val id: String,
    val componentInstanceId: String,
    val componentName: String,
    val title: String,
    val severity: DamageSeverity,
    val warning: String,
    val affectedSystem: VehicleSystemType? = null
)

/**
 * Comando de movimiento que el motor emite hacia los joints del assembly cada step.
 */
@Serializable
data class JointMotionCommand(
    val jointId: String,
    val targetValue: Double,
    val velocity: Double = 0.0,
    val stopMotion: Boolean = false
)

/**
 * Estado físico de un joint específico (alimenta el renderer 3D).
 */
@Serializable
data class JointRuntimeState(
    val jointId: String,
    val currentValue: Double,
    val targetValue: Double,
    val isMoving: Boolean,
    val isLocked: Boolean,
    val violation: Boolean
)

/**
 * Configuración del mundo físico.
 */
@Serializable
data class PhysicsWorldConfig(
    val gravity: Vector3Data = Vector3Data(0.0, -9.81, 0.0),
    val globalFriction: Double = 0.3,
    val fixedStepSec: Double = 1.0 / 60.0,
    val enableCollisions: Boolean = true,
    val maxSubSteps: Int = 5
)

/**
 * Configuración de simulación runtime para el motor físico.
 */
@Serializable
data class SimulationModeConfig(
    val mode: SimulationMode,
    val playState: PlayState = PlayState.STOPPED,
    val speedMultiplier: Double = 1.0,
    val showForceOverlay: Boolean = false,
    val showTorqueOverlay: Boolean = false,
    val showDamageOverlay: Boolean = false,
    val showCollisionOverlay: Boolean = false
)

enum class SimulationMode { EDIT, ASSEMBLY, KINEMATIC, RUNTIME, FAILURE_INJECTION, REPAIR }
enum class PlayState { STOPPED, PLAYING, PAUSED }

/**
 * Reporte diagnóstico completo de una falla detectada.
 */
@Serializable
data class DiagnosticReport(
    val id: String,
    val affectedSystem: VehicleSystemType?,
    val affectedPartInstanceId: String?,
    val affectedPartName: String,
    val probableFailure: String,
    val confidence: Double,
    val severity: DamageSeverity,
    val observedSymptoms: List<String>,
    val likelyCauses: List<String>,
    val consequences: List<String>,
    val repairProcedure: RepairProcedure? = null,
    val replacementProcedure: ReplacementProcedure? = null,
    val toolsRequired: List<ToolRequirement> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val relatedDtcCodes: List<String> = emptyList(),
    val educationalExplanation: String = ""
) {
    init {
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "confidence must be in [0,1]"
        }
    }
}

@Serializable
data class PartDiagnosticReport(
    val instanceId: String,
    val partName: String,
    val severity: DamageSeverity,
    val damageTypes: List<DamageType>,
    val canRepair: Boolean,
    val mustReplace: Boolean,
    val repairProcedure: RepairProcedure? = null,
    val replacementProcedure: ReplacementProcedure? = null,
    val notes: String = ""
)

@Serializable
data class FailureModeMatch(
    val failureMode: FailureMode,
    val confidence: Double,
    val instanceId: String
)

@Serializable
data class RepairRecommendation(
    val action: RecommendedAction,
    val procedure: RepairProcedure? = null,
    val replacement: ReplacementProcedure? = null,
    val rationale: String,
    val requiresProfessional: Boolean
)

enum class RecommendedAction { REPAIR, REPLACE, INSPECT_ONLY, INSUFFICIENT_DATA }

/**
 * Overlay visual de daño (color/grietas/vibración) que el renderer debe aplicar.
 */
@Serializable
data class DamageVisualOverlay(
    val instanceId: String,
    val severity: DamageSeverity,
    val showCrack: Boolean,
    val showLeak: Boolean,
    val colorOverlay: Long, // ARGB
    val vibrateHz: Double = 0.0
)

/**
 * Efectos agregados del daño en un assembly.
 */
@Serializable
data class DamageEffects(
    val assemblyId: String,
    val totalTorqueLossPercent: Double,
    val additionalFriction: Double,
    val additionalVibration: Double,
    val temperatureRiseC: Double,
    val affectedInstanceIds: List<String>
)

/**
 * Reporte objetivo sobre los joints que faltan en un assembly.
 */
@Serializable
data class JointTarget(val value: Double, val velocity: Double = 0.0)