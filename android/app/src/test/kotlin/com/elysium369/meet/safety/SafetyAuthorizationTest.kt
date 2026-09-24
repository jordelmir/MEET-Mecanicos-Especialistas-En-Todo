package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.PolicyDecision
import com.elysium369.meet.safety.domain.SafetyAction
import com.elysium369.meet.safety.domain.SafetyAuthorizationContext
import com.elysium369.meet.safety.domain.SafetyAuthorizationPolicy
import org.junit.Assert.*
import org.junit.Test

class SafetyAuthorizationTest {

    @Test
    fun `blank subjectId returns Deny`() {
        val context = SafetyAuthorizationContext(subjectId = "  ", action = SafetyAction.CREATE_REPORT)
        val decision = SafetyAuthorizationPolicy.evaluate(context)
        assertTrue(decision is PolicyDecision.Deny)
    }

    @Test
    fun `VIEW_PUBLIC_CASE always Allow`() {
        val context = SafetyAuthorizationContext(subjectId = "user1", action = SafetyAction.VIEW_PUBLIC_CASE)
        val decision = SafetyAuthorizationPolicy.evaluate(context)
        assertTrue(decision is PolicyDecision.Allow)
    }

    @Test
    fun `CREATE_REPORT with valid subject returns Allow`() {
        val context = SafetyAuthorizationContext(subjectId = "user1", action = SafetyAction.CREATE_REPORT)
        val decision = SafetyAuthorizationPolicy.evaluate(context)
        assertTrue(decision is PolicyDecision.Allow)
    }

    @Test
    fun `MODERATE returns Deny when elevated role required`() {
        val context = SafetyAuthorizationContext(subjectId = "user1", action = SafetyAction.MODERATE)
        val decision = SafetyAuthorizationPolicy.evaluate(context)
        assertTrue(decision is PolicyDecision.Deny)
    }
}
