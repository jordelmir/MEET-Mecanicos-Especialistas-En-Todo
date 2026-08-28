package com.elysium369.meet.core.obd

import android.util.Log
import kotlinx.serialization.Serializable

enum class AdapterRiskTier {
    GENUINE_STN,
    GENUINE_OBDLINK,
    GENUINE_CANDLELIGHT,
    COMPATIBLE_V15,
    LOW_COST_CLONE_RISK_MEDIUM,
    DEFECTIVE_CLONE_RISK_HIGH,
    UNKNOWN_ADAPTER,
}

enum class DiagnosticProbeSpeed {
    FAST_PATH_CACHED,
    OPTIMIZED_STANDARD,
    EXHAUSTIVE_SAFE_PROBE,
}

@Serializable
data class ProtocolCandidate(
    val protocolId: String,       // e.g. "ISO_15765_4_CAN_11BIT_500K", "ISO_15765_4_CAN_29BIT_500K", "ISO_14230_4_KWP_FAST"
    val elmCode: String,          // e.g. "6", "7", "8", "9", "5"
    val initSequence: List<String>,
    val headerBytesHex: String,   // e.g. "7DF" or "18DB33F1"
    val priority: Int,
)

data class ProtocolNegotiationPlan(
    val adapterRiskTier: AdapterRiskTier,
    val probeSpeed: DiagnosticProbeSpeed,
    val preferredCandidate: ProtocolCandidate,
    val fallbackCandidates: List<ProtocolCandidate>,
    val interCommandDelayMs: Long,
    val enableAdaptiveTiming: Boolean,
)

/**
 * AdaptiveProtocolNegotiatorV2 — Compiles deterministic protocol negotiation plans
 * based on adapter fingerprints, clone risks, and vehicle history.
 */
object AdaptiveProtocolNegotiatorV2 {

    private const val TAG = "AdaptiveNegotiatorV2"

    val STANDARD_CAN_11BIT_500K = ProtocolCandidate(
        protocolId = "ISO_15765_4_CAN_11BIT_500K",
        elmCode = "6",
        initSequence = listOf("ATSP6", "ATSH7DF", "ATCAF1"),
        headerBytesHex = "7DF",
        priority = 10,
    )

    val STANDARD_CAN_29BIT_500K = ProtocolCandidate(
        protocolId = "ISO_15765_4_CAN_29BIT_500K",
        elmCode = "7",
        initSequence = listOf("ATSP7", "ATSH18DB33F1", "ATCAF1"),
        headerBytesHex = "18DB33F1",
        priority = 9,
    )

    val STANDARD_CAN_11BIT_250K = ProtocolCandidate(
        protocolId = "ISO_15765_4_CAN_11BIT_250K",
        elmCode = "8",
        initSequence = listOf("ATSP8", "ATSH7DF", "ATCAF1"),
        headerBytesHex = "7DF",
        priority = 8,
    )

    val KWP_2000_FAST = ProtocolCandidate(
        protocolId = "ISO_14230_4_KWP_FAST",
        elmCode = "5",
        initSequence = listOf("ATSP5", "ATSHC133F1", "ATIB10"),
        headerBytesHex = "C133F1",
        priority = 5,
    )

    val ISO_9141_2 = ProtocolCandidate(
        protocolId = "ISO_9141_2_SLOW",
        elmCode = "3",
        initSequence = listOf("ATSP3", "ATSH686AF1", "ATIIA13"),
        headerBytesHex = "686AF1",
        priority = 4,
    )

    fun evaluateAdapterRisk(adapterVersionString: String?): AdapterRiskTier {
        if (adapterVersionString == null) return AdapterRiskTier.UNKNOWN_ADAPTER
        val upper = adapterVersionString.uppercase()
        return when {
            upper.contains("STN") -> AdapterRiskTier.GENUINE_STN
            upper.contains("OBDLINK") -> AdapterRiskTier.GENUINE_OBDLINK
            upper.contains("GS_USB") || upper.contains("CANDLELIGHT") -> AdapterRiskTier.GENUINE_CANDLELIGHT
            upper.contains("V1.5") -> AdapterRiskTier.COMPATIBLE_V15
            upper.contains("V2.1") || upper.contains("ELM327 V2.1") -> AdapterRiskTier.DEFECTIVE_CLONE_RISK_HIGH
            upper.contains("V2.2") || upper.contains("V2.3") -> AdapterRiskTier.LOW_COST_CLONE_RISK_MEDIUM
            else -> AdapterRiskTier.UNKNOWN_ADAPTER
        }
    }

    fun compilePlan(
        adapterVersionString: String?,
        cachedSuccessfulProtocol: String? = null,
        vehicleYear: Int? = null,
    ): ProtocolNegotiationPlan {
        val riskTier = evaluateAdapterRisk(adapterVersionString)

        val interCommandDelay = when (riskTier) {
            AdapterRiskTier.GENUINE_STN, AdapterRiskTier.GENUINE_OBDLINK, AdapterRiskTier.GENUINE_CANDLELIGHT -> 0L
            AdapterRiskTier.COMPATIBLE_V15 -> 10L
            AdapterRiskTier.LOW_COST_CLONE_RISK_MEDIUM -> 25L
            AdapterRiskTier.DEFECTIVE_CLONE_RISK_HIGH -> 50L
            AdapterRiskTier.UNKNOWN_ADAPTER -> 20L
        }

        val enableAdaptiveTiming = when (riskTier) {
            AdapterRiskTier.DEFECTIVE_CLONE_RISK_HIGH -> false // Clones hang with ATAT1/ATAT2
            else -> true
        }

        val candidates = mutableListOf<ProtocolCandidate>()
        if (vehicleYear != null && vehicleYear < 2008) {
            // Prioritize legacy protocols for older vehicles
            candidates.add(STANDARD_CAN_11BIT_500K)
            candidates.add(KWP_2000_FAST)
            candidates.add(ISO_9141_2)
            candidates.add(STANDARD_CAN_29BIT_500K)
            candidates.add(STANDARD_CAN_11BIT_250K)
        } else {
            // Standard CAN first (US 2008+ / EU 2004+)
            candidates.add(STANDARD_CAN_11BIT_500K)
            candidates.add(STANDARD_CAN_29BIT_500K)
            candidates.add(STANDARD_CAN_11BIT_250K)
            candidates.add(KWP_2000_FAST)
            candidates.add(ISO_9141_2)
        }

        val preferred = if (cachedSuccessfulProtocol != null) {
            candidates.find { it.protocolId == cachedSuccessfulProtocol } ?: candidates.first()
        } else {
            candidates.first()
        }

        val fallbacks = candidates.filter { it.protocolId != preferred.protocolId }

        val probeSpeed = if (cachedSuccessfulProtocol != null) {
            DiagnosticProbeSpeed.FAST_PATH_CACHED
        } else if (riskTier == AdapterRiskTier.DEFECTIVE_CLONE_RISK_HIGH) {
            DiagnosticProbeSpeed.EXHAUSTIVE_SAFE_PROBE
        } else {
            DiagnosticProbeSpeed.OPTIMIZED_STANDARD
        }

        Log.i(TAG, "Compiled negotiation plan: Risk=$riskTier, Speed=$probeSpeed, Preferred=${preferred.protocolId}, InterDelay=${interCommandDelay}ms")

        return ProtocolNegotiationPlan(
            adapterRiskTier = riskTier,
            probeSpeed = probeSpeed,
            preferredCandidate = preferred,
            fallbackCandidates = fallbacks,
            interCommandDelayMs = interCommandDelay,
            enableAdaptiveTiming = enableAdaptiveTiming,
        )
    }
}
