package com.elysium369.meet.core.warranty

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  W A R R A N T Y   E N G I N E
 *  ────────────────────────────────
 *  Post-repair warranty management with full traceability.
 *
 *  "Si un mecánico certifica su trabajo, lo respalda con garantía."
 *
 *  Features:
 *  ✅ Warranty per repair/part with conditions
 *  ✅ Full traceability: mechanic + parts + date + vehicle
 *  ✅ Claim workflow with evidence (photos, DTCs, pre/post scan)
 *  ✅ Expiration tracking and notifications
 *  ✅ Voiding conditions (tampering, misuse, 3rd party work)
 *  ✅ SHA-256 integrity hash per warranty
 *  ✅ Linked to CertifiedReportEngine and VehicleHistoryTimeline
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Warranty Status ───

enum class WarrantyStatus {
    ACTIVE,         // Currently under warranty
    EXPIRED,        // Past expiration date/mileage
    CLAIMED,        // Claim submitted
    CLAIM_APPROVED, // Claim approved, repair scheduled
    CLAIM_DENIED,   // Claim denied with reason
    FULFILLED,      // Warranty repair completed
    VOIDED,         // Warranty voided (tampering, etc.)
}

// ─── Warranty Type ───

enum class WarrantyType {
    LABOR,          // Warranty on workmanship
    PART,           // Warranty on the part itself
    FULL_REPAIR,    // Both labor and part
    DIAGNOSTIC,     // Warranty on diagnosis accuracy
}

// ─── Voiding Reason ───

enum class VoidReason {
    TAMPERING,          // Vehicle tampered with
    THIRD_PARTY_WORK,   // Unauthorized 3rd party did work
    MISUSE,             // Vehicle misuse/abuse
    MILEAGE_EXCEEDED,   // Exceeded mileage limit
    MODIFICATION,       // Unauthorized modifications
    NON_COMPLIANCE,     // Did not follow maintenance schedule
    FRAUD_DETECTED,     // Fraudulent claim
}

// ─── Warranty ───

@Serializable
data class Warranty(
    val warrantyId: String,
    val vehicleId: String,
    val mechanicId: String,
    val mechanicName: String,
    val repairDescription: String,
    val type: WarrantyType,
    val status: WarrantyStatus = WarrantyStatus.ACTIVE,
    // Coverage
    val coveredParts: List<String> = emptyList(),
    val coveredLabor: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val exclusions: List<String> = emptyList(),
    // Duration
    val startEpochMs: Long = System.currentTimeMillis(),
    val expiresEpochMs: Long,
    val maxMileageKm: Long? = null,
    val currentMileageKm: Long? = null,
    // Traceability
    val relatedReportId: String? = null,
    val relatedQuoteId: String? = null,
    val repairCost: Long = 0,
    val currency: String = "CRC",
    // Integrity
    val integrityHash: String = "",
    // Claims
    val claims: List<WarrantyClaim> = emptyList(),
    val voidReason: VoidReason? = null,
    val voidedAtEpochMs: Long? = null,
) {
    val isActive: Boolean get() = status == WarrantyStatus.ACTIVE && !isExpired
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresEpochMs
    val isMileageExceeded: Boolean
        get() = maxMileageKm != null && currentMileageKm != null && currentMileageKm > maxMileageKm

    val remainingDays: Long
        get() = ((expiresEpochMs - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)

    val remainingMileageKm: Long?
        get() = if (maxMileageKm != null && currentMileageKm != null) {
            (maxMileageKm - currentMileageKm).coerceAtLeast(0)
        } else null

    val coverageSummary: String
        get() = buildString {
            append(type.displayLabel)
            append(" — ${remainingDays} días restantes")
            remainingMileageKm?.let { append(" · ${it} km restantes") }
        }
}

val WarrantyType.displayLabel: String
    get() = when (this) {
        WarrantyType.LABOR -> "Garantía de mano de obra"
        WarrantyType.PART -> "Garantía de repuesto"
        WarrantyType.FULL_REPAIR -> "Garantía completa"
        WarrantyType.DIAGNOSTIC -> "Garantía de diagnóstico"
    }

// ─── Claim ───

@Serializable
data class WarrantyClaim(
    val claimId: String,
    val warrantyId: String,
    val description: String,
    val category: ClaimCategory,
    val evidence: ClaimEvidence,
    val status: ClaimStatus = ClaimStatus.SUBMITTED,
    val resolution: String? = null,
    val submittedAtEpochMs: Long = System.currentTimeMillis(),
    val resolvedAtEpochMs: Long? = null,
)

enum class ClaimCategory {
    SAME_ISSUE_RETURNED,    // Same problem came back
    PART_DEFECTIVE,         // Replaced part failed
    WORKMANSHIP_ISSUE,      // Quality of work was poor
    MISDIAGNOSIS,           // Original diagnosis was wrong
    NEW_ISSUE_CAUSED,       // Repair caused a new problem
}

enum class ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    DENIED,
    REPAIR_SCHEDULED,
    FULFILLED,
}

@Serializable
data class ClaimEvidence(
    val dtcCodes: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val preScanReportId: String? = null,
    val postScanReportId: String? = null,
    val description: String = "",
    val currentMileageKm: Long? = null,
)

// ─── Engine ───

class WarrantyEngine {

    companion object {
        const val DEFAULT_WARRANTY_DAYS = 90L
        const val DEFAULT_WARRANTY_KM = 5000L
    }

    private val warranties = mutableMapOf<String, Warranty>()

    // ─── Create Warranty ───

    fun createWarranty(
        vehicleId: String,
        mechanicId: String,
        mechanicName: String,
        repairDescription: String,
        type: WarrantyType = WarrantyType.FULL_REPAIR,
        coveredParts: List<String> = emptyList(),
        coveredLabor: List<String> = emptyList(),
        conditions: List<String> = emptyList(),
        exclusions: List<String> = emptyList(),
        durationDays: Long = DEFAULT_WARRANTY_DAYS,
        maxMileageKm: Long? = DEFAULT_WARRANTY_KM,
        currentMileageKm: Long? = null,
        relatedReportId: String? = null,
        repairCost: Long = 0,
    ): Warranty {
        val now = System.currentTimeMillis()
        val warranty = Warranty(
            warrantyId = "wrty-${now}",
            vehicleId = vehicleId,
            mechanicId = mechanicId,
            mechanicName = mechanicName,
            repairDescription = repairDescription,
            type = type,
            coveredParts = coveredParts,
            coveredLabor = coveredLabor,
            conditions = conditions,
            exclusions = exclusions,
            expiresEpochMs = now + durationDays * 24 * 60 * 60 * 1000,
            maxMileageKm = maxMileageKm,
            currentMileageKm = currentMileageKm,
            relatedReportId = relatedReportId,
            repairCost = repairCost,
        )
        val hashed = warranty.copy(integrityHash = computeHash(warranty))
        warranties[hashed.warrantyId] = hashed
        return hashed
    }

    // ─── Submit Claim ───

    fun submitClaim(
        warrantyId: String,
        description: String,
        category: ClaimCategory,
        evidence: ClaimEvidence,
    ): WarrantyClaim? {
        val warranty = warranties[warrantyId] ?: return null
        if (!warranty.isActive) return null

        val claim = WarrantyClaim(
            claimId = "claim-${System.currentTimeMillis()}",
            warrantyId = warrantyId,
            description = description,
            category = category,
            evidence = evidence,
        )

        warranties[warrantyId] = warranty.copy(
            status = WarrantyStatus.CLAIMED,
            claims = warranty.claims + claim,
        )
        return claim
    }

    // ─── Resolve Claim ───

    fun approveClaim(warrantyId: String, claimId: String, resolution: String): Boolean {
        val warranty = warranties[warrantyId] ?: return false
        val updatedClaims = warranty.claims.map { claim ->
            if (claim.claimId == claimId) claim.copy(
                status = ClaimStatus.APPROVED,
                resolution = resolution,
                resolvedAtEpochMs = System.currentTimeMillis(),
            ) else claim
        }
        warranties[warrantyId] = warranty.copy(
            status = WarrantyStatus.CLAIM_APPROVED,
            claims = updatedClaims,
        )
        return true
    }

    fun denyClaim(warrantyId: String, claimId: String, reason: String): Boolean {
        val warranty = warranties[warrantyId] ?: return false
        val updatedClaims = warranty.claims.map { claim ->
            if (claim.claimId == claimId) claim.copy(
                status = ClaimStatus.DENIED,
                resolution = reason,
                resolvedAtEpochMs = System.currentTimeMillis(),
            ) else claim
        }
        warranties[warrantyId] = warranty.copy(
            status = WarrantyStatus.CLAIM_DENIED,
            claims = updatedClaims,
        )
        return true
    }

    // ─── Void Warranty ───

    fun voidWarranty(warrantyId: String, reason: VoidReason): Boolean {
        val warranty = warranties[warrantyId] ?: return false
        if (warranty.status == WarrantyStatus.VOIDED) return false
        warranties[warrantyId] = warranty.copy(
            status = WarrantyStatus.VOIDED,
            voidReason = reason,
            voidedAtEpochMs = System.currentTimeMillis(),
        )
        return true
    }

    // ─── Update Mileage ───

    fun updateMileage(warrantyId: String, currentKm: Long): Boolean {
        val warranty = warranties[warrantyId] ?: return false
        warranties[warrantyId] = warranty.copy(currentMileageKm = currentKm)
        return true
    }

    // ─── Queries ───

    fun getWarranty(id: String): Warranty? = warranties[id]

    fun getActiveWarranties(vehicleId: String): List<Warranty> =
        warranties.values.filter { it.vehicleId == vehicleId && it.isActive }

    fun getWarrantiesByMechanic(mechanicId: String): List<Warranty> =
        warranties.values.filter { it.mechanicId == mechanicId }

    fun getExpiringWarranties(vehicleId: String, withinDays: Long = 30): List<Warranty> =
        getActiveWarranties(vehicleId).filter { it.remainingDays <= withinDays }

    val totalWarranties: Int get() = warranties.size
    val totalActiveClaims: Int get() = warranties.values.sumOf {
        it.claims.count { c -> c.status in listOf(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW) }
    }

    private fun computeHash(w: Warranty): String {
        val data = "${w.warrantyId}|${w.vehicleId}|${w.mechanicId}|${w.repairDescription}|${w.startEpochMs}"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
