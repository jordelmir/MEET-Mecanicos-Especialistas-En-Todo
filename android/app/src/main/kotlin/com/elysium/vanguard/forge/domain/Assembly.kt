package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Tipos de joints mecánicos que Forge soporta.
 * Las implementaciones están en ForgePhysicsEngine.
 */
enum class JointType {
    FIXED,
    BOLTED,
    WELDED,
    CONCENTRIC,
    COINCIDENT,
    HINGE,
    REVOLUTE,
    SLIDER,
    BEARING,
    BUSHING,
    PIN,
    SPRING_DAMPER,
    GEAR,
    BELT,
    CHAIN,
    SHAFT_COUPLER,
    UNIVERSAL_JOINT,
    CV_JOINT,
    FLUID_LINE,
    ELECTRICAL_CONNECTOR;

    fun isRotational(): Boolean = this == HINGE || this == REVOLUTE || this == BEARING || this == UNIVERSAL_JOINT || this == CV_JOINT
    fun isTranslational(): Boolean = this == SLIDER || this == CHAIN
    fun isRigid(): Boolean = this == FIXED || this == BOLTED || this == WELDED || this == CONCENTRIC || this == COINCIDENT || this == SHAFT_COUPLER
    fun isCompliant(): Boolean = this == SPRING_DAMPER || this == BUSHING || this == BELT
}

/**
 * Tipos de restricciones numéricas aplicables a un joint axis.
 */
enum class ConstraintType {
    POSITION,
    ROTATION,
    DISTANCE,
    ANGLE,
    AXIS_ALIGNMENT,
    PLANAR,
    CONTACT,
    LIMIT
}

/**
 * Restricción concreta (axis + valor target + rango opcional).
 */
@Serializable
data class JointConstraint(
    val axis: Vector3Data? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val targetValue: Double? = null,
    val constraintType: ConstraintType
) {
    init {
        minValue?.let { require(it.isFinite()) { "minValue must be finite" } }
        maxValue?.let { require(it.isFinite()) { "maxValue must be finite" } }
        targetValue?.let { require(it.isFinite()) { "targetValue must be finite" } }
        if (minValue != null && maxValue != null) require(minValue <= maxValue) {
            "minValue must be <= maxValue"
        }
    }
}

/**
 * Límites físicos del joint: rango de traslación, rotación, torque, carga.
 */
@Serializable
data class JointLimits(
    val minTranslationMm: Double? = null,
    val maxTranslationMm: Double? = null,
    val minRotationDeg: Double? = null,
    val maxRotationDeg: Double? = null,
    val maxTorqueNm: Double? = null,
    val maxLoadN: Double? = null
)

/**
 * Umbral de falla física: torque/load máximo antes de que el joint ceda.
 */
@Serializable
data class FailureThreshold(
    val maxTorqueNm: Double? = null,
    val maxLoadN: Double? = null,
    val maxCycles: Long? = null,
    val degradationRate: Double = 0.0
)

/**
 * Joint mecánico entre dos instancias de piezas.
 */
@Serializable
data class MechanicalJoint(
    val id: String,
    val name: String = "",
    val jointType: JointType,
    val parentInstanceId: String,
    val childInstanceId: String,
    val parentPortId: String? = null,
    val childPortId: String? = null,
    val constraints: List<JointConstraint> = emptyList(),
    val limits: JointLimits? = null,
    val stiffness: Double? = null,
    val damping: Double? = null,
    val friction: Double? = null,
    val failureThreshold: FailureThreshold? = null
) {
    init {
        require(id.isNotBlank()) { "Joint id cannot be blank" }
        require(parentInstanceId != childInstanceId) {
            "Joint parent and child must be different instances"
        }
        stiffness?.let { require(it.isFinite() && it >= 0.0) { "stiffness must be non-negative finite" } }
        damping?.let { require(it.isFinite() && it >= 0.0) { "damping must be non-negative finite" } }
        friction?.let { require(it.isFinite() && it >= 0.0) { "friction must be non-negative finite" } }
    }
}

/**
 * Estado de daño de una PartInstance en runtime.
 * Actualizado por ForgeDamageEngine.
 */
@Serializable
data class DamageState(
    val healthPercent: Double = 100.0,
    val damageTypes: List<DamageType> = emptyList(),
    val severity: DamageSeverity = DamageSeverity.NONE,
    val isFailed: Boolean = false,
    val notes: String? = null
) {
    init {
        require(healthPercent.isFinite()) { "healthPercent must be finite" }
        require(healthPercent in 0.0..100.0) { "healthPercent must be in [0, 100]" }
    }
}

enum class DamageType {
    NONE,
    WEAR,
    CRACK,
    BENT,
    BROKEN,
    LEAK,
    CORROSION,
    OVERHEATED,
    SEIZED,
    LOOSE,
    MISALIGNED,
    ELECTRICAL_OPEN,
    ELECTRICAL_SHORT,
    CLOGGED,
    LOW_PRESSURE,
    HIGH_FRICTION,
    FATIGUE,
    CONTAMINATED;

    fun isCritical(): Boolean = this == BROKEN || this == SEIZED || this == ELECTRICAL_SHORT
}

enum class DamageSeverity { NONE, LOW, MEDIUM, HIGH, CRITICAL;
    // compareTo viene de Enum (compara por ordinal) — comportamiento correcto para DamageSeverity.
}

/**
 * Una instancia de pieza dentro de un ensamblaje: partId + transform + damageState runtime.
 */
@Serializable
data class PartInstance(
    val id: String,
    val partId: String,
    val transform: TransformData = TransformData(),
    val damageState: DamageState = DamageState(),
    val visible: Boolean = true,
    val locked: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "PartInstance id cannot be blank" }
        require(partId.isNotBlank()) { "PartInstance partId cannot be blank" }
    }
}

/**
 * Nodo de árbol jerárquico de ensamblaje. Permite sub-ensamblajes.
 */
@Serializable
data class AssemblyNode(
    val id: String,
    val name: String,
    val partInstanceId: String? = null,
    val children: List<AssemblyNode> = emptyList()
)

/**
 * Perfil de simulación asociado al ensamblaje (gravedad, fricción global, paso fijo, etc.).
 */
@Serializable
data class SimulationProfile(
    val gravity: Vector3Data = Vector3Data(0.0, -9.81, 0.0),
    val globalFriction: Double = 0.3,
    val fixedStepSec: Double = 1.0 / 60.0,
    val enableCollisions: Boolean = true,
    val enableGravity: Boolean = false
) {
    init {
        // Range validation is enforced by ForgeValidationEngine / physics engine
        // at simulation time. The data class is a transport object that can
        // carry any user-supplied value; the validator is the single source
        // of truth for what is valid.
    }
}

/**
 * Ensamblaje completo: cabecera + instances + joints + árbol + profile.
 */
@Serializable
data class ForgeAssembly(
    val artifact: ForgeArtifact,
    val instances: List<PartInstance> = emptyList(),
    val joints: List<MechanicalJoint> = emptyList(),
    val assemblyTree: AssemblyNode = AssemblyNode(id = "root", name = "Root"),
    val simulationProfile: SimulationProfile? = null,
    val manualIds: List<String> = emptyList()
) {
    init {
        require(artifact.artifactType == ForgeArtifactType.ASSEMBLY) {
            "ForgeAssembly.artifact must be of type ASSEMBLY"
        }
        val ids = instances.map { it.id }
        require(ids.toSet().size == ids.size) { "Instance ids must be unique" }
        joints.forEach { j ->
            require(ids.contains(j.parentInstanceId)) {
                "Joint ${j.id} references missing parentInstanceId ${j.parentInstanceId}"
            }
            require(ids.contains(j.childInstanceId)) {
                "Joint ${j.id} references missing childInstanceId ${j.childInstanceId}"
            }
        }
    }
}