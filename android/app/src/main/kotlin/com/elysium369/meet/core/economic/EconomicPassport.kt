package com.elysium369.meet.core.economic

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import com.elysium369.meet.education.economic.SkillCredentialState
import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §46, §48, §54 — Economic Passport & Evidence-Based Reputation.
 *
 * Reputation is derived from VERIFIED WORK, not stars (§46).
 * A human's economic passport is the union of:
 * - Demonstrated skills with credential states
 * - Verified job outcomes with evidence
 * - Domain-specific scores (not aggregated into a single meaningless number)
 * - Portable, exportable, user-controlled (§54)
 */

// ─── Economic Passport ───

@Serializable
data class EconomicPassport(
    val ownerId: String,
    val displayName: String,
    val skills: List<VerifiedSkill> = emptyList(),
    val domainScores: List<DomainReputationScore> = emptyList(),
    val workHistory: List<WorkHistoryEntry> = emptyList(),
    val credentials: List<ExternalCredential> = emptyList(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val lastUpdatedAtEpochMs: Long = System.currentTimeMillis(),
    val version: Int = 1,
) {
    /**
     * §46: Reputation is domain-specific. Never collapse into single number.
     * Returns the score for a specific domain, or null if no track record.
     */
    fun reputationFor(domain: UniversalServiceDomain): DomainReputationScore? {
        return domainScores.firstOrNull { it.domain == domain }
    }

    val totalVerifiedJobs: Int
        get() = workHistory.count { it.outcomeVerified }

    val overallVerifiedRate: Double
        get() {
            if (workHistory.isEmpty()) return 0.0
            return totalVerifiedJobs.toDouble() / workHistory.size
        }
}

// ─── Verified Skill ───

@Serializable
data class VerifiedSkill(
    val skillId: String,
    val name: String,
    val domain: UniversalServiceDomain,
    val credentialState: SkillCredentialState,
    val evidenceCount: Int = 0,
    val lastDemonstratedAtEpochMs: Long? = null,
    val iscoCode: String? = null,
) {
    /**
     * §42: KNOWLEDGE ≠ COMPETENCE ≠ CREDENTIAL.
     * Only DEMONSTRATED or higher can be shown to potential customers.
     */
    val isMarketplaceVisible: Boolean
        get() = credentialState.ordinal >= SkillCredentialState.DEMONSTRATED.ordinal
}

// ─── Domain Reputation Score ───

/**
 * §46: Reputation derived from verified outcomes, not star ratings.
 * Each domain has independent scores — a great plumber doesn't
 * automatically become a great electrician.
 */
@Serializable
data class DomainReputationScore(
    val domain: UniversalServiceDomain,
    val completedJobs: Int = 0,
    val verifiedOutcomes: Int = 0,
    val exceededExpectations: Int = 0,
    val disputes: Int = 0,
    val repeatCustomers: Int = 0,
    val averageCompletionMinutes: Int = 0,
    val warrantyHonored: Int = 0,
    val warrantyClaimed: Int = 0,
) {
    /**
     * Outcome-based reputation score (0.0-1.0).
     * Weighted: verified outcomes (40%), repeat customers (25%),
     * exceeded expectations (20%), dispute-free (15%).
     */
    val score: Double
        get() {
            if (completedJobs == 0) return 0.0
            val verifiedRate = verifiedOutcomes.toDouble() / completedJobs
            val repeatRate = repeatCustomers.toDouble() / completedJobs
            val excellenceRate = exceededExpectations.toDouble() / completedJobs
            val disputeFreeRate = 1.0 - (disputes.toDouble() / completedJobs)
            return ((verifiedRate * 0.40) + (repeatRate * 0.25) +
                (excellenceRate * 0.20) + (disputeFreeRate * 0.15))
                .coerceIn(0.0, 1.0)
        }

    val hasTrackRecord: Boolean get() = completedJobs >= 3
}

// ─── Work History Entry ───

@Serializable
data class WorkHistoryEntry(
    val jobId: String,
    val domain: UniversalServiceDomain,
    val description: String,
    val customerConfirmed: Boolean = false,
    val outcomeVerified: Boolean = false,
    val completedAtEpochMs: Long,
    val durationMinutes: Int? = null,
    val evidenceRefs: List<String> = emptyList(),
)

// ─── External Credential ───

@Serializable
data class ExternalCredential(
    val name: String,
    val issuedBy: String,
    val issuedAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val verificationUrl: String? = null,
    val verifiedByPlatform: Boolean = false,
    val domain: UniversalServiceDomain,
) {
    val isExpired: Boolean
        get() = expiresAtEpochMs != null && expiresAtEpochMs < System.currentTimeMillis()

    val isVerified: Boolean
        get() = verifiedByPlatform && !isExpired
}

// ─── Passport Engine ───

object EconomicPassportEngine {

    /**
     * Records a verified job outcome into the passport.
     */
    fun recordOutcome(
        passport: EconomicPassport,
        jobId: String,
        domain: UniversalServiceDomain,
        description: String,
        customerConfirmed: Boolean,
        evidenceRefs: List<String>,
        durationMinutes: Int?,
        exceededExpectations: Boolean = false,
        isRepeatCustomer: Boolean = false,
    ): EconomicPassport {
        val isVerified = customerConfirmed && evidenceRefs.isNotEmpty()

        val entry = WorkHistoryEntry(
            jobId = jobId,
            domain = domain,
            description = description,
            customerConfirmed = customerConfirmed,
            outcomeVerified = isVerified,
            completedAtEpochMs = System.currentTimeMillis(),
            durationMinutes = durationMinutes,
            evidenceRefs = evidenceRefs,
        )

        // Update domain score
        val existingScore = passport.domainScores.firstOrNull { it.domain == domain }
            ?: DomainReputationScore(domain = domain)

        val updatedScore = existingScore.copy(
            completedJobs = existingScore.completedJobs + 1,
            verifiedOutcomes = existingScore.verifiedOutcomes + if (isVerified) 1 else 0,
            exceededExpectations = existingScore.exceededExpectations + if (exceededExpectations) 1 else 0,
            repeatCustomers = existingScore.repeatCustomers + if (isRepeatCustomer) 1 else 0,
            averageCompletionMinutes = if (durationMinutes != null) {
                ((existingScore.averageCompletionMinutes.toLong() *
                    existingScore.completedJobs + durationMinutes) /
                    (existingScore.completedJobs + 1)).toInt()
            } else existingScore.averageCompletionMinutes,
        )

        val newScores = passport.domainScores.filter { it.domain != domain } + updatedScore

        return passport.copy(
            workHistory = passport.workHistory + entry,
            domainScores = newScores,
            lastUpdatedAtEpochMs = System.currentTimeMillis(),
        )
    }
}
