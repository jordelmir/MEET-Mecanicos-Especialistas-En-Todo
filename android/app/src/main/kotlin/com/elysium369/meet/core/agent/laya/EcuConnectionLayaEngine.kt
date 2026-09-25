package com.elysium369.meet.core.agent.laya

import com.elysium369.meet.core.obd.AdapterCompatibilityTier
import com.elysium369.meet.core.obd.DiagnosticProbeSpeed
import com.elysium369.meet.core.obd.ProtocolCandidate

/**
 * Vehicle profile context for ECU protocol negotiation.
 */
data class VehicleNegotiationContext(
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val vin: String? = null,
    val fuelType: String? = null, // GASOLINE, DIESEL, HYBRID, ELECTRIC
    val previousErrors: List<String> = emptyList(),
)

/**
 * Result of Laya System 1 ECU Connection strategy evaluation.
 */
data class EcuConnectionStrategy(
    val preferredProtocolId: String,
    val targetEcuHeader: String,
    val probeSpeed: DiagnosticProbeSpeed,
    val adapterStabilityRisk: Int, // 1 (stable) to 5 (extreme risk of crash/timeout)
    val bypassAutoSearch: Boolean, // Skip ATSP0 to prevent clone adapter freezing
    val recommendedInterCommandDelayMs: Long,
    val enableAdaptiveTiming: Boolean,
    val confidence: Double,
    val rationale: String,
)

/**
 * EcuConnectionLayaEngine — Distills automotive ECU communication knowledge into
 * Laya's System 1 non-autoregressive decision model.
 *
 * Infers optimal OBD-II/CAN protocols, ECU header addresses (e.g. 7DF vs 7E0 vs 18DB33F1),
 * baud rates, and adapter quirk workarounds based on vehicle make, model, year, VIN,
 * and ELM327/STN adapter version.
 *
 * Fallback Guarantee: Always safe. If Laya evaluation is uncertain or offline,
 * falls back to deterministic safe candidates. Physical communication is never blocked.
 */
class EcuConnectionLayaEngine(
    private val decisionEngine: LayaDecisionEngine = LayaDecisionEngine(),
) {

    private val protocolOptions = listOf(
        "ISO_15765_4_CAN_11BIT_500K",
        "ISO_15765_4_CAN_29BIT_500K",
        "ISO_15765_4_CAN_11BIT_250K",
        "ISO_14230_4_KWP_FAST",
        "ISO_9141_2_SLOW",
        "SAE_J1850_PWM",
        "SAE_J1850_VPW",
    )

    private val headerOptions = listOf(
        "7DF",          // Standard 11-bit broadcast
        "7E0",          // 11-bit Engine PCM primary
        "7E1",          // 11-bit Transmission TCM
        "18DB33F1",     // Standard 29-bit broadcast
        "18DA10F1",     // 29-bit Engine PCM direct
        "C133F1",       // KWP2000 physical
        "686AF1",       // ISO 9141-2 physical
    )

    fun evaluateStrategy(
        adapterVersionString: String?,
        context: VehicleNegotiationContext,
    ): EcuConnectionStrategy {
        val make = context.make?.trim()?.uppercase() ?: ""
        val model = context.model?.trim()?.uppercase() ?: ""
        val year = context.year ?: 0
        val vin = context.vin?.trim()?.uppercase() ?: ""
        val adapter = adapterVersionString?.trim()?.uppercase() ?: "UNKNOWN"

        // Build state descriptor for Laya System 1
        val state = buildString {
            append("ADAPTER: $adapter | ")
            if (make.isNotEmpty()) append("MAKE: $make ")
            if (model.isNotEmpty()) append("MODEL: $model ")
            if (year > 0) append("YEAR: $year ")
            if (vin.length >= 3) append("WMI: ${vin.take(3)} ")
            if (context.fuelType != null) append("FUEL: ${context.fuelType} ")
            if (context.previousErrors.isNotEmpty()) {
                append("ERRORS: ${context.previousErrors.joinToString(",")}")
            }
        }

        val questions = listOf(
            LayaQuestion.Choice(name = "optimal_protocol", options = protocolOptions),
            LayaQuestion.Choice(name = "target_header", options = headerOptions),
            LayaQuestion.Score(name = "adapter_risk", levels = 5),
            LayaQuestion.Noul(name = "bypass_auto_search"),
            LayaQuestion.Noul(name = "enable_fast_init"),
        )

        val batch = decisionEngine.evaluateSync(state, questions)

        // Knowledge-based calibration based on make, year and adapter
        val isClone = adapter.contains("V2.1") || adapter.contains("V2.2") || adapter.contains("V2.3")
        val isStn = adapter.contains("STN") || adapter.contains("OBDLINK") || adapter.contains("CANDLELIGHT")

        // 1. Protocol determination
        val inferredProtocol = when {
            // US / Global mandatory CAN (2008+)
            year >= 2008 -> {
                if (make in listOf("CHEVROLET", "GMC", "CADILLAC", "FREIGHTLINER", "ISUZU", "HINO") && (model.contains("2500") || model.contains("3500") || model.contains("TRUCK") || model.contains("BUS"))) {
                    "ISO_15765_4_CAN_29BIT_500K"
                } else {
                    "ISO_15765_4_CAN_11BIT_500K"
                }
            }
            // Ford / Lincoln / Mercury pre-2008
            make in listOf("FORD", "LINCOLN", "MERCURY", "MAZDA") && year in 1996..2007 -> {
                if (year >= 2004 && (model.contains("F-150") || model.contains("FOCUS") || model.contains("ESCAPE"))) {
                    "ISO_15765_4_CAN_11BIT_500K"
                } else {
                    "SAE_J1850_PWM"
                }
            }
            // GM / Chrysler pre-2008
            make in listOf("CHEVROLET", "CHEVY", "GMC", "CADILLAC", "PONTIAC", "BUICK", "OLDSMOBILE", "DODGE", "CHRYSLER", "JEEP") && year in 1996..2007 -> {
                if (year >= 2005 && (model.contains("CORVETTE") || model.contains("CHARGER") || model.contains("300C"))) {
                    "ISO_15765_4_CAN_11BIT_500K"
                } else {
                    "SAE_J1850_VPW"
                }
            }
            // European VAG, BMW, Mercedes pre-2008
            make in listOf("VOLKSWAGEN", "VW", "AUDI", "SEAT", "SKODA", "BMW", "MERCEDES", "MERCEDES-BENZ", "PEUGEOT", "RENAULT", "CITROEN") && year in 1996..2007 -> {
                if (year >= 2004) "ISO_15765_4_CAN_11BIT_500K" else "ISO_14230_4_KWP_FAST"
            }
            // Asian (Toyota, Honda, Nissan, Hyundai, Kia) pre-2008
            make in listOf("TOYOTA", "LEXUS", "HONDA", "ACURA", "NISSAN", "INFINITI", "HYUNDAI", "KIA", "MITSUBISHI", "SUBARU") && year in 1996..2007 -> {
                if (year >= 2005) "ISO_15765_4_CAN_11BIT_500K" else "ISO_9141_2_SLOW"
            }
            else -> batch.choice("optimal_protocol")?.value ?: "ISO_15765_4_CAN_11BIT_500K"
        }

        // 2. Header determination
        val targetHeader = when {
            inferredProtocol.contains("29BIT") -> "18DB33F1"
            inferredProtocol.contains("KWP") -> "C133F1"
            inferredProtocol.contains("9141") -> "686AF1"
            else -> "7DF"
        }

        // 3. Adapter stability & delay determination
        val adapterRisk = when {
            isStn -> 1
            isClone -> 4
            else -> batch.score("adapter_risk")?.level ?: 2
        }

        // Bypass auto-search (ATSP0) if using a clone or if vehicle protocol is high-confidence
        val bypassAutoSearch = isClone || (year > 0 && make.isNotEmpty())

        val interCommandDelay = when {
            isStn -> 0L
            isClone -> 50L
            adapterRisk >= 4 -> 35L
            else -> 10L
        }

        val enableAdaptiveTiming = !isClone

        val probeSpeed = when {
            isClone -> DiagnosticProbeSpeed.STABILITY_FIRST_PROBE
            isStn -> DiagnosticProbeSpeed.FAST_PATH_CACHED
            else -> DiagnosticProbeSpeed.OPTIMIZED_STANDARD
        }

        val confidence = when {
            year > 0 && make.isNotEmpty() -> 0.95
            year > 0 -> 0.85
            else -> batch.choice("optimal_protocol")?.confidence ?: 0.70
        }

        val rationale = "Laya System 1: Make='$make', Year='$year', AdapterTier='$adapter', Inferred='$inferredProtocol', Header='$targetHeader'"

        return EcuConnectionStrategy(
            preferredProtocolId = inferredProtocol,
            targetEcuHeader = targetHeader,
            probeSpeed = probeSpeed,
            adapterStabilityRisk = adapterRisk,
            bypassAutoSearch = bypassAutoSearch,
            recommendedInterCommandDelayMs = interCommandDelay,
            enableAdaptiveTiming = enableAdaptiveTiming,
            confidence = confidence,
            rationale = rationale,
        )
    }

    /**
     * Map protocol ID string to Elm candidate definition.
     */
    fun createCandidate(protocolId: String, customHeader: String? = null): ProtocolCandidate {
        return when (protocolId) {
            "ISO_15765_4_CAN_29BIT_500K" -> ProtocolCandidate(
                protocolId = "ISO_15765_4_CAN_29BIT_500K",
                elmCode = "7",
                initSequence = listOf("ATSP7", "ATSH${customHeader ?: "18DB33F1"}", "ATCAF1"),
                headerBytesHex = customHeader ?: "18DB33F1",
                priority = 9,
            )
            "ISO_15765_4_CAN_11BIT_250K" -> ProtocolCandidate(
                protocolId = "ISO_15765_4_CAN_11BIT_250K",
                elmCode = "8",
                initSequence = listOf("ATSP8", "ATSH${customHeader ?: "7DF"}", "ATCAF1"),
                headerBytesHex = customHeader ?: "7DF",
                priority = 8,
            )
            "ISO_14230_4_KWP_FAST" -> ProtocolCandidate(
                protocolId = "ISO_14230_4_KWP_FAST",
                elmCode = "5",
                initSequence = listOf("ATSP5", "ATSH${customHeader ?: "C133F1"}", "ATIB10"),
                headerBytesHex = customHeader ?: "C133F1",
                priority = 5,
            )
            "ISO_9141_2_SLOW" -> ProtocolCandidate(
                protocolId = "ISO_9141_2_SLOW",
                elmCode = "3",
                initSequence = listOf("ATSP3", "ATSH${customHeader ?: "686AF1"}", "ATIIA13"),
                headerBytesHex = customHeader ?: "686AF1",
                priority = 4,
            )
            "SAE_J1850_PWM" -> ProtocolCandidate(
                protocolId = "SAE_J1850_PWM",
                elmCode = "1",
                initSequence = listOf("ATSP1", "ATSH${customHeader ?: "616AF1"}"),
                headerBytesHex = customHeader ?: "616AF1",
                priority = 3,
            )
            "SAE_J1850_VPW" -> ProtocolCandidate(
                protocolId = "SAE_J1850_VPW",
                elmCode = "2",
                initSequence = listOf("ATSP2", "ATSH${customHeader ?: "686AF1"}"),
                headerBytesHex = customHeader ?: "686AF1",
                priority = 3,
            )
            else -> ProtocolCandidate(
                protocolId = "ISO_15765_4_CAN_11BIT_500K",
                elmCode = "6",
                initSequence = listOf("ATSP6", "ATSH${customHeader ?: "7DF"}", "ATCAF1"),
                headerBytesHex = customHeader ?: "7DF",
                priority = 10,
            )
        }
    }
}
