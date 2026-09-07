package com.elysium369.meet.core.intelligence

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import org.junit.Assert.*
import org.junit.Test

class CollectiveIntelligenceTest {

    private fun outcome(
        dtcs: List<String> = listOf("P0300", "P0171"),
        brand: String = "Toyota",
        model: String = "Corolla",
        year: Int = 2015,
        diagnosis: String = "Empaque múltiple admisión",
        repair: String = "Reemplazo empaque",
        success: Boolean = true,
        cost: Double = 150.0,
        mechanicId: String = "mech-${System.currentTimeMillis()}",
    ) = RepairOutcome(
        outcomeId = "o-${System.currentTimeMillis()}-${(Math.random()*1000).toInt()}",
        mechanicId = mechanicId,
        domain = UniversalServiceDomain.AUTO_MECHANICAL,
        vehicleBrand = brand, vehicleModel = model, vehicleYear = year,
        dtcCodes = dtcs, diagnosisDescription = diagnosis,
        repairAction = repair, wasSuccessful = success,
        costUsd = cost, laborMinutes = 60,
        partsUsed = listOf("Empaque", "Tornillos"),
    )

    @Test
    fun `record outcomes builds knowledge base`() {
        val engine = CollectiveIntelligenceEngine()
        engine.recordOutcome(outcome())
        engine.recordOutcome(outcome())
        assertEquals(2, engine.totalKnowledge)
    }

    @Test
    fun `compute pattern from 5 matching outcomes`() {
        val engine = CollectiveIntelligenceEngine()
        repeat(5) { engine.recordOutcome(outcome(mechanicId = "mech-$it")) }
        val patterns = engine.computePatterns(listOf("P0300", "P0171"), "Toyota", "Corolla")
        assertEquals(1, patterns.size)
        assertEquals(100.0, patterns.first().confidencePercent, 0.001)
        assertEquals(5, patterns.first().dataPoints)
    }

    @Test
    fun `confidence drops when some repairs fail`() {
        val engine = CollectiveIntelligenceEngine()
        repeat(7) { engine.recordOutcome(outcome(success = true, mechanicId = "m-$it")) }
        repeat(3) { engine.recordOutcome(outcome(success = false, mechanicId = "mf-$it")) }
        val patterns = engine.computePatterns(listOf("P0300", "P0171"), "Toyota")
        assertEquals(70.0, patterns.first().confidencePercent, 0.001)
    }

    @Test
    fun `evidence basis is honest for sparse data`() {
        val engine = CollectiveIntelligenceEngine()
        repeat(2) { engine.recordOutcome(outcome(mechanicId = "m-$it")) }
        val patterns = engine.computePatterns(listOf("P0300", "P0171"), "Toyota")
        assertTrue(patterns.first().evidenceBasis.contains("Dato preliminar"))
        assertFalse(patterns.first().isReliable)
    }

    @Test
    fun `evidence basis is strong for 50 plus data points`() {
        val pattern = CollectivePattern(
            "cp-1", listOf("P0300"), VehicleSelector("Toyota"),
            "Diagnosis", "Repair", 85.0, 55, 0.85, 200.0, 60, listOf("Part1"),
        )
        assertTrue(pattern.evidenceBasis.contains("Alta confianza"))
        assertTrue(pattern.isReliable)
    }

    @Test
    fun `getInsight returns disclaimer when no data`() {
        val engine = CollectiveIntelligenceEngine()
        val insight = engine.getInsight(listOf("P9999"), "Unknown", "Car", 2020)
        assertNull(insight.topDiagnosis)
        assertTrue(insight.disclaimer.contains("Sin datos colectivos"))
    }

    @Test
    fun `multiple diagnoses for same DTCs ranked by confidence`() {
        val engine = CollectiveIntelligenceEngine()
        repeat(8) { engine.recordOutcome(outcome(diagnosis = "Empaque", mechanicId = "m-$it")) }
        repeat(2) { engine.recordOutcome(outcome(diagnosis = "Sensor O2", mechanicId = "s-$it")) }
        val insight = engine.getInsight(listOf("P0300", "P0171"), "Toyota", "Corolla", 2015)
        assertNotNull(insight.topDiagnosis)
        assertEquals("Empaque", insight.topDiagnosis!!.mostLikelyDiagnosis)
        assertEquals(1, insight.alternativeDiagnoses.size)
    }

    @Test
    fun `network stats tracks contributors`() {
        val engine = CollectiveIntelligenceEngine()
        engine.recordOutcome(outcome(mechanicId = "mech-1"))
        engine.recordOutcome(outcome(mechanicId = "mech-2"))
        engine.recordOutcome(outcome(mechanicId = "mech-1", dtcs = listOf("P0420")))
        val stats = engine.networkStats()
        assertEquals(3, stats.totalOutcomes)
        assertEquals(2, stats.contributingMechanics)
        assertEquals(2, stats.uniqueDtcCombinations)
    }

    @Test
    fun `average cost computed from network`() {
        val engine = CollectiveIntelligenceEngine()
        engine.recordOutcome(outcome(cost = 100.0, mechanicId = "m1"))
        engine.recordOutcome(outcome(cost = 200.0, mechanicId = "m2"))
        engine.recordOutcome(outcome(cost = 300.0, mechanicId = "m3"))
        val patterns = engine.computePatterns(listOf("P0300", "P0171"), "Toyota")
        assertEquals(200.0, patterns.first().averageCostUsd, 0.001)
    }

    @Test
    fun `vehicle selector filters correctly`() {
        val selector = VehicleSelector("Toyota", "Corolla", 2010, 2020)
        assertTrue(selector.matches("Toyota", "Corolla", 2015, ""))
        assertFalse(selector.matches("Honda", "Civic", 2015, ""))
        assertFalse(selector.matches("Toyota", "Corolla", 2025, ""))
    }
}
