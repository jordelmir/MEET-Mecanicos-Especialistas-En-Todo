package com.elysium369.meet.safety.intelligence

import com.elysium369.meet.safety.intelligence.domain.EconomicEntity
import com.elysium369.meet.safety.intelligence.domain.EconomicEntityType
import com.elysium369.meet.safety.intelligence.domain.EntityResolutionEngine
import com.elysium369.meet.safety.intelligence.domain.EntityResolutionOutcome
import org.junit.Assert.*
import org.junit.Test

class SafetyEntityResolutionEngineTest {

    @Test
    fun exactTaxIdMatchConfirmsSameEntity() {
        val companyA = EconomicEntity(
            entityId = "ent-1",
            entityType = EconomicEntityType.CORPORATION,
            registeredName = "Consorcio Vial Del Este S.A.",
            canonicalTaxId = "3-101-789012",
        )
        val companyB = EconomicEntity(
            entityId = "ent-2",
            entityType = EconomicEntityType.CORPORATION,
            registeredName = "Vial del Este S.A.",
            canonicalTaxId = "3-101-789012",
        )

        val result = EntityResolutionEngine.evaluate(companyA, companyB)

        assertEquals(EntityResolutionOutcome.MATCH_CONFIRMED_IDENTIFIER, result.outcome)
        assertTrue(result.canAutomaticallyMerge)
        assertEquals(1.0, result.confidenceScore, 0.001)
    }

    @Test
    fun differingTaxIdsConfirmDistinctEntitiesEvenWithIdenticalNames() {
        val companyA = EconomicEntity(
            entityId = "ent-1",
            entityType = EconomicEntityType.CORPORATION,
            registeredName = "Constructora San José S.A.",
            canonicalTaxId = "3-101-111111",
        )
        val companyB = EconomicEntity(
            entityId = "ent-2",
            entityType = EconomicEntityType.CORPORATION,
            registeredName = "Constructora San José S.A.",
            canonicalTaxId = "3-101-222222",
        )

        val result = EntityResolutionEngine.evaluate(companyA, companyB)

        assertEquals(EntityResolutionOutcome.DISTINCT_ENTITIES_IDENTIFIER_MISMATCH, result.outcome)
        assertFalse(result.canAutomaticallyMerge)
    }

    @Test
    fun similarNamesWithoutTaxIdAreStrictlyRejectedFromMerging() {
        val companyA = EconomicEntity(
            entityId = "ent-1",
            entityType = EconomicEntityType.CORPORATION,
            registeredName = "Transportes del Pacífico Central S.A.",
            canonicalTaxId = null,
        )
        val companyB = EconomicEntity(
            entityId = "ent-2",
            entityType = EconomicEntityType.CORPORATION,
            registeredName = "Transportes del Pacifico Central Limitada",
            canonicalTaxId = null,
        )

        val result = EntityResolutionEngine.evaluate(companyA, companyB)

        assertEquals(EntityResolutionOutcome.REJECTED_WEAK_MATCH_NAME_OR_ADDRESS_ONLY, result.outcome)
        assertFalse(
            "Under conservative entity resolution, name similarity alone MUST NEVER trigger automatic merge",
            result.canAutomaticallyMerge,
        )
        assertTrue(result.rationale.contains("Principio contra falsos positivos"))
    }
}
