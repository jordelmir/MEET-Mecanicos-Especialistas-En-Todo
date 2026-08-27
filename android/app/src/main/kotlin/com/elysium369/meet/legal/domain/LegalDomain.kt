package com.elysium369.meet.legal.domain

import com.elysium369.meet.platform.marketos.ProfessionalCredentialProof
import java.util.UUID

enum class LegalAuthority { CAAB, DNN }
enum class LegalConflictDecision { PENDING, CLEAR, POSSIBLE_CONFLICT, CONFLICT }
enum class LegalMatterState { DRAFT, CONFLICT_SCREENING, MATCHING, OFFERED, ENGAGED, ACTIVE, COMPLETED, VOIDED }
enum class LegalDisclosureLevel { TRIAGE_ONLY, PARTY_NAMES_ONLY, CONFLICT_CLEARED, ENGAGED }
enum class LegalFeeModel { FIXED, HOURLY, PER_STAGE, RETAINER, TARIFF_BASED, QUOTE_AFTER_CONSULTATION }

data class LegalProfessionalEligibility(
    val lawyerProof: ProfessionalCredentialProof,
    val notaryProof: ProfessionalCredentialProof?,
    val declaredCapabilities: Set<String>,
    val demonstratedMatterCount: Int,
) {
    fun canPracticeLaw(nowEpochMs: Long, freshnessWindowMs: Long): Boolean =
        lawyerProof.isActiveAt(nowEpochMs, freshnessWindowMs)

    fun canOfferNotarialService(nowEpochMs: Long, freshnessWindowMs: Long): Boolean =
        canPracticeLaw(nowEpochMs, freshnessWindowMs) &&
            notaryProof?.isActiveAt(nowEpochMs, freshnessWindowMs) == true
}

data class LegalMatterParty(
    val partyId: UUID,
    val role: String,
    val privateDisplayName: String,
    val conflictFingerprint: String,
) {
    init {
        require(role.isNotBlank())
        require(privateDisplayName.isNotBlank())
        require(conflictFingerprint.matches(Regex("[a-f0-9]{64}")))
    }
}

data class LegalMatter(
    val matterId: UUID,
    val clientPrincipalId: UUID,
    val categoryCode: String,
    val subcategoryCode: String?,
    val humanSummary: String,
    val jurisdictionCode: String,
    val urgency: String,
    val state: LegalMatterState,
    val disclosureLevel: LegalDisclosureLevel,
    val version: Long,
) {
    init {
        require(categoryCode.isNotBlank())
        require(humanSummary.isNotBlank())
        require(version >= 0)
    }
}

data class LegalConflictCheck(
    val matterId: UUID,
    val professionalPrincipalId: UUID,
    val organizationId: UUID?,
    val decision: LegalConflictDecision,
    val checkedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
) {
    fun permitsPrivilegedDisclosure(nowEpochMs: Long): Boolean =
        decision == LegalConflictDecision.CLEAR && expiresAtEpochMs >= nowEpochMs
}

object LegalAccessPolicy {
    fun mayReadPrivilegedMatter(
        actorPrincipalId: UUID,
        matter: LegalMatter,
        engagementProfessionalIds: Set<UUID>,
        conflictCheck: LegalConflictCheck?,
        nowEpochMs: Long,
    ): Boolean {
        if (actorPrincipalId == matter.clientPrincipalId) return true
        if (actorPrincipalId !in engagementProfessionalIds) return false
        return conflictCheck?.professionalPrincipalId == actorPrincipalId &&
            conflictCheck.permitsPrivilegedDisclosure(nowEpochMs) &&
            matter.disclosureLevel >= LegalDisclosureLevel.CONFLICT_CLEARED
    }
}
