package com.elysium369.meet.core.apprenticeship

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import com.elysium369.meet.education.economic.SkillCredentialState
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  A P P R E N T I C E S H I P   P R O T O C O L
 *  ──────────────────────────────────────────────────
 *  The ancient guild system, digitized.
 *
 *  "Connect me with a master plumber who will let me watch and learn."
 *
 *  Learning from a master, not just from content. Real transfer of
 *  tacit knowledge that cannot be captured in videos or textbooks.
 *  The apprentice earns trust through the master's endorsement.
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Apprenticeship Relationship ───

enum class ApprenticeshipStatus {
    REQUESTED,      // Apprentice requested, awaiting master acceptance
    ACTIVE,         // Master accepted, learning in progress
    ON_HOLD,        // Temporarily paused
    GRADUATED,      // Master confirmed competence
    WITHDRAWN,      // One party left
    EXPIRED,        // Time limit exceeded without graduation
}

@Serializable
data class Apprenticeship(
    val id: String,
    val masterId: String,
    val masterName: String,
    val apprenticeId: String,
    val apprenticeName: String,
    val domain: UniversalServiceDomain,
    val skillFocus: String,
    val status: ApprenticeshipStatus = ApprenticeshipStatus.REQUESTED,
    val startedAtEpochMs: Long? = null,
    val graduatedAtEpochMs: Long? = null,
    val milestones: List<ApprenticeshipMilestone> = emptyList(),
    val masterEndorsement: String? = null,
    val maxDurationDays: Int = 180,
) {
    val isActive: Boolean get() = status == ApprenticeshipStatus.ACTIVE
    val isGraduated: Boolean get() = status == ApprenticeshipStatus.GRADUATED
    val completedMilestones: Int get() = milestones.count { it.completed }
    val totalMilestones: Int get() = milestones.size
    val progressPercent: Int
        get() = if (totalMilestones == 0) 0
        else ((completedMilestones.toDouble() / totalMilestones) * 100).toInt()
}

@Serializable
data class ApprenticeshipMilestone(
    val id: String,
    val description: String,
    val order: Int,
    val completed: Boolean = false,
    val masterVerified: Boolean = false,
    val completedAtEpochMs: Long? = null,
    val evidenceRef: String? = null,
)

// ─── Master Profile ───

@Serializable
data class MasterProfile(
    val userId: String,
    val displayName: String,
    val domain: UniversalServiceDomain,
    val yearsOfExperience: Int,
    val credentialState: SkillCredentialState,
    val maxApprentices: Int = 3,
    val currentApprenticeCount: Int = 0,
    val totalGraduated: Int = 0,
    val acceptingApprentices: Boolean = true,
    val teachingStyle: String = "",
    val requirements: String = "",
) {
    val hasCapacity: Boolean
        get() = currentApprenticeCount < maxApprentices && acceptingApprentices

    /** Masters must be at least DEMONSTRATED to teach */
    val isQualifiedToTeach: Boolean
        get() = credentialState.ordinal >= SkillCredentialState.DEMONSTRATED.ordinal
}

// ─── Apprenticeship Engine ───

class ApprenticeshipEngine {

    private val apprenticeships = mutableListOf<Apprenticeship>()
    private val masters = mutableListOf<MasterProfile>()

    fun registerMaster(profile: MasterProfile): Boolean {
        if (!profile.isQualifiedToTeach) return false
        masters.add(profile)
        return true
    }

    /**
     * Request an apprenticeship. The master must accept.
     */
    fun requestApprenticeship(
        apprenticeId: String,
        apprenticeName: String,
        masterId: String,
        domain: UniversalServiceDomain,
        skillFocus: String,
        milestones: List<String>,
    ): Apprenticeship? {
        val master = masters.firstOrNull { it.userId == masterId } ?: return null
        if (!master.hasCapacity) return null
        if (!master.isQualifiedToTeach) return null

        val apprenticeship = Apprenticeship(
            id = "apprentice-${System.currentTimeMillis()}",
            masterId = masterId,
            masterName = master.displayName,
            apprenticeId = apprenticeId,
            apprenticeName = apprenticeName,
            domain = domain,
            skillFocus = skillFocus,
            milestones = milestones.mapIndexed { i, desc ->
                ApprenticeshipMilestone(
                    id = "ms-$i",
                    description = desc,
                    order = i + 1,
                )
            },
        )

        apprenticeships.add(apprenticeship)
        return apprenticeship
    }

    /**
     * Master accepts an apprenticeship request.
     */
    fun acceptApprenticeship(apprenticeshipId: String, masterId: String): Boolean {
        val idx = apprenticeships.indexOfFirst {
            it.id == apprenticeshipId && it.masterId == masterId
        }
        if (idx < 0) return false

        apprenticeships[idx] = apprenticeships[idx].copy(
            status = ApprenticeshipStatus.ACTIVE,
            startedAtEpochMs = System.currentTimeMillis(),
        )
        return true
    }

    /**
     * Master verifies a milestone completion.
     */
    fun completeMilestone(
        apprenticeshipId: String,
        milestoneId: String,
        evidenceRef: String,
    ): Boolean {
        val idx = apprenticeships.indexOfFirst { it.id == apprenticeshipId }
        if (idx < 0) return false

        val current = apprenticeships[idx]
        if (!current.isActive) return false

        val updatedMilestones = current.milestones.map { ms ->
            if (ms.id == milestoneId) ms.copy(
                completed = true,
                masterVerified = true,
                completedAtEpochMs = System.currentTimeMillis(),
                evidenceRef = evidenceRef,
            ) else ms
        }

        apprenticeships[idx] = current.copy(milestones = updatedMilestones)
        return true
    }

    /**
     * Master graduates an apprentice.
     * This is the highest form of trust transfer:
     * "I trained this person, and they are competent."
     */
    fun graduate(
        apprenticeshipId: String,
        endorsement: String,
    ): Apprenticeship? {
        val idx = apprenticeships.indexOfFirst { it.id == apprenticeshipId }
        if (idx < 0) return null

        val current = apprenticeships[idx]
        if (!current.isActive) return null

        // Must have completed at least 70% of milestones
        if (current.progressPercent < 70) return null

        val graduated = current.copy(
            status = ApprenticeshipStatus.GRADUATED,
            graduatedAtEpochMs = System.currentTimeMillis(),
            masterEndorsement = endorsement,
        )

        apprenticeships[idx] = graduated
        return graduated
    }

    fun findMasters(domain: UniversalServiceDomain): List<MasterProfile> {
        return masters.filter { it.domain == domain && it.hasCapacity }
    }

    fun apprenticeshipsFor(userId: String): List<Apprenticeship> {
        return apprenticeships.filter {
            it.apprenticeId == userId || it.masterId == userId
        }
    }

    val activeMasters: List<MasterProfile> get() = masters.filter { it.acceptingApprentices }
    val totalApprenticeships: Int get() = apprenticeships.size
}
