package com.elysium.vanguard.forge.physics

import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.DamageType
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.PowertrainDefinition
import com.elysium.vanguard.forge.domain.SafetyClassification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeEngineSimulatorTest {

    private fun physicsEngine(): ForgeEducationalPhysicsEngine = ForgeEducationalPhysicsEngine()

    private fun emptyEngineAsm(): ForgeAssembly {
        return ForgeAssembly(
            artifact = ForgeArtifact(
                id = "engine_asm", name = "Engine", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            )
        )
    }

    @Test
    fun `engine cannot start when crankshaft missing`() {
        val sim = ForgeEngineSimulator(physicsEngine())
        val powertrain = PowertrainDefinition(
            engineAssemblyId = "engine_asm",
            pistonInstanceIds = listOf("p1"),
            ignitionComponentIds = listOf("sp1"),
            fuelComponentIds = listOf("inj1")
        )
        val validation = sim.canStart(powertrain, emptyEngineAsm())
        assertFalse("Engine without crankshaft must not start", validation.canStart)
        assertTrue("Missing crankshaft must be in list", "cigüeñal" in validation.missingComponents)
    }

    @Test
    fun `engine cannot start when pistons missing`() {
        val sim = ForgeEngineSimulator(physicsEngine())
        val powertrain = PowertrainDefinition(
            engineAssemblyId = "engine_asm",
            crankshaftInstanceId = "crank",
            pistonInstanceIds = emptyList(),
            ignitionComponentIds = listOf("sp1"),
            fuelComponentIds = listOf("inj1")
        )
        val validation = sim.canStart(powertrain, emptyEngineAsm())
        assertFalse(validation.canStart)
    }

    @Test
    fun `valid simplified engine can start`() {
        val sim = ForgeEngineSimulator(physicsEngine())
        val asm = emptyEngineAsm().let {
            it.copy(
                instances = listOf(
                    com.elysium.vanguard.forge.domain.PartInstance("crank", "crank_simplified"),
                    com.elysium.vanguard.forge.domain.PartInstance("p1", "piston_generic"),
                    com.elysium.vanguard.forge.domain.PartInstance("sp1", "spark_plug_generic"),
                    com.elysium.vanguard.forge.domain.PartInstance("inj1", "injector_generic")
                )
            )
        }
        val powertrain = PowertrainDefinition(
            engineAssemblyId = "engine_asm",
            crankshaftInstanceId = "crank",
            pistonInstanceIds = listOf("p1"),
            ignitionComponentIds = listOf("sp1"),
            fuelComponentIds = listOf("inj1")
        )
        val validation = sim.canStart(powertrain, asm)
        assertTrue("Valid engine must be able to start", validation.canStart)
    }

    @Test
    fun `throttle increases rpm in running engine`() {
        val sim = ForgeEngineSimulator(physicsEngine())
        val asm = emptyEngineAsm().let {
            it.copy(
                instances = listOf(
                    com.elysium.vanguard.forge.domain.PartInstance("crank", "crank_simplified"),
                    com.elysium.vanguard.forge.domain.PartInstance("p1", "piston_generic"),
                    com.elysium.vanguard.forge.domain.PartInstance("sp1", "spark_plug_generic"),
                    com.elysium.vanguard.forge.domain.PartInstance("inj1", "injector_generic")
                )
            )
        }
        val powertrain = PowertrainDefinition(
            engineAssemblyId = "engine_asm",
            crankshaftInstanceId = "crank",
            pistonInstanceIds = listOf("p1"),
            ignitionComponentIds = listOf("sp1"),
            fuelComponentIds = listOf("inj1")
        )
        sim.startEngine(powertrain, asm)
        sim.engageStarter()
        // Avanzar hasta IDLE.
        repeat(200) { sim.stepEngine(1.0 / 60.0, asm) }
        val idleRpm = sim.stepEngine(1.0 / 60.0, asm).state.rpm
        sim.updateThrottle(0.8)
        repeat(120) { sim.stepEngine(1.0 / 60.0, asm) }
        val runningRpm = sim.stepEngine(1.0 / 60.0, asm).state.rpm
        assertTrue("Throttle should increase rpm (idle=$idleRpm, running=$runningRpm)", runningRpm >= idleRpm)
    }

    @Test
    fun `broken timing belt creates failure warning`() {
        val sim = ForgeEngineSimulator(physicsEngine())
        val asm = emptyEngineAsm().let {
            it.copy(
                instances = listOf(
                    com.elysium.vanguard.forge.domain.PartInstance("crank", "crank_simplified"),
                    com.elysium.vanguard.forge.domain.PartInstance("p1", "piston_generic"),
                    com.elysium.vanguard.forge.domain.PartInstance("sp1", "spark_plug_generic"),
                    com.elysium.vanguard.forge.domain.PartInstance("inj1", "injector_generic"),
                    com.elysium.vanguard.forge.domain.PartInstance(
                        "belt1", "timing_belt",
                        damageState = DamageState(
                            damageTypes = listOf(DamageType.BROKEN),
                            severity = DamageSeverity.HIGH
                        )
                    )
                )
            )
        }
        val state = com.elysium.vanguard.forge.domain.EngineRuntimeState(running = true, rpm = 2000.0)
        val failures = sim.detectRuntimeFailures(state, asm)
        assertTrue("Broken belt must be detected",
            failures.any { it.componentInstanceId == "belt1" || it.title.contains("Correa", ignoreCase = true) }
        )
    }

    @Test
    fun `damaged spark plug creates misfire warning`() {
        val sim = ForgeEngineSimulator(physicsEngine())
        val asm = emptyEngineAsm().let {
            it.copy(
                instances = listOf(
                    com.elysium.vanguard.forge.domain.PartInstance("crank", "crank_simplified"),
                    com.elysium.vanguard.forge.domain.PartInstance("p1", "piston_generic"),
                    com.elysium.vanguard.forge.domain.PartInstance(
                        "sp1", "spark_plug",
                        damageState = DamageState(
                            damageTypes = listOf(DamageType.ELECTRICAL_OPEN),
                            severity = DamageSeverity.MEDIUM
                        )
                    )
                )
            )
        }
        val state = com.elysium.vanguard.forge.domain.EngineRuntimeState(running = true, rpm = 2000.0)
        val failures = sim.detectRuntimeFailures(state, asm)
        assertTrue("Misfire must be detected", failures.any { it.title.contains("Misfare", ignoreCase = true) || it.title.contains("misfare", ignoreCase = true) })
    }

    @Test
    fun `overheating state is detected`() {
        val sim = ForgeEngineSimulator(physicsEngine())
        val asm = emptyEngineAsm()
        val state = com.elysium.vanguard.forge.domain.EngineRuntimeState(running = true, rpm = 2000.0, coolantTempC = 120.0)
        val failures = sim.detectRuntimeFailures(state, asm)
        assertTrue("Overheating must be detected", failures.any { it.title.contains("Sobrecalentamiento", ignoreCase = true) })
    }
}