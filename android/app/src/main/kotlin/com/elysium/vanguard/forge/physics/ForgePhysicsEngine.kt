package com.elysium.vanguard.forge.physics

import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.JointRuntimeState
import com.elysium.vanguard.forge.domain.JointTarget
import com.elysium.vanguard.forge.domain.PartRuntimeState
import com.elysium.vanguard.forge.domain.PhysicsStepResult
import com.elysium.vanguard.forge.domain.PhysicsWorldConfig
import com.elysium.vanguard.forge.domain.Vector3Data

/**
 * Abstracción del motor físico. V1: implementación educativa propia.
 * V2: adapter para Bullet/Jolt/PhysX-like via JNI/Rust si el proyecto lo permite.
 *
 * Reglas:
 * - stepSimulation acepta deltaTime clamped (no se permite step > maxStepSec).
 * - applyMotorTorque clampado a [0, maxTorqueNm].
 * - Nunca propaga NaN/Infinity al estado.
 */
interface ForgePhysicsEngine {

    fun initializeWorld(config: PhysicsWorldConfig)
    fun loadAssembly(assembly: ForgeAssembly)
    fun stepSimulation(deltaTimeSec: Double): PhysicsStepResult
    fun applyMotorTorque(instanceId: String, torqueNm: Double)
    fun applyForce(instanceId: String, force: Vector3Data)
    fun applyBrake(instanceId: String, brakeTorqueNm: Double)
    fun setJointTarget(jointId: String, target: JointTarget)
    fun injectDamage(instanceId: String, damage: DamageState)
    fun getPartRuntimeState(instanceId: String): PartRuntimeState?
    fun getJointRuntimeState(jointId: String): JointRuntimeState?
    fun dispose()

    companion object {
        const val MAX_STEP_SEC: Double = 1.0 / 30.0  // 33 ms — evita pasos inestables
        const val MAX_TORQUE_NM: Double = 5000.0     // tope duro para motor educativo
        const val MAX_FORCE_N: Double = 50000.0
    }
}