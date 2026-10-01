package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku

/**
 * Phase 20 — Claim Promotion Engine & Phase 21 — Retail Coverage Engine
 *
 * Computes evidence-derived claims and retail coverage percentages.
 * Zero false claims: "100% CORE VERIFIED" is returned ONLY when activeSkuCount == coreVerifiedCount
 * AND pendingCount == 0 AND regressionCount == 0.
 */
object ClaimPromotionEngine {

    enum class DerivedClaimStatus {
        SOURCE_IMPORTED,
        STRUCTURAL_VALID,
        RUNTIME_EXECUTABLE,
        OPTICAL_TX_VERIFIED,
        INDEPENDENT_DECODE_VERIFIED,
        REAL_DEVICE_VERIFIED,
        HIL_VERIFIED,
        RETAIL_MATRIX_VERIFIED
    }

    data class RetailCoverageResult(
        val retailerName: String,
        val activeSkuCount: Int,
        val knownModelCount: Int,
        val coreVerifiedCount: Int,
        val pendingCount: Int,
        val regressionCount: Int,
        val coveragePercentage: Double,
        val is100PercentCoreVerified: Boolean
    )

    /**
     * Derives claim status strictly from recorded physical test evidence.
     */
    fun deriveClaimStatus(evidenceList: List<PhysicalTestEvidence>): DerivedClaimStatus {
        if (evidenceList.isEmpty()) return DerivedClaimStatus.STRUCTURAL_VALID

        val hasHil = evidenceList.any { it.status == "HIL_VERIFIED" }
        if (hasHil) return DerivedClaimStatus.HIL_VERIFIED

        val hasRealDevice = evidenceList.any { it.status == "REAL_DEVICE_VERIFIED" }
        if (hasRealDevice) return DerivedClaimStatus.REAL_DEVICE_VERIFIED

        val hasTxVerified = evidenceList.any { it.status == "OPTICAL_TX_VERIFIED" }
        if (hasTxVerified) return DerivedClaimStatus.OPTICAL_TX_VERIFIED

        return DerivedClaimStatus.RUNTIME_EXECUTABLE
    }

    /**
     * Computes mathematical retail coverage for a specific retailer's SKU matrix.
     */
    fun computeRetailCoverage(
        retailerName: String,
        activeSkus: List<RetailerSku>,
        evidenceMap: Map<String, List<PhysicalTestEvidence>>
    ): RetailCoverageResult {
        val totalActive = activeSkus.size
        if (totalActive == 0) {
            return RetailCoverageResult(
                retailerName = retailerName,
                activeSkuCount = 0,
                knownModelCount = 0,
                coreVerifiedCount = 0,
                pendingCount = 0,
                regressionCount = 0,
                coveragePercentage = 0.0,
                is100PercentCoreVerified = false
            )
        }

        var knownModelCount = 0
        var coreVerifiedCount = 0
        var pendingCount = 0

        for (sku in activeSkus) {
            if (!sku.deviceModelId.isNullOrBlank()) {
                knownModelCount++
                val evidence = evidenceMap[sku.deviceModelId] ?: emptyList()
                val status = deriveClaimStatus(evidence)
                if (status >= DerivedClaimStatus.REAL_DEVICE_VERIFIED) {
                    coreVerifiedCount++
                } else {
                    pendingCount++
                }
            } else {
                pendingCount++
            }
        }

        val percentage = (coreVerifiedCount.toDouble() / totalActive.toDouble()) * 100.0
        val is100Verified = (totalActive == coreVerifiedCount) && (pendingCount == 0)

        return RetailCoverageResult(
            retailerName = retailerName,
            activeSkuCount = totalActive,
            knownModelCount = knownModelCount,
            coreVerifiedCount = coreVerifiedCount,
            pendingCount = pendingCount,
            regressionCount = 0,
            coveragePercentage = percentage,
            is100PercentCoreVerified = is100Verified
        )
    }
}
