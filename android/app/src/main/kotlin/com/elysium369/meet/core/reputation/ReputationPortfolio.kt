package com.elysium369.meet.core.reputation

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * ══════════════════════════════════════════════════════════════════════
 *  R E P U T A T I O N   P O R T F O L I O
 *  ──────────────────────────────────────────
 *  Your professional resume — verified, portable, yours forever.
 *
 *  Connects: SovereignIdentity + EconomicPassport + TrustGraph +
 *  CollectiveIntelligence into ONE verifiable document.
 *
 *  A mechanic can show:
 *  "50 jobs verified, trust 0.9, 3 masters endorse me,
 *   certified in brakes, average customer rating 4.8/5"
 *
 *  — All verifiable offline with a QR code.
 *  — Works OUTSIDE of ELYSIUM.
 *  — No platform dependency.
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
data class ReputationPortfolio(
    val portfolioId: String,
    val holderDid: String,
    val displayName: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val domains: List<DomainReputation> = emptyList(),
    val highlights: List<PortfolioHighlight> = emptyList(),
    val endorsements: List<Endorsement> = emptyList(),
    val certifications: List<PortfolioCertification> = emptyList(),
    val overallTrustScore: Double = 0.0,
    val totalVerifiedJobs: Int = 0,
    val totalYearsExperience: Int = 0,
    val integrityHash: String = "",
    val isPublic: Boolean = false,
) {
    val domainCount: Int get() = domains.size
    val endorsementCount: Int get() = endorsements.size
}

@Serializable
data class DomainReputation(
    val domain: UniversalServiceDomain,
    val verifiedJobs: Int,
    val averageRating: Double,
    val trustScore: Double,
    val specializations: List<String>,
    val yearsActive: Int,
    val repeatCustomerRate: Double,
)

@Serializable
data class PortfolioHighlight(
    val title: String,
    val description: String,
    val category: HighlightCategory,
    val evidenceHash: String = "",
    val dateEpochMs: Long = System.currentTimeMillis(),
)

enum class HighlightCategory {
    MILESTONE,          // "50th verified job"
    ACHIEVEMENT,        // "Zero complaints in 100 jobs"
    APPRENTICESHIP,     // "Graduated under Master Pedro"
    COMMUNITY_SERVICE,  // "20 hours hurricane relief"
    CERTIFICATION,      // "OBD-II Level 2"
    CUSTOMER_PRAISE,    // "Outstanding service" (anonymized)
}

@Serializable
data class Endorsement(
    val endorserId: String,
    val endorserName: String,
    val endorserRole: String,
    val domain: UniversalServiceDomain,
    val statement: String,
    val relationship: EndorsementRelationship,
    val signatureHash: String = "",
    val dateEpochMs: Long = System.currentTimeMillis(),
)

enum class EndorsementRelationship {
    MASTER,             // "I trained this person"
    PEER,               // "We work in the same field"
    CUSTOMER,           // "They did excellent work for me"
    COMMUNITY_LEADER,   // "I vouch for their character"
}

@Serializable
data class PortfolioCertification(
    val name: String,
    val issuedBy: String,
    val domain: UniversalServiceDomain,
    val isVerified: Boolean,
    val dateEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long? = null,
) {
    val isExpired: Boolean
        get() = expiresAtEpochMs != null && System.currentTimeMillis() > expiresAtEpochMs

    val isActive: Boolean get() = isVerified && !isExpired
}

// ─── Portfolio Engine ───

class ReputationPortfolioEngine {

    private val portfolios = mutableMapOf<String, ReputationPortfolio>()

    /**
     * Builds a portfolio from a user's accumulated data.
     */
    fun buildPortfolio(
        holderDid: String,
        displayName: String,
        domains: List<DomainReputation>,
        highlights: List<PortfolioHighlight> = emptyList(),
        endorsements: List<Endorsement> = emptyList(),
        certifications: List<PortfolioCertification> = emptyList(),
    ): ReputationPortfolio {
        val totalJobs = domains.sumOf { it.verifiedJobs }
        val avgTrust = if (domains.isEmpty()) 0.0
            else domains.map { it.trustScore }.average()
        val totalYears = domains.maxOfOrNull { it.yearsActive } ?: 0

        val portfolio = ReputationPortfolio(
            portfolioId = "portfolio-${System.currentTimeMillis()}",
            holderDid = holderDid,
            displayName = displayName,
            domains = domains,
            highlights = highlights,
            endorsements = endorsements,
            certifications = certifications,
            overallTrustScore = avgTrust,
            totalVerifiedJobs = totalJobs,
            totalYearsExperience = totalYears,
        )

        val withHash = portfolio.copy(
            integrityHash = computeHash(portfolio),
        )
        portfolios[withHash.portfolioId] = withHash
        return withHash
    }

    /**
     * Generates a minimal QR for portfolio verification.
     */
    fun generateVerificationQr(portfolioId: String): String? {
        val p = portfolios[portfolioId] ?: return null
        return "PORTFOLIO|${p.portfolioId}|${p.integrityHash}|${p.totalVerifiedJobs}|${p.overallTrustScore}"
    }

    /**
     * Verifies a portfolio's integrity offline.
     */
    fun verifyIntegrity(portfolio: ReputationPortfolio): Boolean {
        return portfolio.integrityHash == computeHash(portfolio.copy(integrityHash = ""))
    }

    /**
     * Creates a filtered view for sharing — selective disclosure.
     * "Show my plumbing credentials but not my electrical ones."
     */
    fun filterByDomain(
        portfolioId: String,
        domain: UniversalServiceDomain,
    ): ReputationPortfolio? {
        val p = portfolios[portfolioId] ?: return null
        return p.copy(
            domains = p.domains.filter { it.domain == domain },
            endorsements = p.endorsements.filter { it.domain == domain },
            certifications = p.certifications.filter { it.domain == domain },
        )
    }

    /**
     * Generates a human-readable summary.
     */
    fun generateSummary(portfolioId: String): String {
        val p = portfolios[portfolioId] ?: return "Portfolio no encontrado"
        return buildString {
            appendLine("═══ PORTAFOLIO PROFESIONAL ═══")
            appendLine("${p.displayName}")
            appendLine("Trabajos verificados: ${p.totalVerifiedJobs}")
            appendLine("Confianza: ${String.format("%.1f", p.overallTrustScore * 100)}%")
            appendLine("Años de experiencia: ${p.totalYearsExperience}")
            appendLine("Dominios: ${p.domains.joinToString { it.domain.name }}")
            appendLine("Endosos: ${p.endorsementCount}")
            appendLine("Certificaciones activas: ${p.certifications.count { it.isActive }}")
            appendLine("Hash: ${p.integrityHash.take(16)}…")
            appendLine("═══════════════════════════════")
        }
    }

    fun getPortfolio(id: String): ReputationPortfolio? = portfolios[id]

    val totalPortfolios: Int get() = portfolios.size

    private fun computeHash(portfolio: ReputationPortfolio): String {
        val content = buildString {
            append(portfolio.holderDid)
            append(portfolio.displayName)
            append(portfolio.totalVerifiedJobs)
            portfolio.domains.forEach { d -> append("${d.domain}:${d.verifiedJobs}:${d.trustScore}") }
            portfolio.endorsements.forEach { e -> append("${e.endorserId}:${e.statement}") }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }
}
