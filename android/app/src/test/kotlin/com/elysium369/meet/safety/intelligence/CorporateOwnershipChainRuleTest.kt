package com.elysium369.meet.safety.intelligence

import com.elysium369.meet.safety.intelligence.domain.CorporateOwnershipChainRule
import com.elysium369.meet.safety.intelligence.domain.EconomicEntity
import com.elysium369.meet.safety.intelligence.domain.EconomicEntityType
import com.elysium369.meet.safety.intelligence.domain.EntityRelationship
import com.elysium369.meet.safety.intelligence.domain.OwnershipAnomalyType
import com.elysium369.meet.safety.intelligence.domain.RelationshipType
import org.junit.Assert.*
import org.junit.Test

class CorporateOwnershipChainRuleTest {

    @Test
    fun circularOwnershipCycleIsDetectedWithMandatoryAlternativeHypotheses() {
        val corpA = EconomicEntity("corp-A", EconomicEntityType.CORPORATION, "Empresa Alfa S.A.", "3-101-111")
        val corpB = EconomicEntity("corp-B", EconomicEntityType.CORPORATION, "Empresa Beta S.A.", "3-101-222")
        val corpC = EconomicEntity("corp-C", EconomicEntityType.CORPORATION, "Empresa Gamma S.A.", "3-101-333")

        val relationships = listOf(
            EntityRelationship("rel-1", "corp-A", "corp-B", RelationshipType.SHAREHOLDER, sharePercentage = 60.0, sourceRecordId = "src-1"),
            EntityRelationship("rel-2", "corp-B", "corp-C", RelationshipType.SHAREHOLDER, sharePercentage = 50.0, sourceRecordId = "src-2"),
            EntityRelationship("rel-3", "corp-C", "corp-A", RelationshipType.SHAREHOLDER, sharePercentage = 40.0, sourceRecordId = "src-3"),
        )

        val assessment = CorporateOwnershipChainRule.evaluate(
            entities = listOf(corpA, corpB, corpC),
            relationships = relationships,
        )

        assertEquals(OwnershipAnomalyType.CIRCULAR_OWNERSHIP_CYCLE, assessment.anomalyType)
        assertTrue(assessment.requiresHumanAnalystReview)
        assertTrue(
            "Every detected anomaly signal MUST provide mandatory legitimate alternative hypotheses",
            assessment.alternativeLegitimateHypotheses.isNotEmpty(),
        )
        assertTrue(assessment.alternativeLegitimateHypotheses.any { it.contains("holding") || it.contains("patrimonial") })
    }

    @Test
    fun linearCorporateStructureIsNormalAndDoesNotRaiseAnomaly() {
        val holding = EconomicEntity("corp-H", EconomicEntityType.CORPORATION, "Holding Central S.A.", "3-101-000")
        val subsidiary = EconomicEntity("corp-S", EconomicEntityType.CORPORATION, "Operadora Local S.A.", "3-101-999")

        val relationships = listOf(
            EntityRelationship("rel-1", "corp-H", "corp-S", RelationshipType.SUBSIDIARY_OF, sourceRecordId = "src-1"),
        )

        val assessment = CorporateOwnershipChainRule.evaluate(
            entities = listOf(holding, subsidiary),
            relationships = relationships,
        )

        assertEquals(OwnershipAnomalyType.NORMAL_STRUCTURE, assessment.anomalyType)
        assertFalse(assessment.requiresHumanAnalystReview)
    }
}
