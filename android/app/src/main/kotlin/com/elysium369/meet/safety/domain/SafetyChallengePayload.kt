package com.elysium369.meet.safety.domain

import kotlinx.serialization.Serializable

enum class SafetyChallengeKind {
    REPORT_ERROR,
    CONTRARY_EVIDENCE,
    RECTIFICATION,
}

/** Local intent only. Publication and claim state remain server-authoritative. */
@Serializable
data class SafetyChallengePayload(
    val caseId: String,
    val claimId: String?,
    val kind: SafetyChallengeKind,
    val narrative: String,
    val sourceUrl: String?,
)
