package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.*
import org.junit.Assert.*
import org.junit.Test

class SafetyReviewEnginesTest {
    @Test fun `single source universe requests review without alleging concealment`() {
        val result = SafetyReconciliationEngine.evaluate(
            ReconciliationInput("event", listOf(SafetySourceClass.CIVIL)),
        )
        assertTrue(result.civilOnly)
        assertTrue(result.requiresReview)
        assertFalse(result.mediaOnly)
    }

    @Test fun `publication preview redacts private location and never directly authorizes exact publication`() {
        val decision = SafetyPublicationPolicy.eligibleDecision(
            PublicationAssessment(true, true, false, 3, 0),
        )
        assertEquals(PublicationDecision.PUBLIC_REDACTED, decision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `entity resolution rejects unexplained reviewer verdict`() {
        SafetyEntityResolutionPolicy.recordDecision(
            EntityResolutionCandidate("a", "b"), EntityResolutionState.SAME_ENTITY,
            "reviewer", "short", 1L,
        )
    }
}
