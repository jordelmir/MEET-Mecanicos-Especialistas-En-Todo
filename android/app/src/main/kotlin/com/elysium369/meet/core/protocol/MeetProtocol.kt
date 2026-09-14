package com.elysium369.meet.core.protocol

import com.elysium369.meet.BuildConfig

/**
 * MeetProtocol: Server-authoritative contract versioning independent of store marketing versions.
 * Allows the backend to enforce minimum protocol versions and retire vulnerable clients safely.
 */
object MeetProtocol {
    const val VERSION = 2
    const val HEADER_PROTOCOL = "X-MEET-PROTOCOL"
    const val HEADER_BUILD = "X-MEET-BUILD"

    val buildSha: String get() = BuildConfig.MEET_BUILD_SHA

    fun headers(): Map<String, String> = mapOf(
        HEADER_PROTOCOL to VERSION.toString(),
        HEADER_BUILD to buildSha,
    )
}
