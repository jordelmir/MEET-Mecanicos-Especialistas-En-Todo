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
     * Grants access without misrepresenting a local pilot approval as a remote
     * identity review. Production and pilot approvals are intentionally
     * distinguishable in storage and UI.
     */
    fun grantsAccess(status: String?): Boolean =
        status == "APPROVED" || status == PILOT_APPROVED
}
