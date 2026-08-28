package com.elysium369.meet.core.obd

import java.security.MessageDigest

enum class VinStrategy { SAE_MODE_09_PID_02, UDS_F190, DOIP_UDS_F190, KWP_RECIPE }
enum class VinResponseOutcome { VERIFIED, NO_RESPONSE, INVALID_RESPONSE, NOT_SUPPORTED, NEGATIVE_RESPONSE }
enum class VehicleIdentityResultCode {
    UNKNOWN,
    NO_RESPONSE,
    INVALID_RESPONSE,
    NOT_SUPPORTED,
    NOT_AVAILABLE_VIA_CURRENT_PATH,
    SINGLE_ECU_OBSERVED,
    SINGLE_ECU_VERIFIED,
    MULTI_ECU_CONSENSUS,
    CONFLICT_DETECTED,
}

data class VinCapabilityContext(
    val transport: DiagnosticTransport,
    val protocol: DiagnosticApplicationProtocol,
    val mode09Pid02Supported: Boolean = false,
    val knownUdsEndpoint: EcuEndpoint? = null,
    val authorizedKwpRecipeId: String? = null,
    val authorizedKwpCommands: List<String> = emptyList(),
)

data class VinProbe(
    val strategy: VinStrategy,
    val command: String,
    val requestAddress: String?,
    val recipeId: String? = null,
)

data class VinProbePlan(
    val probes: List<VinProbe>,
    val unavailableReason: VehicleIdentityResultCode? = null,
)

object VinStrategyCompiler {
    fun compile(context: VinCapabilityContext): VinProbePlan = when {
        context.transport == DiagnosticTransport.DOIP &&
            context.protocol == DiagnosticApplicationProtocol.UDS &&
            context.knownUdsEndpoint != null -> VinProbePlan(
                listOf(VinProbe(VinStrategy.DOIP_UDS_F190, "22F190", context.knownUdsEndpoint.logicalAddress)),
            )
        context.protocol == DiagnosticApplicationProtocol.UDS && context.knownUdsEndpoint != null -> VinProbePlan(
            listOf(VinProbe(VinStrategy.UDS_F190, "22F190", context.knownUdsEndpoint.requestAddress)),
        )
        context.protocol == DiagnosticApplicationProtocol.SAE_OBD && context.mode09Pid02Supported -> VinProbePlan(
            listOf(VinProbe(VinStrategy.SAE_MODE_09_PID_02, "0902", "7DF")),
        )
        context.protocol == DiagnosticApplicationProtocol.SAE_OBD -> VinProbePlan(
            emptyList(), VehicleIdentityResultCode.NOT_SUPPORTED,
        )
        context.protocol == DiagnosticApplicationProtocol.KWP2000 &&
            !context.authorizedKwpRecipeId.isNullOrBlank() &&
            context.authorizedKwpCommands.isNotEmpty() -> VinProbePlan(
                context.authorizedKwpCommands.map {
                    VinProbe(VinStrategy.KWP_RECIPE, it, requestAddress = null, recipeId = context.authorizedKwpRecipeId)
                },
            )
        else -> VinProbePlan(emptyList(), VehicleIdentityResultCode.NOT_AVAILABLE_VIA_CURRENT_PATH)
    }
}

data class VinObservation(
    val identityObservationId: String,
    val diagnosticSessionId: String,
    val vehicleBindingId: String?,
    val strategy: VinStrategy,
    val transport: DiagnosticTransport,
    val protocol: DiagnosticApplicationProtocol,
    val requestAddress: String?,
    val responseAddress: String?,
    val ecuIdentity: String?,
    val startedMonotonicMs: Long,
    val completedMonotonicMs: Long,
    val responseOutcome: VinResponseOutcome,
    val rawResponseHash: String,
    val parserVersion: String,
    /** Local verified binding material. Telemetry accepts only [vinHash]. */
    val normalizedVin: String?,
    val vinHash: String?,
    val vinLength: Int?,
) {
    val isVerified: Boolean
        get() = responseOutcome == VinResponseOutcome.VERIFIED && VinValidator.normalize(normalizedVin.orEmpty()) != null

    companion object {
        fun verifiedVinHash(vin: String): String = MessageDigest.getInstance("SHA-256")
            .digest(vin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

data class VinConsensusResult(
    val resultCode: VehicleIdentityResultCode,
    val verifiedVin: String? = null,
    val supportingObservationIds: List<String> = emptyList(),
)

object VinConsensusEvaluator {
    fun evaluate(observations: List<VinObservation>): VinConsensusResult {
        val verified = observations.filter(VinObservation::isVerified)
        if (verified.isEmpty()) {
            val result = when {
                observations.isEmpty() -> VehicleIdentityResultCode.UNKNOWN
                observations.all { it.responseOutcome == VinResponseOutcome.NO_RESPONSE } -> VehicleIdentityResultCode.NO_RESPONSE
                observations.any { it.responseOutcome == VinResponseOutcome.INVALID_RESPONSE } -> VehicleIdentityResultCode.INVALID_RESPONSE
                observations.all { it.responseOutcome == VinResponseOutcome.NOT_SUPPORTED } -> VehicleIdentityResultCode.NOT_SUPPORTED
                else -> VehicleIdentityResultCode.UNKNOWN
            }
            return VinConsensusResult(result)
        }
        val byVin = verified.groupBy { it.normalizedVin!! }
        if (byVin.size > 1) return VinConsensusResult(
            VehicleIdentityResultCode.CONFLICT_DETECTED,
            supportingObservationIds = verified.map(VinObservation::identityObservationId),
        )
        val (vin, support) = byVin.entries.single()
        return VinConsensusResult(
            resultCode = if (support.size > 1) VehicleIdentityResultCode.MULTI_ECU_CONSENSUS
            else VehicleIdentityResultCode.SINGLE_ECU_VERIFIED,
            verifiedVin = vin,
            supportingObservationIds = support.map(VinObservation::identityObservationId),
        )
    }
}
