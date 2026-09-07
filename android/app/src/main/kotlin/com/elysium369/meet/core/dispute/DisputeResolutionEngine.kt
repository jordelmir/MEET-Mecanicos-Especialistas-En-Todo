package com.elysium369.meet.core.dispute

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  D I S P U T E   R E S O L U T I O N   E N G I N E
 *  ────────────────────────────────────────────────────
 *  Evidence-based justice. Never "he said / she said."
 *
 *  "Los datos hablan. El pre-scan muestra P0301 activo.
 *   El post-scan muestra P0301 cleared. El mecánico cumplió."
 *
 *  Evidence hierarchy:
 *  1. Certified Pre/Post Scan reports (highest weight)
 *  2. Signed quotes with SHA-256 hash
 *  3. Timestamped photos
 *  4. GPS forensic trail (was the mechanic there?)
 *  5. Warranty terms
 *  6. Chat/message history
 *  7. Testimonies (lowest weight)
 *
 *  Principles:
 *  - Protects the HONEST mechanic AND the honest customer
 *  - Never favors either party without evidence
 *  - Transparent scoring — both parties see the evidence weight
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Dispute Status ───

enum class DisputeStatus {
    OPENED,           // Customer or mechanic initiated
    EVIDENCE_PHASE,   // Both parties submitting evidence
    UNDER_REVIEW,     // Auto-arbiter analyzing
    MEDIATION,        // Human mediator involved
    RESOLVED_CUSTOMER, // Resolved in customer's favor
    RESOLVED_MECHANIC, // Resolved in mechanic's favor
    RESOLVED_PARTIAL,  // Compromise
    CLOSED,           // Final, no further action
    ESCALATED,        // Escalated to external authority
}

// ─── Dispute Category ───

enum class DisputeCategory {
    WORK_NOT_PERFORMED,     // Paid but work wasn't done
    WORK_QUALITY_POOR,      // Work done but poorly
    OVERCHARGED,            // Billed more than quoted
    MISDIAGNOSIS,           // Wrong diagnosis, wrong repair
    PART_WRONG_OR_USED,     // Wrong part or used part sold as new
    WARRANTY_DENIED_UNFAIR, // Warranty claim unfairly denied
    DAMAGE_CAUSED,          // Mechanic caused new damage
    PAYMENT_NOT_RECEIVED,   // Customer didn't pay
    NO_SHOW,                // One party didn't show up
    OTHER,
}

// ─── Evidence ───

@Serializable
data class DisputeEvidence(
    val evidenceId: String,
    val submittedBy: DisputeParty,
    val type: EvidenceType,
    val title: String,
    val description: String,
    val weight: Double,            // 0.0-1.0 auto-calculated
    val referenceId: String? = null, // Report ID, quote ID, etc.
    val dataHash: String? = null,
    val photoUrls: List<String> = emptyList(),
    val submittedAtEpochMs: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false,
)

enum class EvidenceType {
    CERTIFIED_PRE_SCAN,     // Weight: 0.95
    CERTIFIED_POST_SCAN,    // Weight: 0.95
    SIGNED_QUOTE,           // Weight: 0.85
    TIMESTAMPED_PHOTOS,     // Weight: 0.70
    GPS_FORENSIC_TRAIL,     // Weight: 0.75
    WARRANTY_DOCUMENT,      // Weight: 0.80
    CHAT_HISTORY,           // Weight: 0.50
    PAYMENT_RECEIPT,        // Weight: 0.85
    THIRD_PARTY_INSPECTION, // Weight: 0.90
    TESTIMONY,              // Weight: 0.30
}

val EvidenceType.defaultWeight: Double
    get() = when (this) {
        EvidenceType.CERTIFIED_PRE_SCAN -> 0.95
        EvidenceType.CERTIFIED_POST_SCAN -> 0.95
        EvidenceType.SIGNED_QUOTE -> 0.85
        EvidenceType.TIMESTAMPED_PHOTOS -> 0.70
        EvidenceType.GPS_FORENSIC_TRAIL -> 0.75
        EvidenceType.WARRANTY_DOCUMENT -> 0.80
        EvidenceType.CHAT_HISTORY -> 0.50
        EvidenceType.PAYMENT_RECEIPT -> 0.85
        EvidenceType.THIRD_PARTY_INSPECTION -> 0.90
        EvidenceType.TESTIMONY -> 0.30
    }

enum class DisputeParty {
    CUSTOMER,
    MECHANIC,
    PLATFORM,
    THIRD_PARTY,
}

// ─── Dispute ───

@Serializable
data class Dispute(
    val disputeId: String,
    val rideOrRepairId: String,
    val category: DisputeCategory,
    val status: DisputeStatus = DisputeStatus.OPENED,
    val customerId: String,
    val customerName: String,
    val mechanicId: String,
    val mechanicName: String,
    val vehicleId: String,
    val disputeAmount: Long = 0,
    val currency: String = "CRC",
    val description: String,
    val evidence: List<DisputeEvidence> = emptyList(),
    val customerEvidenceScore: Double = 0.0,
    val mechanicEvidenceScore: Double = 0.0,
    val resolution: DisputeResolution? = null,
    val openedAtEpochMs: Long = System.currentTimeMillis(),
    val resolvedAtEpochMs: Long? = null,
    val integrityHash: String = "",
) {
    val isOpen: Boolean get() = status in listOf(
        DisputeStatus.OPENED, DisputeStatus.EVIDENCE_PHASE,
        DisputeStatus.UNDER_REVIEW, DisputeStatus.MEDIATION,
    )
    val isResolved: Boolean get() = status in listOf(
        DisputeStatus.RESOLVED_CUSTOMER, DisputeStatus.RESOLVED_MECHANIC,
        DisputeStatus.RESOLVED_PARTIAL, DisputeStatus.CLOSED,
    )
    val totalEvidence: Int get() = evidence.size
    val customerEvidence: List<DisputeEvidence> get() = evidence.filter { it.submittedBy == DisputeParty.CUSTOMER }
    val mechanicEvidence: List<DisputeEvidence> get() = evidence.filter { it.submittedBy == DisputeParty.MECHANIC }
}

// ─── Resolution ───

@Serializable
data class DisputeResolution(
    val resolvedBy: DisputeParty,
    val outcome: String,
    val refundAmount: Long = 0,
    val warrantyExtensionDays: Int = 0,
    val freeRepairGranted: Boolean = false,
    val details: String,
    val evidenceSummary: String,
)

// ─── Engine ───

class DisputeResolutionEngine {

    private val disputes = mutableMapOf<String, Dispute>()

    // ─── Open Dispute ───

    fun openDispute(
        rideOrRepairId: String,
        category: DisputeCategory,
        customerId: String,
        customerName: String,
        mechanicId: String,
        mechanicName: String,
        vehicleId: String,
        disputeAmount: Long = 0,
        description: String,
    ): Dispute {
        val dispute = Dispute(
            disputeId = "disp-${System.currentTimeMillis()}",
            rideOrRepairId = rideOrRepairId,
            category = category,
            customerId = customerId,
            customerName = customerName,
            mechanicId = mechanicId,
            mechanicName = mechanicName,
            vehicleId = vehicleId,
            disputeAmount = disputeAmount,
            description = description,
            status = DisputeStatus.EVIDENCE_PHASE,
        )
        val hashed = dispute.copy(integrityHash = computeHash(dispute))
        disputes[hashed.disputeId] = hashed
        return hashed
    }

    // ─── Submit Evidence ───

    fun submitEvidence(
        disputeId: String,
        submittedBy: DisputeParty,
        type: EvidenceType,
        title: String,
        description: String,
        referenceId: String? = null,
        photoUrls: List<String> = emptyList(),
    ): DisputeEvidence? {
        val dispute = disputes[disputeId] ?: return null
        if (!dispute.isOpen) return null

        val evidence = DisputeEvidence(
            evidenceId = "evd-${System.currentTimeMillis()}-${dispute.totalEvidence}",
            submittedBy = submittedBy,
            type = type,
            title = title,
            description = description,
            weight = type.defaultWeight,
            referenceId = referenceId,
            photoUrls = photoUrls,
            isVerified = type in listOf(
                EvidenceType.CERTIFIED_PRE_SCAN, EvidenceType.CERTIFIED_POST_SCAN,
                EvidenceType.PAYMENT_RECEIPT,
            ),
        )

        val updated = dispute.copy(evidence = dispute.evidence + evidence)
        val scored = recalculateScores(updated)
        disputes[disputeId] = scored
        return evidence
    }

    // ─── Auto-Arbitrate ───

    fun autoArbitrate(disputeId: String): DisputeResolution? {
        val dispute = disputes[disputeId] ?: return null
        if (dispute.evidence.isEmpty()) return null

        val scored = recalculateScores(dispute)
        val custScore = scored.customerEvidenceScore
        val mechScore = scored.mechanicEvidenceScore
        val diff = custScore - mechScore

        val (status, outcome, details) = when {
            diff > 0.3 -> Triple(
                DisputeStatus.RESOLVED_CUSTOMER,
                "A favor del cliente",
                "Evidencia del cliente (${formatPercent(custScore)}) supera significativamente la del mecánico (${formatPercent(mechScore)}).",
            )
            diff < -0.3 -> Triple(
                DisputeStatus.RESOLVED_MECHANIC,
                "A favor del mecánico",
                "Evidencia del mecánico (${formatPercent(mechScore)}) supera significativamente la del cliente (${formatPercent(custScore)}).",
            )
            else -> Triple(
                DisputeStatus.RESOLVED_PARTIAL,
                "Resolución parcial — compromiso recomendado",
                "Evidencia equilibrada. Cliente: ${formatPercent(custScore)}, Mecánico: ${formatPercent(mechScore)}. Se recomienda mediación.",
            )
        }

        val refund = when (status) {
            DisputeStatus.RESOLVED_CUSTOMER -> dispute.disputeAmount
            DisputeStatus.RESOLVED_PARTIAL -> dispute.disputeAmount / 2
            else -> 0
        }

        val resolution = DisputeResolution(
            resolvedBy = DisputeParty.PLATFORM,
            outcome = outcome,
            refundAmount = refund,
            warrantyExtensionDays = if (status == DisputeStatus.RESOLVED_CUSTOMER) 30 else 0,
            freeRepairGranted = status == DisputeStatus.RESOLVED_CUSTOMER &&
                dispute.category in listOf(DisputeCategory.WORK_QUALITY_POOR, DisputeCategory.MISDIAGNOSIS),
            details = details,
            evidenceSummary = buildEvidenceSummary(scored),
        )

        disputes[disputeId] = scored.copy(
            status = status,
            resolution = resolution,
            resolvedAtEpochMs = System.currentTimeMillis(),
        )
        return resolution
    }

    // ─── Escalate ───

    fun escalate(disputeId: String): Boolean {
        val dispute = disputes[disputeId] ?: return false
        if (!dispute.isOpen) return false
        disputes[disputeId] = dispute.copy(status = DisputeStatus.ESCALATED)
        return true
    }

    // ─── Queries ───

    fun getDispute(id: String): Dispute? = disputes[id]

    fun getOpenDisputes(userId: String): List<Dispute> =
        disputes.values.filter {
            it.isOpen && (it.customerId == userId || it.mechanicId == userId)
        }

    fun getDisputeHistory(userId: String): List<Dispute> =
        disputes.values.filter {
            it.customerId == userId || it.mechanicId == userId
        }.sortedByDescending { it.openedAtEpochMs }

    val totalDisputes: Int get() = disputes.size
    val openDisputeCount: Int get() = disputes.count { it.value.isOpen }

    // ─── Internal ───

    private fun recalculateScores(dispute: Dispute): Dispute {
        val custEvidence = dispute.evidence.filter { it.submittedBy == DisputeParty.CUSTOMER }
        val mechEvidence = dispute.evidence.filter { it.submittedBy == DisputeParty.MECHANIC }

        val custScore = if (custEvidence.isNotEmpty()) {
            custEvidence.sumOf { it.weight } / custEvidence.size
        } else 0.0

        val mechScore = if (mechEvidence.isNotEmpty()) {
            mechEvidence.sumOf { it.weight } / mechEvidence.size
        } else 0.0

        return dispute.copy(
            customerEvidenceScore = custScore,
            mechanicEvidenceScore = mechScore,
        )
    }

    private fun buildEvidenceSummary(dispute: Dispute): String = buildString {
        appendLine("Resumen de Evidencia:")
        dispute.evidence.groupBy { it.submittedBy }.forEach { (party, items) ->
            appendLine("  ${party.name}: ${items.size} pieza(s)")
            items.forEach { e ->
                appendLine("    · ${e.title} (peso: ${formatPercent(e.weight)})")
            }
        }
    }

    private fun formatPercent(value: Double) = "${(value * 100).toInt()}%"

    private fun computeHash(d: Dispute): String {
        val data = "${d.disputeId}|${d.customerId}|${d.mechanicId}|${d.category}|${d.openedAtEpochMs}"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
