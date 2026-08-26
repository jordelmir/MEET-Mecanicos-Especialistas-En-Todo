package com.elysium369.meet.core.humanity.vision

import com.elysium369.meet.core.humanity.TruthState

enum class VisualVerificationStatus {
    IDENTIFICATION_NOT_VERIFIED,
    PROBABLE_CANDIDATE,
    OEM_VERIFIED_EXACT,
}

data class VisualDetectionCandidate(
    val detectedLabel: String,
    val modelConfidencePct: Int,
    val boundingBox: String,
    val visualFeaturesNote: String,
)

data class VisualVerificationResult(
    val status: VisualVerificationStatus,
    val truthState: TruthState,
    val candidateLabel: String,
    val confidencePct: Int,
    val disclaimer: String,
    val requiresPhysicalConfirmation: Boolean,
)

object VisualComponentVerifier {

    /**
     * Evaluates a visual recognition candidate.
     * Enforces the hard safety rule: Computer vision confidence alone NEVER equates to OEM verification.
     */
    fun evaluateVisualDetection(
        candidate: VisualDetectionCandidate,
        hasOemVinMatch: Boolean = false,
    ): VisualVerificationResult {
        if (!hasOemVinMatch) {
            return VisualVerificationResult(
                status = VisualVerificationStatus.IDENTIFICATION_NOT_VERIFIED,
                truthState = TruthState.ESTIMATED,
                candidateLabel = candidate.detectedLabel,
                confidencePct = candidate.modelConfidencePct,
                disclaimer = "Compatibilidad probable (${candidate.modelConfidencePct}%). Requiere confirmación por VIN/OEM/conector/medidas físicas.",
                requiresPhysicalConfirmation = true,
            )
        }

        return VisualVerificationResult(
            status = VisualVerificationStatus.OEM_VERIFIED_EXACT,
            truthState = TruthState.AUTHORITATIVE,
            candidateLabel = candidate.detectedLabel,
            confidencePct = 100,
            disclaimer = "Componente verificado con evidencia exacta por VIN y catálogo OEM.",
            requiresPhysicalConfirmation = false,
        )
    }
}
