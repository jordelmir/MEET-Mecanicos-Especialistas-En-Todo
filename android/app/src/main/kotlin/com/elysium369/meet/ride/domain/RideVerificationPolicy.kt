package com.elysium369.meet.ride.domain

data class RideVerificationDecision(
    val status: String,
    val approvedAtEpochMs: Long?,
)

object RideVerificationPolicy {
    const val PILOT_APPROVED = "PILOT_APPROVED"

    fun decide(
        localAutoApprovalEnabled: Boolean,
        evidenceReady: Boolean,
        nowEpochMs: Long,
    ): RideVerificationDecision {
        require(nowEpochMs >= 0L) { "Verification time cannot be negative" }
        return if (!evidenceReady) {
            RideVerificationDecision(
                status = "INCOMPLETE",
                approvedAtEpochMs = null,
            )
        } else if (localAutoApprovalEnabled) {
            RideVerificationDecision(
                status = PILOT_APPROVED,
                approvedAtEpochMs = nowEpochMs,
            )
        } else {
            RideVerificationDecision(
                status = "PENDING",
                approvedAtEpochMs = null,
            )
        }
    }

    /**
     * Local evidence attestation is never enough to operate. Only an explicit
     * remote review may grant access.
     */
    fun grantsAccess(status: String?): Boolean =
        status == "APPROVED"
}
