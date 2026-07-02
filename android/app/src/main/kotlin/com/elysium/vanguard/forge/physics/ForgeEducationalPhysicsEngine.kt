package com.elysium.vanguard.forge.physics

import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.InterferenceResult
import com.elysium.vanguard.forge.domain.JointRuntimeState
import com.elysium.vanguard.forge.domain.JointTarget
import com.elysium.vanguard.forge.domain.JointType
import com.elysium.vanguard.forge.domain.PartRuntimeState
import com.elysium.vanguard.forge.domain.PhysicsStepResult
import com.elysium.vanguard.forge.domain.PhysicsWorldConfig
import com.elysium.vanguard.forge.domain.TransformData
import com.elysium.vanguard.forge.domain.Vector3Data
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Implementación educativa determinística.
 *
 * Capacidades V1:
 * - REVOLUTE / HINGE / BEARING: rotación angular con velocidad angular, afectada por
 *   torque aplicado y fricción.
 * - SLIDER: traslación con velocidad lineal, afectada por límites.
 * - SPRING_DAMPER: fuerza restauradora = -k*x - c*v.
 * - FIXED / BOLTED / WELDED: ninguna cinemática; bloquea movimiento.
 * - Otros tipos: caemos a comportamiento "fijo" para no romper.
 *
 * NO implementa:
 * - Colisiones rígidas continuas
 * - Integración con gravedad no-uniforme
 * - Restricciones múltiples con prioridades (sólo la primera restricción del joint)
 *
 * El estado se mantiene internamente; stepSimulation actualiza y devuelve snapshot.
 */
class ForgeEducationalPhysicsEngine : ForgePhysicsEngine {

    private var config: PhysicsWorldConfig = PhysicsWorldConfig()
    private var stepIndex: Long = 0L
    private var elapsedSec: Double = 0.0

    private val partStates: MutableMap<String, PartRuntimeState> = HashMap()
    private val jointStates: MutableMap<String, JointRuntimeState> = HashMap()

    // Estado por joint: ángulo o traslación actual + velocidad.
    private data class JointKinematics(
        var position: Double = 0.0,
        var velocity: Double = 0.0,
        var targetValue: Double = 0.0,
        var isLocked: Boolean = false
    )

    private val jointKinematics: MutableMap<String, JointKinematics> = HashMap()
    private val motorTorques: MutableMap<String, Double> = HashMap()
    private val brakeTorques: MutableMap<String, Double> = HashMap()
    private val forces: MutableMap<String, Vector3Data> = HashMap()
    private var currentAssembly: ForgeAssembly? = null

    override fun initializeWorld(config: PhysicsWorldConfig) {
        this.config = config
        stepIndex = 0
        elapsedSec = 0.0
    }

    override fun loadAssembly(assembly: ForgeAssembly) {
        currentAssembly = assembly
        partStates.clear()
        jointStates.clear()
        jointKinematics.clear()
        for (instance in assembly.instances) {
            partStates[instance.id] = PartRuntimeState(
                instanceId = instance.id,
                transform = instance.transform,
                damageState = instance.damageState
            )
        }
        for (joint in assembly.joints) {
            jointKinematics[joint.id] = JointKinematics()
            jointStates[joint.id] = JointRuntimeState(
                jointId = joint.id,
                currentValue = 0.0,
                targetValue = 0.0,
                isMoving = false,
                isLocked = joint.jointType == JointType.FIXED || joint.jointType == JointType.BOLTED || joint.jointType == JointType.WELDED,
                violation = false
            )
        }
        // Las instancias conectadas a joint FIXED no se moverán; las demás inician en reposo.
    }

    override fun stepSimulation(deltaTimeSec: Double): PhysicsStepResult {
        // Clamp deltaTime.
        val dt = if (deltaTimeSec.isFinite() && deltaTimeSec > 0.0) {
            min(deltaTimeSec, ForgePhysicsEngine.MAX_STEP_SEC)
        } else {
            1.0 / 60.0
        }
        stepIndex++
        elapsedSec += dt

        val collisions = mutableListOf<InterferenceResult>()
        val jointViolations = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Aplicar paso por joint.
        for ((jointId, kin) in jointKinematics) {
            val joint = currentAssembly?.joints?.firstOrNull { it.id == jointId } ?: continue
            val damageMultiplier = damageEffectivenessMultiplier(joint.parentInstanceId, joint.childInstanceId)
            val newKin = stepJointKinematics(joint.jointType, joint, kin, dt, damageMultiplier)
            jointKinematics[jointId] = newKin
            val limitViolation = checkJointLimits(joint, newKin)
            if (limitViolation != null) {
                jointViolations += limitViolation
            }
            jointStates[jointId] = JointRuntimeState(
                jointId = jointId,
                currentValue = newKin.position,
                targetValue = newKin.targetValue,
                isMoving = abs(newKin.velocity) > 1e-6,
                isLocked = newKin.isLocked || limitViolation != null,
                violation = limitViolation != null
            )
        }

        // Aplicar movimiento propagado a las PartRuntimeStates.
        for ((jointId, kin) in jointKinematics) {
            val joint = currentAssembly?.joints?.firstOrNull { it.id == jointId } ?: continue
            applyJointMotionToParts(joint, kin)
        }

        return PhysicsStepResult(
            stepIndex = stepIndex,
            elapsedSec = elapsedSec,
            partStates = HashMap(partStates),
            jointViolations = jointViolations,
            collisions = collisions,
            warnings = warnings
        )
    }

    override fun applyMotorTorque(instanceId: String, torqueNm: Double) {
        val safe = clamp(torqueNm, 0.0, ForgePhysicsEngine.MAX_TORQUE_NM)
        motorTorques[instanceId] = safe
    }

    override fun applyForce(instanceId: String, force: Vector3Data) {
        if (!force.x.isFinite() || !force.y.isFinite() || !force.z.isFinite()) return
        val safe = Vector3Data(
            clamp(force.x, -ForgePhysicsEngine.MAX_FORCE_N, ForgePhysicsEngine.MAX_FORCE_N),
            clamp(force.y, -ForgePhysicsEngine.MAX_FORCE_N, ForgePhysicsEngine.MAX_FORCE_N),
            clamp(force.z, -ForgePhysicsEngine.MAX_FORCE_N, ForgePhysicsEngine.MAX_FORCE_N)
        )
        forces[instanceId] = safe
    }

    override fun applyBrake(instanceId: String, brakeTorqueNm: Double) {
        val safe = clamp(brakeTorqueNm, 0.0, ForgePhysicsEngine.MAX_TORQUE_NM)
        brakeTorques[instanceId] = safe
    }

    override fun setJointTarget(jointId: String, target: JointTarget) {
        val kin = jointKinematics[jointId] ?: return
        val safeValue = if (target.value.isFinite()) target.value else 0.0
        kin.targetValue = safeValue
        val safeVel = if (target.velocity.isFinite()) target.velocity else 0.0
        if (safeVel > 0.0) kin.velocity = safeVel
    }

    override fun injectDamage(instanceId: String, damage: DamageState) {
        val state = partStates[instanceId] ?: return
        partStates[instanceId] = state.copy(damageState = damage)
    }

    override fun getPartRuntimeState(instanceId: String): PartRuntimeState? = partStates[instanceId]

    override fun getJointRuntimeState(jointId: String): JointRuntimeState? = jointStates[jointId]

    override fun dispose() {
        partStates.clear()
        jointStates.clear()
        jointKinematics.clear()
        motorTorques.clear()
        brakeTorques.clear()
        forces.clear()
        currentAssembly = null
    }

    // --- Internos ---

    private fun stepJointKinematics(
        type: JointType,
        joint: com.elysium.vanguard.forge.domain.MechanicalJoint,
        kin: JointKinematics,
        dt: Double,
        damageMultiplier: Double
    ): JointKinematics {
        // Para tipos rígidos, nada se mueve.
        if (type == JointType.FIXED || type == JointType.BOLTED || type == JointType.WELDED) {
            kin.isLocked = true
            kin.velocity = 0.0
            kin.position = 0.0
            return kin
        }

        val parentTorque = motorTorques[joint.parentInstanceId] ?: 0.0
        val brakeTorque = brakeTorques[joint.parentInstanceId] ?: 0.0

        return when {
            type == JointType.REVOLUTE || type == JointType.HINGE ||
            type == JointType.BEARING || type == JointType.BUSHING ||
            type == JointType.UNIVERSAL_JOINT || type == JointType.CV_JOINT -> {
                // Ecuación simplificada: ω_new = ω + (T - brakeT - friction*ω) / I * dt
                val inertia = 1.0  // momento de inercia normalizado
                val friction = (joint.friction ?: 0.1) * damageMultiplier
                val net = parentTorque - brakeTorque
                val angularAcc = (net - friction * kin.velocity) / inertia
                kin.velocity = clampFinite(kin.velocity + angularAcc * dt, -10000.0, 10000.0)
                kin.position = kin.position + kin.velocity * dt
                // Aplicar damping del joint si está.
                joint.damping?.let { d -> kin.velocity *= (1.0 - d * dt).coerceIn(0.0, 1.0) }
                kin
            }
            type == JointType.SLIDER || type == JointType.CHAIN -> {
                val inertia = 1.0
                val friction = joint.friction ?: 0.1
                val netForce = parentTorque - brakeTorque // simplificado
                val accel = (netForce - friction * kin.velocity) / inertia
                kin.velocity = clampFinite(kin.velocity + accel * dt, -1000.0, 1000.0)
                kin.position = kin.position + kin.velocity * dt
                // Respect joint limits when present.
                val limits = joint.limits
                if (limits != null) {
                    val min = limits.minTranslationMm ?: Double.NEGATIVE_INFINITY
                    val max = limits.maxTranslationMm ?: Double.POSITIVE_INFINITY
                    kin.position = clampFinite(kin.position, min, max)
                }
                kin
            }
            type == JointType.SPRING_DAMPER -> {
                val k = joint.stiffness ?: 25.0
                val c = joint.damping ?: 1.0
                val force = -k * kin.position - c * kin.velocity
                val accel = force
                kin.velocity = clampFinite(kin.velocity + accel * dt, -1000.0, 1000.0)
                kin.position = kin.position + kin.velocity * dt
                kin
            }
            type == JointType.BELT -> {
                // BELT: pseudo-rotación inducida por el padre (cigüeñal).
                val parentVel = jointKinematics.entries.firstOrNull { it.key != joint.id }?.value?.velocity ?: 0.0
                kin.velocity = parentVel * 0.7
                kin.position = kin.position + kin.velocity * dt
                kin
            }
            else -> {
                // Otros tipos (GEAR, CHAIN, FLUID_LINE, ELECTRICAL_CONNECTOR): sin cinemática V1.
                kin
            }
        }
    }

    private fun damageEffectivenessMultiplier(parentId: String, childId: String): Double {
        val parentDamage = partStates[parentId]?.damageState ?: return 1.0
        val childDamage = partStates[childId]?.damageState ?: return 1.0
        val health = (parentDamage.healthPercent + childDamage.healthPercent) / 200.0
        return clampFinite(health.coerceIn(0.0, 1.0), 0.0, 1.0)
    }

    private fun checkJointLimits(
        joint: com.elysium.vanguard.forge.domain.MechanicalJoint,
        kin: JointKinematics
    ): String? {
        val limits = joint.limits ?: return null
        val isRotational = joint.jointType.isRotational()
        if (isRotational) {
            val minRad = (limits.minRotationDeg ?: Double.NEGATIVE_INFINITY) * Math.PI / 180.0
            val maxRad = (limits.maxRotationDeg ?: Double.POSITIVE_INFINITY) * Math.PI / 180.0
            if (kin.position < minRad || kin.position > maxRad) {
                return "Joint ${joint.id} exceeded rotation limits"
            }
        } else {
            val minMm = limits.minTranslationMm ?: Double.NEGATIVE_INFINITY
            val maxMm = limits.maxTranslationMm ?: Double.POSITIVE_INFINITY
            if (kin.position < minMm || kin.position > maxMm) {
                return "Joint ${joint.id} exceeded translation limits"
            }
        }
        return null
    }

    private fun applyJointMotionToParts(
        joint: com.elysium.vanguard.forge.domain.MechanicalJoint,
        kin: JointKinematics
    ) {
        val childState = partStates[joint.childInstanceId] ?: return
        val parentState = partStates[joint.parentInstanceId] ?: return

        val newRotation = when (joint.jointType) {
            JointType.REVOLUTE, JointType.HINGE, JointType.BEARING, JointType.UNIVERSAL_JOINT, JointType.CV_JOINT -> {
                childState.transform.copy(
                    rotationDeg = Vector3Data(
                        childState.transform.rotationDeg.x,
                        childState.transform.rotationDeg.y,
                        childState.transform.rotationDeg.z + kin.position * 180.0 / Math.PI
                    )
                )
            }
            JointType.SLIDER, JointType.CHAIN -> {
                childState.transform.copy(
                    position = childState.transform.position + Vector3Data(0.0, kin.position, 0.0)
                )
            }
            JointType.SPRING_DAMPER -> {
                childState.transform.copy(
                    position = childState.transform.position + Vector3Data(0.0, kin.position, 0.0)
                )
            }
            JointType.BELT -> {
                // Belt: la polea hija rota con la mitad de velocidad (gear ratio approx).
                childState.transform.copy(
                    rotationDeg = Vector3Data(
                        childState.transform.rotationDeg.x,
                        childState.transform.rotationDeg.y,
                        childState.transform.rotationDeg.z + kin.position * 0.5 * 180.0 / Math.PI
                    )
                )
            }
            else -> childState.transform
        }

        partStates[joint.childInstanceId] = childState.copy(transform = newRotation)
        partStates[joint.parentInstanceId] = parentState
    }

    private fun clampFinite(value: Double, min: Double, max: Double): Double {
        if (!value.isFinite()) return 0.0
        return value.coerceIn(min, max)
    }

    private fun clamp(value: Double, min: Double, max: Double): Double {
        if (!value.isFinite()) return 0.0
        return value.coerceIn(min, max)
    }
}