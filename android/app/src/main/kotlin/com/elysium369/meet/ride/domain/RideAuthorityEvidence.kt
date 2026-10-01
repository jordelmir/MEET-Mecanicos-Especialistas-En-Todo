package com.elysium369.meet.ride.domain

/** Missing responses, local status and timeouts carry no server authority. */
object RideAuthorityEvidence {
    fun validVersion(version:Long?):Long?=version?.takeIf { it>0L }
    fun isTerminalSnapshot(state:String?,version:Long):Boolean =
        validVersion(version)!=null && state in setOf("COMPLETED","CANCELLED","EXPIRED","VOIDED")
}
