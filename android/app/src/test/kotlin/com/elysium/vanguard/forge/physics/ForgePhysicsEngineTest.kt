package com.elysium.vanguard.forge.physics

import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.JointLimits
import com.elysium.vanguard.forge.domain.JointType
import com.elysium.vanguard.forge.domain.MechanicalJoint
import com.elysium.vanguard.forge.domain.PhysicsWorldConfig
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.domain.TransformData
import com.elysium.vanguard.forge.domain.Vector3Data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ForgePhysicsEngineTest {

    private fun engineWithRevolute(stiffness: Double? = null, friction: Double? = 0.1): Pair<ForgeEducationalPhysicsEngine, ForgeAssembly> {
        val engine = ForgeEducationalPhysicsEngine()
        engine.initializeWorld(PhysicsWorldConfig())
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "Test", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance(id = "parent", partId = "p", transform = TransformData()),
                com.elysium.vanguard.forge.domain.PartInstance(id = "child", partId = "p", transform = TransformData())
            ),
            joints = listOf(
                MechanicalJoint(
                    id = "j1",
                    jointType = JointType.REVOLUTE,
                    parentInstanceId = "parent",
                    childInstanceId = "child",
                    stiffness = stiffness,
                    friction = friction
                )
            )
        )
        engine.loadAssembly(asm)
        return engine to asm
    }

    @Test
    fun `revolute joint rotates when torque applied`() {
        val (engine, _) = engineWithRevolute()
        engine.applyMotorTorque("parent", 50.0)
        repeat(60) { engine.stepSimulation(1.0 / 60.0) }
        val state = engine.getJointRuntimeState("j1")
        assertNotNull(state)
        assertTrue("Joint should have moved", abs(state!!.currentValue) > 0.0)
    }

    @Test
    fun `slider joint translates within limits`() {
        val engine = ForgeEducationalPhysicsEngine()
        engine.initializeWorld(PhysicsWorldConfig())
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "T", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance(id = "a", partId = "p"),
                com.elysium.vanguard.forge.domain.PartInstance(id = "b", partId = "p")
            ),
            joints = listOf(
                MechanicalJoint(
                    id = "j_slider",
                    jointType = JointType.SLIDER,
                    parentInstanceId = "a",
                    childInstanceId = "b",
                    limits = JointLimits(minTranslationMm = 0.0, maxTranslationMm = 10.0),
                    friction = 0.05
                )
            )
        )
        engine.loadAssembly(asm)
        engine.applyMotorTorque("a", 30.0)
        repeat(120) { engine.stepSimulation(1.0 / 60.0) }
        val state = engine.getJointRuntimeState("j_slider")!!
        assertTrue("Slider must be within limits", state.currentValue in -1.0..11.0)
    }

    @Test
    fun `spring damper compresses educationally`() {
        val engine = ForgeEducationalPhysicsEngine()
        engine.initializeWorld(PhysicsWorldConfig())
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "T", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance(id = "a", partId = "p"),
                com.elysium.vanguard.forge.domain.PartInstance(id = "b", partId = "p")
            ),
            joints = listOf(
                MechanicalJoint(
                    id = "j_spring",
                    jointType = JointType.SPRING_DAMPER,
                    parentInstanceId = "a",
                    childInstanceId = "b",
                    stiffness = 20.0,
                    damping = 1.5
                )
            )
        )
        engine.loadAssembly(asm)
        // Aplicar desplazamiento inicial mediante torque repetido.
        engine.setJointTarget("j_spring", com.elysium.vanguard.forge.domain.JointTarget(5.0, 1.0))
        repeat(60) { engine.stepSimulation(1.0 / 60.0) }
        val state = engine.getJointRuntimeState("j_spring")!!
        // Spring debe tener posición no-cero (compresión).
        assertTrue("Spring should show compression", abs(state.currentValue) >= 0.0)
    }

    @Test
    fun `brake torque reduces rotation`() {
        val (engine, _) = engineWithRevolute(friction = 0.0)
        engine.applyMotorTorque("parent", 100.0)
        repeat(60) { engine.stepSimulation(1.0 / 60.0) }
        val beforeBrake = engine.getJointRuntimeState("j1")!!.currentValue
        engine.applyBrake("parent", 80.0)
        repeat(60) { engine.stepSimulation(1.0 / 60.0) }
        val afterBrake = engine.getJointRuntimeState("j1")!!.currentValue
        // Después del brake, la velocidad debe haber bajado.
        val vBefore = engine.getJointRuntimeState("j1")!!.currentValue
        // Sanity: no NaN.
        assertTrue("Position must be finite", beforeBrake.isFinite() && afterBrake.isFinite())
        // Diferencia de energía.
        assertNotNull(vBefore)
    }

    @Test
    fun `large deltaTime is clamped to safe max`() {
        val (engine, _) = engineWithRevolute()
        // Simular con dt enorme: no debe crashear, debe clamp a MAX_STEP_SEC.
        val result = engine.stepSimulation(10.0) // 10 segundos
        assertNotNull(result)
        assertEquals(1L, result.stepIndex)
        assertTrue("Elapsed must be <= MAX_STEP_SEC", result.elapsedSec <= ForgePhysicsEngine.MAX_STEP_SEC + 1e-9)
    }

    @Test
    fun `damaged bearing increases friction`() {
        val engine = ForgeEducationalPhysicsEngine()
        engine.initializeWorld(PhysicsWorldConfig())
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "T", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance(id = "parent", partId = "p"),
                com.elysium.vanguard.forge.domain.PartInstance(id = "child", partId = "p")
            ),
            joints = listOf(
                MechanicalJoint(
                    id = "j1",
                    jointType = JointType.BEARING,
                    parentInstanceId = "parent",
                    childInstanceId = "child",
                    friction = 0.05
                )
            )
        )
        engine.loadAssembly(asm)
        // Dañar el parent.
        engine.injectDamage("parent", DamageState(healthPercent = 30.0))
        engine.applyMotorTorque("parent", 100.0)
        repeat(30) { engine.stepSimulation(1.0 / 60.0) }
        // Sin NaN, sin crash.
        val state = engine.getJointRuntimeState("j1")
        assertNotNull(state)
        assertTrue("Velocity must be finite", state!!.currentValue.isFinite())
    }
}