package com.elysium369.meet.safety.domain

import java.time.Instant

data class SafetyAuthorizationContext(
    val subjectId: String,
    val action: SafetyAction,
    val resourceId: String = "",
    val incidentId: String? = null,
    val now: Instant = Instant.now(),
)

enum class SafetyAction {
    CREATE_REPORT,
    VIEW_OWN_REPORT,
    VIEW_PUBLIC_CASE,
    ATTACH_EVIDENCE,
    SUBMIT_CHALLENGE,
    MODERATE,
    WITHDRAW_REPORT,
}

sealed interface PolicyDecision {
    data class Allow(val expiresAt: Instant?) : PolicyDecision
    data class Deny(val reason: String) : PolicyDecision
}

object SafetyAuthorizationPolicy {
    fun evaluate(ctx: SafetyAuthorizationContext): PolicyDecision {
        if (ctx.subjectId.isBlank()) return PolicyDecision.Deny("Missing subject identity")
        return when (ctx.action) {
            SafetyAction.VIEW_PUBLIC_CASE -> PolicyDecision.Allow(null)
            SafetyAction.CREATE_REPORT,
            SafetyAction.ATTACH_EVIDENCE,
            SafetyAction.SUBMIT_CHALLENGE,
            SafetyAction.VIEW_OWN_REPORT,
            SafetyAction.WITHDRAW_REPORT -> {
                if (ctx.subjectId.isBlank()) PolicyDecision.Deny("Authentication required")
                else PolicyDecision.Allow(null)
            }
            SafetyAction.MODERATE -> PolicyDecision.Deny("Moderation requires elevated role")
        }
    }
}
