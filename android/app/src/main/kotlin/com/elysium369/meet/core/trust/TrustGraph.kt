package com.elysium369.meet.core.trust

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  T R U S T   G R A P H
 *  ──────────────────────
 *  Peer-to-peer trust network that kills intermediaries.
 *
 *  "María verified José's plumbing work → Carlos trusts José
 *   because Carlos trusts María."
 *
 *  Trust is EARNED through verified outcomes, never purchased.
 *  Trust is TRANSITIVE but decays with distance.
 *  Trust is DOMAIN-SPECIFIC: trusting someone as a plumber
 *  doesn't mean trusting them as an electrician.
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Trust Edge ───

@Serializable
data class TrustEdge(
    val fromUserId: String,
    val toUserId: String,
    val domain: String,
    val trustLevel: TrustLevel,
    val reason: TrustReason,
    val evidenceRef: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long? = null,
) {
    val isActive: Boolean
        get() = expiresAtEpochMs == null || System.currentTimeMillis() < expiresAtEpochMs
}

enum class TrustLevel(val weight: Double) {
    VOUCHED(0.3),           // "I know this person"
    WITNESSED(0.5),         // "I saw them work"
    VERIFIED_OUTCOME(0.8),  // "They did good work for me"
    MASTER_APPRENTICE(0.9), // "I trained them"
    CERTIFIED(1.0),         // Verified by multiple independent sources
}

enum class TrustReason {
    PERSONAL_KNOWLEDGE,     // I know them personally
    WITNESSED_WORK,         // I watched them work
    CUSTOMER_OF,            // They did work for me
    TRAINED_BY,             // They were my apprentice
    TRAINED_WITH,           // We trained together
    COMMUNITY_LEADER,       // Community leader vouches
    REPEAT_CUSTOMER,        // Multiple jobs, always satisfied
    PEER_REVIEW,            // Fellow professional confirms competence
}

// ─── Trust Score ───

@Serializable
data class TrustScore(
    val userId: String,
    val domain: String,
    val directTrust: Double = 0.0,
    val transitiveTrust: Double = 0.0,
    val totalVerifications: Int = 0,
    val uniqueVouchers: Int = 0,
) {
    /** Combined trust score (direct weighs more than transitive) */
    val composite: Double
        get() = (directTrust * 0.7 + transitiveTrust * 0.3).coerceIn(0.0, 1.0)

    val humanReadable: String
        get() = when {
            composite >= 0.8 -> "Confianza alta — verificado por múltiples personas"
            composite >= 0.5 -> "Confianza moderada — respaldado por la comunidad"
            composite >= 0.2 -> "Confianza inicial — pocas verificaciones"
            else -> "Sin historial de confianza"
        }
}

// ─── Trust Graph Engine ───

class TrustGraph {

    private val edges = mutableListOf<TrustEdge>()

    fun addTrust(edge: TrustEdge) {
        // Cannot trust yourself
        require(edge.fromUserId != edge.toUserId) { "Cannot create self-trust" }
        edges.add(edge)
    }

    /**
     * Computes direct trust: how much do people who DIRECTLY
     * interacted with this user trust them?
     */
    fun directTrustFor(userId: String, domain: String): Double {
        val directEdges = edges.filter {
            it.toUserId == userId && it.domain == domain && it.isActive
        }
        if (directEdges.isEmpty()) return 0.0

        val weightedSum = directEdges.sumOf { it.trustLevel.weight }
        return (weightedSum / directEdges.size).coerceIn(0.0, 1.0)
    }

    /**
     * Computes transitive trust (depth 2):
     * If A trusts B, and B trusts C, then A has transitive trust in C.
     * Transitive trust decays by 50% per hop.
     */
    fun transitiveTrustFor(userId: String, domain: String): Double {
        val directTrusters = edges.filter {
            it.toUserId == userId && it.domain == domain && it.isActive
        }.map { it.fromUserId }

        if (directTrusters.isEmpty()) return 0.0

        // Find who trusts the direct trusters (depth 2)
        val transitiveEdges = edges.filter {
            it.toUserId in directTrusters && it.domain == domain && it.isActive
        }

        if (transitiveEdges.isEmpty()) return 0.0

        val decayFactor = 0.5
        val transitiveScore = transitiveEdges.sumOf { it.trustLevel.weight * decayFactor }
        return (transitiveScore / transitiveEdges.size).coerceIn(0.0, 1.0)
    }

    /**
     * Full trust score for a user in a domain.
     */
    fun trustScoreFor(userId: String, domain: String): TrustScore {
        val direct = directTrustFor(userId, domain)
        val transitive = transitiveTrustFor(userId, domain)
        val directEdges = edges.filter {
            it.toUserId == userId && it.domain == domain && it.isActive
        }

        return TrustScore(
            userId = userId,
            domain = domain,
            directTrust = direct,
            transitiveTrust = transitive,
            totalVerifications = directEdges.size,
            uniqueVouchers = directEdges.map { it.fromUserId }.distinct().size,
        )
    }

    /**
     * Who trusts this person? Returns the trust network.
     */
    fun vouchersFor(userId: String, domain: String): List<TrustEdge> {
        return edges.filter {
            it.toUserId == userId && it.domain == domain && it.isActive
        }
    }

    /**
     * Who does this person trust?
     */
    fun trustedBy(userId: String, domain: String): List<TrustEdge> {
        return edges.filter {
            it.fromUserId == userId && it.domain == domain && it.isActive
        }
    }

    val totalEdges: Int get() = edges.size
}
