package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * Community-submitted real-world repair case.
 * Per the spec: a case requires country, vehicle, DTC, symptoms,
 * cause confirmed, repair done, parts changed, cost, time, photos,
 * outcome, license consent.
 */
@Serializable
data class CommunityCase(
    val id: String,
    val country: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val engine: String? = null,
    val transmission: String? = null,
    val odometerKm: Double? = null,
    val dtcCode: String,
    val symptoms: String,
    val freezeFrame: Map<String, Double> = emptyMap(),
    val livePids: Map<String, Double> = emptyMap(),
    val confirmedCause: String,
    val evidence: String,
    val repairDone: String,
    val partChanged: String? = null,
    val costCents: Long? = null,
    val timeMinutes: Int? = null,
    val photos: List<String> = emptyList(),
    val resolved: Boolean,
    val licenseConsent: Boolean = false,
    val status: CommunityCaseStatus = CommunityCaseStatus.PENDING_REVIEW,
    val submittedBy: String,
    val submittedAt: Long
)

@Serializable
enum class CommunityCaseStatus {
    PENDING_REVIEW,
    AI_REVIEWED,
    MECHANIC_VERIFIED,
    COMMUNITY_CONFIRMED,
    REJECTED,
    NEEDS_MORE_EVIDENCE
}

/**
 * Aggregated community reputation for a user (mechanic or contributor).
 * Per spec: verifiedCases, successVotes, failureVotes, brandsSpecialty,
 * systemsSpecialty, country, confidenceScore, abuseScore.
 */
@Serializable
data class CommunityReputation(
    val userId: String,
    val verifiedCases: Int = 0,
    val successVotes: Int = 0,
    val failureVotes: Int = 0,
    val brandsSpecialty: List<String> = emptyList(),
    val systemsSpecialty: List<String> = emptyList(),
    val country: String? = null,
    val confidenceScore: Double = 0.5,
    val abuseScore: Double = 0.0
) {
    fun trustLevel(): TrustLevel = when {
        abuseScore > 0.3 -> TrustLevel.BLOCKED
        verifiedCases >= 50 && confidenceScore > 0.8 -> TrustLevel.HIGH
        verifiedCases >= 10 && confidenceScore > 0.6 -> TrustLevel.MEDIUM
        verifiedCases >= 1 -> TrustLevel.LOW
        else -> TrustLevel.NEW
    }
}

@Serializable
enum class TrustLevel {
    BLOCKED, NEW, LOW, MEDIUM, HIGH
}

/**
 * Moderation engine: validates a community case before it goes public.
 * Per spec: anti-spam, dedup, dangerous diagnostics, abusive language,
 * minimum evidence.
 */
class CommunityModeration {

    data class ModerationResult(
        val accepted: Boolean,
        val reason: String = "",
        val suggestedStatus: CommunityCaseStatus
    )

    private val dangerousPhrases = listOf(
        "just replace", "skip the test", "ignore the multimeter",
        "no need to check", "trust me"
    )

    fun moderate(case: CommunityCase): ModerationResult {
        // Rule 1: minimum evidence
        if (case.dtcCode.isBlank() || case.symptoms.isBlank() ||
            case.confirmedCause.isBlank() || case.repairDone.isBlank()) {
            return ModerationResult(
                accepted = false,
                reason = "Faltan campos requeridos: dtcCode, symptoms, confirmedCause, repairDone",
                suggestedStatus = CommunityCaseStatus.NEEDS_MORE_EVIDENCE
            )
        }

        // Rule 2: license consent
        if (!case.licenseConsent) {
            return ModerationResult(
                accepted = false,
                reason = "Sin consentimiento de licencia. Usuario debe aceptar licencia de contribucion.",
                suggestedStatus = CommunityCaseStatus.NEEDS_MORE_EVIDENCE
            )
        }

        // Rule 3: anti-dangerous advice
        val text = (case.symptoms + " " + case.confirmedCause + " " +
            case.repairDone + " " + case.evidence).lowercase()
        val danger = dangerousPhrases.firstOrNull { it in text }
        if (danger != null) {
            return ModerationResult(
                accepted = false,
                reason = "Diagnostico potencialmente peligroso detectado: '$danger'",
                suggestedStatus = CommunityCaseStatus.REJECTED
            )
        }

        // Rule 4: no VIN in plain text (privacy)
        val vinRegex = Regex("[A-HJ-NPR-Z0-9]{17}")
        if (vinRegex.containsMatchIn(case.evidence + case.symptoms)) {
            return ModerationResult(
                accepted = false,
                reason = "Posible VIN en texto plano. Remover antes de publicar.",
                suggestedStatus = CommunityCaseStatus.NEEDS_MORE_EVIDENCE
            )
        }

        // Rule 5: passed
        return ModerationResult(
            accepted = true,
            reason = "Caso aceptado. Visible para comunidad.",
            suggestedStatus = CommunityCaseStatus.AI_REVIEWED
        )
    }

    fun computeReputation(cases: List<CommunityCase>): CommunityReputation {
        if (cases.isEmpty()) {
            return CommunityReputation(userId = "unknown")
        }
        val verified = cases.count { it.status == CommunityCaseStatus.MECHANIC_VERIFIED }
        val resolved = cases.count { it.resolved }
        val brands = cases.map { it.vehicleMake }.distinct()
        val systems = cases.map { it.dtcCode.substring(0, 1) }.distinct()  // P/C/B/U
        val confidence = (resolved.toDouble() / cases.size).coerceIn(0.0, 1.0)
        val userId = cases.first().submittedBy
        return CommunityReputation(
            userId = userId,
            verifiedCases = verified,
            successVotes = resolved,
            brandsSpecialty = brands,
            systemsSpecialty = systems,
            country = cases.first().country,
            confidenceScore = confidence
        )
    }
}
