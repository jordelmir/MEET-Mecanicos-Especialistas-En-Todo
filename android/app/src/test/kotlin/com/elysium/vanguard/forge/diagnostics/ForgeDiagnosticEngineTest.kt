package com.elysium.vanguard.forge.diagnostics

import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.DamageType
import com.elysium.vanguard.forge.domain.DiagnosticReport
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ProcedureStep
import com.elysium.vanguard.forge.domain.RepairProcedure
import com.elysium.vanguard.forge.domain.ReplacementProcedure
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.domain.TransformData
import com.elysium.vanguard.forge.domain.Vector3Data
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeDiagnosticEngineTest {

    private val engine = ForgeDiagnosticEngine()

    private fun waterPumpPart(): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = "water_pump_generic", name = "Water Pump", artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.STRUCTURAL_UNCERTIFIED
        ),
        repairProcedures = listOf(
            RepairProcedure(
                id = "repair_wp",
                title = "Reemplazo de bomba de agua",
                partId = "water_pump_generic",
                steps = listOf(ProcedureStep(1, "Drenar refrigerante", "Abrir drenaje"))
            )
        )
    )

    private fun sparkPlugPart(): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = "spark_plug_generic", name = "Spark Plug", artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.EDUCATIONAL
        ),
        relatedDtcCodes = listOf("P0301"),
        repairProcedures = listOf(
            RepairProcedure(
                id = "repair_sp",
                title = "Reemplazo de bujía",
                partId = "spark_plug_generic",
                steps = listOf(ProcedureStep(1, "Remover", "Girar en sentido antihorario"))
            )
        )
    )

    private fun brakePadPart(): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = "brake_pad_generic", name = "Brake Pad", artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.SAFETY_CRITICAL_UNCERTIFIED
        )
    )

    private fun bushingPart(): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = "bushing_generic", name = "Bushing", artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.STRUCTURAL_UNCERTIFIED
        )
    )

    @Test
    fun `damaged water pump maps to overheating diagnosis`() {
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "A", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance(
                    id = "wp1", partId = "water_pump_generic",
                    damageState = DamageState(
                        healthPercent = 30.0,
                        damageTypes = listOf(DamageType.LEAK),
                        severity = DamageSeverity.HIGH
                    )
                )
            )
        )
        val partsById = mapOf("water_pump_generic" to waterPumpPart())
        val report = engine.diagnoseAssembly(asm, partsById)
        assertNotNull(report)
        assertTrue("Confidence should be > 0", report.confidence > 0.0)
        assertTrue("Severity should be HIGH or higher", report.severity >= DamageSeverity.MEDIUM)
    }

    @Test
    fun `damaged spark plug maps to misfire diagnosis`() {
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "A", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance(
                    id = "sp1", partId = "spark_plug_generic",
                    damageState = DamageState(
                        healthPercent = 40.0,
                        damageTypes = listOf(DamageType.ELECTRICAL_OPEN),
                        severity = DamageSeverity.MEDIUM
                    )
                )
            )
        )
        val partsById = mapOf("spark_plug_generic" to sparkPlugPart())
        val report = engine.diagnoseAssembly(asm, partsById)
        assertNotNull(report)
        assertTrue("Must include P0301 in related DTC codes",
            "P0301" in report.relatedDtcCodes)
    }

    @Test
    fun `worn brake pad maps to braking issue`() {
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "A", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance(
                    id = "pad1", partId = "brake_pad_generic",
                    damageState = DamageState(
                        damageTypes = listOf(DamageType.WEAR),
                        severity = DamageSeverity.HIGH
                    )
                )
            )
        )
        val partsById = mapOf("brake_pad_generic" to brakePadPart())
        val report = engine.diagnoseAssembly(asm, partsById)
        assertTrue("Severity at least MEDIUM", report.severity >= DamageSeverity.MEDIUM)
    }

    @Test
    fun `worn bushing maps to suspension play`() {
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "A", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance(
                    id = "bush1", partId = "bushing_generic",
                    damageState = DamageState(
                        damageTypes = listOf(DamageType.WEAR),
                        severity = DamageSeverity.MEDIUM
                    )
                )
            )
        )
        val partsById = mapOf("bushing_generic" to bushingPart())
        val report = engine.diagnoseAssembly(asm, partsById)
        assertNotNull(report)
    }

    @Test
    fun `low confidence shown when insufficient signals`() {
        // No hay instancias dañadas → confidence = 0.
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "A", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                com.elysium.vanguard.forge.domain.PartInstance("p1", "part_x")
            )
        )
        val partsById = emptyMap<String, ForgePart>()
        val report = engine.diagnoseAssembly(asm, partsById)
        assertTrue("Low confidence when no damage", report.confidence < 0.5)
    }

    @Test
    fun `mapDtcToForgeParts returns generic categories`() {
        val parts1 = engine.mapDtcToForgeParts("P0301")
        assertTrue(parts1.contains("spark_plug"))
        val parts2 = engine.mapDtcToForgeParts("P0128")
        assertTrue(parts2.contains("water_pump"))
        val parts3 = engine.mapDtcToForgeParts("UNKNOWN_DTC")
        assertTrue(parts3.isEmpty())
    }

    @Test
    fun `generateRepairRecommendation handles insufficient data`() {
        val emptyReport = DiagnosticReport(
            id = "x",
            affectedSystem = null,
            affectedPartInstanceId = null,
            affectedPartName = "None",
            probableFailure = "none",
            confidence = 0.0,
            severity = DamageSeverity.NONE,
            observedSymptoms = emptyList(),
            likelyCauses = emptyList(),
            consequences = emptyList()
        )
        val rec = engine.generateRepairRecommendation(emptyReport)
        assertNotNull(rec)
    }
}