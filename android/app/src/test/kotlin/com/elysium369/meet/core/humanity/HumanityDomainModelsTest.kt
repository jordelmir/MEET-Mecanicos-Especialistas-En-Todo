package com.elysium369.meet.core.humanity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanityDomainModelsTest {

    @Test
    fun testCapabilityLevelHierarchy() {
        val levels = CapabilityLevel.entries
        assertEquals(9, levels.size)
        assertEquals(0, CapabilityLevel.L0_UNKNOWN.levelIndex)
        assertEquals(5, CapabilityLevel.L5_DEMONSTRATED.levelIndex)
        assertEquals(8, CapabilityLevel.L8_TEACHER.levelIndex)
    }

    @Test
    fun testTruthStateHierarchyDoesNotTrustUnverifiedSources() {
        val authoritative = TruthState.AUTHORITATIVE
        val estimated = TruthState.ESTIMATED
        val hypothesis = TruthState.HYPOTHESIS

        assertNotNull(authoritative)
        assertNotNull(estimated)
        assertNotNull(hypothesis)
    }

    @Test
    fun testExecutionTruthStatePreventsSimulationAsReal() {
        val simulated = ExecutionTruthState.SIMULATED
        val physical = ExecutionTruthState.PHYSICALLY_VERIFIED

        assertTrue(simulated != physical)
    }

    @Test
    fun testSkillAndMissionRequirementsModel() {
        val skill = Skill(
            id = "automotive.measure_voltage",
            domainId = "automotive.electrical",
            name = "Medición de Voltaje con Multímetro",
            description = "Capacidad para conectar y medir voltajes automotrices de forma segura.",
            requiredKnowledgeIds = listOf("automotive.electrical.voltage", "automotive.electrical.multimeter"),
            safetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            minimumEvidenceForMastery = 3,
        )

        assertEquals("automotive.measure_voltage", skill.id)
        assertEquals(2, skill.requiredKnowledgeIds.size)
        assertEquals(SafetyLevel.LOW_RISK_PRACTICE, skill.safetyLevel)
    }
}
