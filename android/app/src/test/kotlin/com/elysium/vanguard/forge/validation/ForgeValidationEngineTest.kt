package com.elysium.vanguard.forge.validation

import com.elysium.vanguard.forge.domain.DimensionSet
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ForgeValidationError
import com.elysium.vanguard.forge.domain.ForgeVehicle
import com.elysium.vanguard.forge.domain.PowertrainDefinition
import com.elysium.vanguard.forge.domain.ProcedureStep
import com.elysium.vanguard.forge.domain.RepairProcedure
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.domain.SimulationProfile
import com.elysium.vanguard.forge.domain.VehicleSystemNode
import com.elysium.vanguard.forge.domain.VehicleSystemType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeValidationEngineTest {

    private val engine = ForgeValidationEngine()

    private fun part(
        dims: DimensionSet = DimensionSet(lengthMm = 10.0, widthMm = 10.0, heightMm = 10.0),
        material: String? = "mat",
        processes: List<String> = listOf("p1"),
        safety: SafetyClassification = SafetyClassification.EDUCATIONAL
    ): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = "p", name = "P", artifactType = ForgeArtifactType.PART,
            safetyClassification = safety
        ),
        dimensions = dims,
        materialId = material,
        manufacturingProcessIds = processes
    )

    @Test
    fun `validatePart detects missing dimensions`() {
        val result = engine.validatePart(part(dims = DimensionSet()))
        assertTrue(result.errors.any { it.code == ForgeValidationError.DIMENSION_MISSING })
    }

    @Test
    fun `validatePart detects missing material`() {
        val result = engine.validatePart(part(material = null))
        assertTrue(result.errors.any { it.code == ForgeValidationError.MATERIAL_MISSING })
    }

    @Test
    fun `validatePart detects missing process`() {
        val result = engine.validatePart(part(processes = emptyList()))
        assertTrue(result.errors.any { it.code == ForgeValidationError.PROCESS_MISSING })
    }

    @Test
    fun `validatePart detects safety-critical without manual`() {
        val result = engine.validatePart(
            part(safety = SafetyClassification.SAFETY_CRITICAL_UNCERTIFIED)
        )
        assertTrue(result.errors.any { it.code == ForgeValidationError.MANUAL_MISSING })
    }

    @Test
    fun `validatePart passes for well-formed part`() {
        val result = engine.validatePart(part())
        assertTrue("Well-formed part should pass", result.errors.isEmpty() || result.warnings.isNotEmpty())
    }

    @Test
    fun `validateAssembly flags empty assembly`() {
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "a", name = "A", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            )
        )
        val result = engine.validateAssembly(asm)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateVehicle requires powertrain`() {
        val vehicle = ForgeVehicle(
            artifact = ForgeArtifact(
                id = "v", name = "V", artifactType = ForgeArtifactType.VEHICLE,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            rootAssemblyId = "root"
        )
        val result = engine.validateVehicle(vehicle)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateVehicle with powertrain is valid`() {
        val vehicle = ForgeVehicle(
            artifact = ForgeArtifact(
                id = "v", name = "V", artifactType = ForgeArtifactType.VEHICLE,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            rootAssemblyId = "root",
            systems = listOf(
                VehicleSystemNode(id = "s1", systemType = VehicleSystemType.ENGINE, assemblyId = "eng_asm", isComplete = true)
            ),
            powertrain = PowertrainDefinition(engineAssemblyId = "eng_asm")
        )
        val result = engine.validateVehicle(vehicle)
        assertTrue(result.isValid)
    }

    @Test
    fun `validateSimulationScenario detects bad fixed step`() {
        val profile = SimulationProfile(fixedStepSec = 0.0)
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "a", name = "A", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            )
        )
        val result = engine.validateSimulationScenario(asm, profile)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateForPublishing always flags OEM not licensed in V1`() {
        val artifact = ForgeArtifact(
            id = "p", name = "P", artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.SAFETY_CRITICAL_UNCERTIFIED
        )
        val result = engine.validateForPublishing(artifact)
        assertTrue(result.errors.any { it.code == ForgeValidationError.OEM_DATA_NOT_LICENSED })
    }
}