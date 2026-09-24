package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.ReconciliationInput
import com.elysium369.meet.safety.domain.SafetyReconciliationEngine
import com.elysium369.meet.safety.domain.SafetySourceClass
import org.junit.Assert.*
import org.junit.Test

class SafetySourceIndependenceTest {

    @Test
    fun `10 articles from same class JOURNALISTIC means mediaOnly true and requiresReview true`() {
        val input = ReconciliationInput(
            eventId = "ev-1",
            sourceClasses = List(10) { SafetySourceClass.JOURNALISTIC }
        )
        val result = SafetyReconciliationEngine.evaluate(input)
        assertTrue(result.mediaOnly)
        assertTrue(result.requiresReview)
    }

    @Test
    fun `Mix of CIVIL and JOURNALISTIC means requiresReview false`() {
        val input = ReconciliationInput(
            eventId = "ev-2",
            sourceClasses = listOf(
                SafetySourceClass.CIVIL,
                SafetySourceClass.JOURNALISTIC
            )
        )
        val result = SafetyReconciliationEngine.evaluate(input)
        assertFalse(result.requiresReview)
    }

    @Test
    fun `Single CIVIL source means civilOnly true and requiresReview true`() {
        val input = ReconciliationInput(
            eventId = "ev-3",
            sourceClasses = listOf(SafetySourceClass.CIVIL)
        )
        val result = SafetyReconciliationEngine.evaluate(input)
        assertTrue(result.civilOnly)
        assertTrue(result.requiresReview)
    }

    @Test
    fun `All three classes present means requiresReview false`() {
        val input = ReconciliationInput(
            eventId = "ev-4",
            sourceClasses = listOf(
                SafetySourceClass.CIVIL,
                SafetySourceClass.JOURNALISTIC,
                SafetySourceClass.PUBLIC_RECORD
            )
        )
        val result = SafetyReconciliationEngine.evaluate(input)
        assertFalse(result.requiresReview)
    }
}
