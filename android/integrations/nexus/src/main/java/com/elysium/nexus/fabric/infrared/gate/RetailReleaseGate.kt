package com.elysium.nexus.fabric.infrared.gate

import com.elysium.nexus.fabric.infrared.CodecVerificationStatus

/**
 * Phase 1 — Retail Stop-The-Line Gate
 *
 * Enforces zero-tolerance validation for commercial builds (v0.7 Retail Truth).
 * A commercial release or claim CANNOT exist while any P0 gate check fails.
 */
object RetailReleaseGate {

    sealed class GateResult {
        object Pass : GateResult()
        data class Fail(val reason: String, val code: String) : GateResult()
    }

    /**
     * Verifies that brand search queries enforce explicit device type (e.g. "TV")
     * and do not allow empty deviceType filtering.
     */
    fun checkBrandQuery(deviceType: String?): GateResult {
        if (deviceType.isNullOrBlank()) {
            return GateResult.Fail(
                reason = "Brand search query passed an empty deviceType. Must explicitly filter by 'TV'.",
                code = "ERR_GATE_BRAND_QUERY_UNFILTERED"
            )
        }
        return GateResult.Pass
    }

    /**
     * Verifies that experimental codecs (RC5, RC6, Kaseikyo) are restricted to LAB_ONLY
     * and blocked from commercial candidate routes.
     */
    fun checkCodecEligibility(status: CodecVerificationStatus): GateResult {
        if (status == CodecVerificationStatus.EXPERIMENTAL) {
            return GateResult.Fail(
                reason = "Codec status is EXPERIMENTAL. Blocked from commercial runtime lookup.",
                code = "ERR_GATE_CODEC_EXPERIMENTAL_BLOCKED"
            )
        }
        return GateResult.Pass
    }

    /**
     * Verifies that commercial claims (e.g. "100% CORE VERIFIED") have physical evidence backing.
     */
    fun checkCommercialClaimEligibility(physicalEvidenceCount: Int): GateResult {
        if (physicalEvidenceCount <= 0) {
            return GateResult.Fail(
                reason = "Physical evidence count is 0. Commercial retail claim is blocked.",
                code = "ERR_GATE_ZERO_PHYSICAL_EVIDENCE"
            )
        }
        return GateResult.Pass
    }

    /**
     * Verifies that release signing credentials are not using hardcoded fallbacks.
     */
    fun checkReleaseSigningCredentials(storePass: String?, keyPass: String?): GateResult {
        if (storePass.isNullOrBlank() || keyPass.isNullOrBlank()) {
            return GateResult.Fail(
                reason = "Release signing credentials missing from environment. Hardcoded fallbacks forbidden.",
                code = "ERR_GATE_MISSING_RELEASE_CREDENTIALS"
            )
        }
        if (storePass == "Elysium2026!" || keyPass == "Elysium2026!") {
            return GateResult.Fail(
                reason = "Hardcoded fallback password detected in release signing configuration.",
                code = "ERR_GATE_HARDCODED_RELEASE_PASSWORD"
            )
        }
        return GateResult.Pass
    }
}
