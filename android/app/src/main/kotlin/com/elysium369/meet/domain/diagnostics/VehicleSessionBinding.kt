package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.core.obd.DtcBucket
import com.elysium369.meet.core.obd.DtcRecord
import com.elysium369.meet.core.obd.ScanCompleteness
import java.util.UUID

enum class VehicleSessionBindingState {
    UNBOUND,
    VIN_VERIFIED,
    USER_CONFIRMED,
    CONFLICTED,
}

enum class VehicleSessionBindingMethod {
    NONE,
    ECU_VIN,
    USER_CONFIRMATION,
}

/** Immutable authority boundary between a physical link and a Garage vehicle. */
data class VehicleSessionBinding(
    val bindingId: String,
    val diagnosticSessionId: String,
    val physicalConnectionId: String,
    val vehicleId: String?,
    val observedVin: String?,
    val expectedVin: String?,
    val bindingState: VehicleSessionBindingState,
    val bindingMethod: VehicleSessionBindingMethod,
    val identityEvidenceIds: Set<String>,
    val boundAt: Long?,
    val conflictReason: String?,
) {
    val allowsPersistence: Boolean
        get() = vehicleId != null && bindingState in setOf(
            VehicleSessionBindingState.VIN_VERIFIED,
            VehicleSessionBindingState.USER_CONFIRMED,
        )

    val allowsActiveOperations: Boolean get() = allowsPersistence

    fun observeVin(
        observedVin: String,
        evidenceId: String,
    ): VehicleSessionBinding {
        check(bindingState == VehicleSessionBindingState.UNBOUND) {
            "VIN observation cannot rewrite an established or conflicted binding"
        }
        val normalized = requireNotNull(observedVin.normalizedVin()) { "Invalid observed VIN" }
        return copy(
            observedVin = normalized,
            identityEvidenceIds = identityEvidenceIds + evidenceId,
        )
    }

    fun bindVerifiedVin(
        vehicleId: String,
        observedVin: String,
        expectedVin: String,
        evidenceId: String,
        now: Long = System.currentTimeMillis(),
    ): VehicleSessionBinding {
        val observed = observedVin.normalizedVin()
        val expected = expectedVin.normalizedVin()
        return if (observed != null && observed == expected) {
            copy(
                vehicleId = vehicleId,
                observedVin = observed,
                expectedVin = expected,
                bindingState = VehicleSessionBindingState.VIN_VERIFIED,
                bindingMethod = VehicleSessionBindingMethod.ECU_VIN,
                identityEvidenceIds = identityEvidenceIds + evidenceId,
                boundAt = now,
                conflictReason = null,
            )
        } else {
            conflict(
                observedVin = observed,
                expectedVin = expected,
                reason = "ECU VIN does not match the selected Garage vehicle",
            )
        }
    }

    fun bindUserConfirmed(
        vehicleId: String,
        expectedVin: String?,
        evidenceId: String,
        now: Long = System.currentTimeMillis(),
    ): VehicleSessionBinding = copy(
        vehicleId = vehicleId,
        expectedVin = expectedVin?.normalizedVin(),
        bindingState = VehicleSessionBindingState.USER_CONFIRMED,
        bindingMethod = VehicleSessionBindingMethod.USER_CONFIRMATION,
        identityEvidenceIds = identityEvidenceIds + evidenceId,
        boundAt = now,
        conflictReason = null,
    )

    fun conflict(
        observedVin: String?,
        expectedVin: String?,
        reason: String,
    ): VehicleSessionBinding = copy(
        vehicleId = null,
        observedVin = observedVin?.normalizedVin(),
        expectedVin = expectedVin?.normalizedVin(),
        bindingState = VehicleSessionBindingState.CONFLICTED,
        bindingMethod = VehicleSessionBindingMethod.NONE,
        boundAt = null,
        conflictReason = reason,
    )

    companion object {
        fun unbound(
            diagnosticSessionId: String,
            physicalConnectionId: String,
        ) = VehicleSessionBinding(
            bindingId = UUID.randomUUID().toString(),
            diagnosticSessionId = diagnosticSessionId,
            physicalConnectionId = physicalConnectionId,
            vehicleId = null,
            observedVin = null,
            expectedVin = null,
            bindingState = VehicleSessionBindingState.UNBOUND,
            bindingMethod = VehicleSessionBindingMethod.NONE,
            identityEvidenceIds = emptySet(),
            boundAt = null,
            conflictReason = null,
        )
    }
}

data class LatestDiagnosticScanProjection(
    val scanId: String,
    val sessionId: String,
    val vehicleBindingId: String,
    val vehicleId: String?,
    val findings: List<DtcRecord>,
    val completeness: ScanCompleteness,
    val capturedAt: Long,
) {
    fun codesFor(bucket: DtcBucket): List<String> = findings
        .asSequence()
        .filter { it.bucket == bucket }
        .map { it.code }
        .distinct()
        .toList()

    fun belongsTo(vehicleId: String?): Boolean =
        vehicleId != null && this.vehicleId == vehicleId
}

private fun String.normalizedVin(): String? = trim().uppercase()
    .replace(Regex("[^A-HJ-NPR-Z0-9]"), "")
    .takeIf { it.length == 17 }
