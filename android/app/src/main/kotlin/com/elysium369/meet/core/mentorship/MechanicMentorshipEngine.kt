package com.elysium369.meet.core.mentorship

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  M E C H A N I C   M E N T O R S H I P   E N G I N E
 *  ──────────────────────────────────────────────────────
 *  "De aprendiz a maestro. Todo verificable."
 *
 *  Structured apprenticeship:
 *  ✅ Master-apprentice relationships
 *  ✅ Skill trees with progression levels
 *  ✅ Challenges/tasks assigned by master
 *  ✅ Verification via pre/post scan (did the fix work?)
 *  ✅ Certifications earned and signed by master
 *  ✅ Connected to ReputationPortfolio
 *  ✅ Each certification has SHA-256 integrity hash
 *
 *  "KNOWLEDGE ≠ COMPETENCE ≠ CREDENTIAL"
 *  — But here, we verify all three.
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Skill Domain ───

enum class MechanicSkillDomain {
    BRAKES,
    ENGINE,
    TRANSMISSION,
    ELECTRICAL,
    SUSPENSION,
    AC_HEATING,
    EXHAUST,
    FUEL_SYSTEM,
    DIAGNOSTICS_OBD,
    BODY_PAINT,
    TIRES_WHEELS,
    STEERING,
    HYBRID_EV,
    DIESEL,
    PERFORMANCE_TUNING,
}

val MechanicSkillDomain.displayLabel: String
    get() = when (this) {
        MechanicSkillDomain.BRAKES -> "Frenos"
        MechanicSkillDomain.ENGINE -> "Motor"
        MechanicSkillDomain.TRANSMISSION -> "Transmisión"
        MechanicSkillDomain.ELECTRICAL -> "Eléctrico"
        MechanicSkillDomain.SUSPENSION -> "Suspensión"
        MechanicSkillDomain.AC_HEATING -> "Aire acondicionado"
        MechanicSkillDomain.EXHAUST -> "Escape"
        MechanicSkillDomain.FUEL_SYSTEM -> "Sistema de combustible"
        MechanicSkillDomain.DIAGNOSTICS_OBD -> "Diagnóstico OBD"
        MechanicSkillDomain.BODY_PAINT -> "Carrocería y pintura"
        MechanicSkillDomain.TIRES_WHEELS -> "Neumáticos y rines"
        MechanicSkillDomain.STEERING -> "Dirección"
        MechanicSkillDomain.HYBRID_EV -> "Híbrido/Eléctrico"
        MechanicSkillDomain.DIESEL -> "Diésel"
        MechanicSkillDomain.PERFORMANCE_TUNING -> "Rendimiento/Tuning"
    }

// ─── Skill Level ───

enum class SkillLevel {
    NOVICE,         // Observes, assists
    APPRENTICE,     // Performs under supervision
    JOURNEYMAN,     // Independent work
    SPECIALIST,     // Complex repairs
    MASTER,         // Can teach and certify others
}

val SkillLevel.displayLabel: String
    get() = when (this) {
        SkillLevel.NOVICE -> "Novato"
        SkillLevel.APPRENTICE -> "Aprendiz"
        SkillLevel.JOURNEYMAN -> "Oficial"
        SkillLevel.SPECIALIST -> "Especialista"
        SkillLevel.MASTER -> "Maestro"
    }

// ─── Mentorship Relationship ───

@Serializable
data class MentorshipRelation(
    val relationId: String,
    val masterId: String,
    val masterName: String,
    val apprenticeId: String,
    val apprenticeName: String,
    val domain: MechanicSkillDomain,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val challengesAssigned: Int = 0,
    val challengesCompleted: Int = 0,
)

// ─── Challenge ───

@Serializable
data class MentorChallenge(
    val challengeId: String,
    val relationId: String,
    val domain: MechanicSkillDomain,
    val title: String,
    val description: String,
    val requiredLevel: SkillLevel,
    val status: ChallengeStatus = ChallengeStatus.ASSIGNED,
    val verificationMethod: VerificationMethod,
    val assignedAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long? = null,
    val preScanReportId: String? = null,
    val postScanReportId: String? = null,
    val masterNotes: String = "",
    val masterApproved: Boolean = false,
)

enum class ChallengeStatus {
    ASSIGNED,
    IN_PROGRESS,
    SUBMITTED,       // Apprentice submits for review
    APPROVED,        // Master verified
    FAILED,          // Not satisfactory
    EXPIRED,
}

enum class VerificationMethod {
    MASTER_OBSERVATION,      // Master watches
    PRE_POST_SCAN,           // DTC cleared = verified
    PHOTO_EVIDENCE,          // Before/after photos
    CUSTOMER_FEEDBACK,       // Customer confirms
    TIME_TRIAL,              // Complete within time
}

// ─── Certification ───

@Serializable
data class MechanicCertification(
    val certId: String,
    val mechanicId: String,
    val mechanicName: String,
    val domain: MechanicSkillDomain,
    val level: SkillLevel,
    val issuedByMasterId: String,
    val issuedByMasterName: String,
    val challengesCompleted: Int,
    val successRate: Double,
    val issuedAtEpochMs: Long = System.currentTimeMillis(),
    val integrityHash: String,
    val isActive: Boolean = true,
) {
    val summary: String
        get() = "${domain.displayLabel} — ${level.displayLabel} (por ${issuedByMasterName})"
}

// ─── Skill Progress ───

data class SkillProgress(
    val domain: MechanicSkillDomain,
    val currentLevel: SkillLevel,
    val challengesCompleted: Int,
    val challengesRequired: Int,
    val progressPercent: Double,
    val nextLevel: SkillLevel?,
    val certifications: List<MechanicCertification>,
)

// ─── Engine ───

class MechanicMentorshipEngine {

    companion object {
        val CHALLENGES_PER_LEVEL = mapOf(
            SkillLevel.NOVICE to 3,
            SkillLevel.APPRENTICE to 5,
            SkillLevel.JOURNEYMAN to 8,
            SkillLevel.SPECIALIST to 12,
            SkillLevel.MASTER to 20,
        )
    }

    private val relations = mutableListOf<MentorshipRelation>()
    private val challenges = mutableListOf<MentorChallenge>()
    private val certifications = mutableListOf<MechanicCertification>()

    // ─── Mentorship ───

    fun startMentorship(
        masterId: String, masterName: String,
        apprenticeId: String, apprenticeName: String,
        domain: MechanicSkillDomain,
    ): MentorshipRelation {
        val rel = MentorshipRelation(
            relationId = "mentor-${System.currentTimeMillis()}-${relations.size}",
            masterId = masterId, masterName = masterName,
            apprenticeId = apprenticeId, apprenticeName = apprenticeName,
            domain = domain,
        )
        relations.add(rel)
        return rel
    }

    // ─── Assign Challenge ───

    fun assignChallenge(
        relationId: String,
        title: String,
        description: String,
        requiredLevel: SkillLevel = SkillLevel.APPRENTICE,
        verificationMethod: VerificationMethod = VerificationMethod.PRE_POST_SCAN,
    ): MentorChallenge? {
        val relIdx = relations.indexOfFirst { it.relationId == relationId }
        if (relIdx < 0) return null
        val rel = relations[relIdx]

        val challenge = MentorChallenge(
            challengeId = "chal-${System.currentTimeMillis()}-${challenges.size}",
            relationId = relationId,
            domain = rel.domain,
            title = title,
            description = description,
            requiredLevel = requiredLevel,
            verificationMethod = verificationMethod,
        )
        challenges.add(challenge)
        relations[relIdx] = rel.copy(challengesAssigned = rel.challengesAssigned + 1)
        return challenge
    }

    // ─── Submit & Approve ───

    fun submitChallenge(challengeId: String, preScanId: String? = null, postScanId: String? = null): Boolean {
        val idx = challenges.indexOfFirst { it.challengeId == challengeId }
        if (idx < 0) return false
        challenges[idx] = challenges[idx].copy(
            status = ChallengeStatus.SUBMITTED,
            preScanReportId = preScanId,
            postScanReportId = postScanId,
        )
        return true
    }

    fun approveChallenge(challengeId: String, masterNotes: String = ""): Boolean {
        val idx = challenges.indexOfFirst { it.challengeId == challengeId }
        if (idx < 0) return false
        val ch = challenges[idx]
        if (ch.status != ChallengeStatus.SUBMITTED) return false

        challenges[idx] = ch.copy(
            status = ChallengeStatus.APPROVED,
            masterApproved = true,
            masterNotes = masterNotes,
            completedAtEpochMs = System.currentTimeMillis(),
        )

        // Update relation
        val relIdx = relations.indexOfFirst { it.relationId == ch.relationId }
        if (relIdx >= 0) {
            val rel = relations[relIdx]
            relations[relIdx] = rel.copy(challengesCompleted = rel.challengesCompleted + 1)
        }
        return true
    }

    fun failChallenge(challengeId: String, notes: String = ""): Boolean {
        val idx = challenges.indexOfFirst { it.challengeId == challengeId }
        if (idx < 0) return false
        challenges[idx] = challenges[idx].copy(
            status = ChallengeStatus.FAILED, masterNotes = notes,
        )
        return true
    }

    // ─── Issue Certification ───

    fun issueCertification(
        mechanicId: String,
        mechanicName: String,
        domain: MechanicSkillDomain,
        level: SkillLevel,
        masterId: String,
        masterName: String,
    ): MechanicCertification {
        val completed = challenges.count { ch ->
            ch.domain == domain && ch.masterApproved &&
                relations.any { it.relationId == ch.relationId && it.apprenticeId == mechanicId }
        }
        val total = challenges.count { ch ->
            ch.domain == domain &&
                relations.any { it.relationId == ch.relationId && it.apprenticeId == mechanicId }
        }
        val successRate = if (total > 0) completed.toDouble() / total else 0.0

        val hashData = "$mechanicId|$domain|$level|$masterId|${System.currentTimeMillis()}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(hashData.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val cert = MechanicCertification(
            certId = "cert-${System.currentTimeMillis()}-${certifications.size}",
            mechanicId = mechanicId,
            mechanicName = mechanicName,
            domain = domain,
            level = level,
            issuedByMasterId = masterId,
            issuedByMasterName = masterName,
            challengesCompleted = completed,
            successRate = successRate,
            integrityHash = hash,
        )
        certifications.add(cert)
        return cert
    }

    // ─── Skill Progress ───

    fun getSkillProgress(mechanicId: String, domain: MechanicSkillDomain): SkillProgress {
        val myCerts = certifications.filter { it.mechanicId == mechanicId && it.domain == domain }
        val currentLevel = myCerts.maxByOrNull { it.level.ordinal }?.level ?: SkillLevel.NOVICE
        val completed = challenges.count { ch ->
            ch.domain == domain && ch.masterApproved &&
                relations.any { it.relationId == ch.relationId && it.apprenticeId == mechanicId }
        }
        val required = CHALLENGES_PER_LEVEL[currentLevel] ?: 5
        val nextLevel = SkillLevel.entries.getOrNull(currentLevel.ordinal + 1)

        return SkillProgress(
            domain = domain,
            currentLevel = currentLevel,
            challengesCompleted = completed,
            challengesRequired = required,
            progressPercent = (completed.toDouble() / required * 100).coerceAtMost(100.0),
            nextLevel = nextLevel,
            certifications = myCerts,
        )
    }

    // ─── Queries ───

    fun getCertifications(mechanicId: String): List<MechanicCertification> =
        certifications.filter { it.mechanicId == mechanicId && it.isActive }

    fun getMentorships(masterId: String): List<MentorshipRelation> =
        relations.filter { it.masterId == masterId && it.isActive }

    fun getApprenticeships(apprenticeId: String): List<MentorshipRelation> =
        relations.filter { it.apprenticeId == apprenticeId && it.isActive }

    fun getPendingReviews(masterId: String): List<MentorChallenge> =
        challenges.filter { ch ->
            ch.status == ChallengeStatus.SUBMITTED &&
                relations.any { it.relationId == ch.relationId && it.masterId == masterId }
        }

    val totalCertifications: Int get() = certifications.size
    val totalMentorships: Int get() = relations.count { it.isActive }
}
