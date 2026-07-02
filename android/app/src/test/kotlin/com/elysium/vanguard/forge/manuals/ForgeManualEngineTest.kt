package com.elysium.vanguard.forge.manuals

import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageType
import com.elysium.vanguard.forge.domain.FailureMode
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgeManualType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ProcedureStep
import com.elysium.vanguard.forge.domain.RepairProcedure
import com.elysium.vanguard.forge.domain.ReplacementProcedure
import com.elysium.vanguard.forge.domain.SafetyClassification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeManualEngineTest {

    private val engine = ForgeManualEngine()

    private fun partWithRepair(): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = "spark_plug", name = "Spark Plug", artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.EDUCATIONAL
        ),
        repairProcedures = listOf(
            RepairProcedure(
                id = "rep1",
                title = "Reemplazo bujía",
                partId = "spark_plug",
                steps = listOf(ProcedureStep(1, "Remover", "Girar"))
            )
        ),
        replacementProcedures = listOf(
            ReplacementProcedure(
                id = "repl1",
                title = "Reemplazo",
                partId = "spark_plug",
                steps = listOf(ProcedureStep(1, "Instalar", "Torquear"))
            )
        ),
        relatedDtcCodes = listOf("P0301")
    )

    @Test
    fun `repair manual generated from failure has steps`() {
        val failure = FailureMode(
            id = "fm1",
            partId = "spark_plug",
            title = "Bujía dañada",
            damageType = DamageType.ELECTRICAL_OPEN,
            symptoms = listOf("Misfire"),
            causes = listOf("Electrodo desgastado"),
            consequences = listOf("Pérdida de potencia"),
            severity = DamageSeverity.MEDIUM
        )
        val manual = engine.generateRepairManual(failure, partWithRepair())
        assertTrue(manual.steps.isNotEmpty())
        assertTrue("Manual must include safety warning", manual.safetyWarnings.isNotEmpty())
    }

    @Test
    fun `replacement manual has ordered steps`() {
        val manual = engine.generateReplacementManual(partWithRepair())
        assertTrue(manual.steps.size >= 2)
        // Steps deben tener orden ascendente.
        val orders = manual.steps.map { it.order }
        assertTrue(orders == orders.sorted())
    }

    @Test
    fun `manual validation fails without safety warning`() {
        val badManual = engine.createManualFromPart(partWithRepair()).copy(
            safetyWarnings = emptyList()
        )
        val issues = engine.validateManualCompleteness(badManual)
        assertTrue(issues.any { it.contains("advertencias", ignoreCase = true) })
    }

    @Test
    fun `torque spec preserved in replacement manual`() {
        // Para que este test sea significativo, necesitaríamos un ReplacementProcedure con torqueSpecs.
        // Verificamos que si existe, se preserva.
        val partWithTorque = partWithRepair().copy(
            replacementProcedures = listOf(
                ReplacementProcedure(
                    id = "repl1",
                    title = "Reemplazo con torque",
                    partId = "spark_plug",
                    steps = listOf(ProcedureStep(1, "Torquear", "Aplicar torque")),
                    torqueSpecs = listOf(com.elysium.vanguard.forge.domain.TorqueSpec(
                        fastenerName = "Spark plug",
                        torqueNm = 25.0
                    ))
                )
            )
        )
        val manual = engine.generateReplacementManual(partWithTorque)
        assertTrue(manual.torqueSpecs.isNotEmpty())
        assertTrue(manual.torqueSpecs.any { it.torqueNm == 25.0 })
    }

    @Test
    fun `every manual has at least one safety warning`() {
        val manual = engine.createManualFromPart(partWithRepair())
        assertFalse("Manual must have at least one safety warning", manual.safetyWarnings.isEmpty())
    }
}