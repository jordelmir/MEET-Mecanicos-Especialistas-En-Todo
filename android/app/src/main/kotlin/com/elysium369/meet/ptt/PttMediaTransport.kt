package com.elysium369.meet.ptt

import com.elysium369.meet.communications.LiveKitMediaSession
import com.elysium369.meet.communications.MediaSession
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PttTransmitOutcome {
    data object Transmitting : PttTransmitOutcome
    data class Denied(val reason: String) : PttTransmitOutcome
    data class Error(val message: String) : PttTransmitOutcome
}

/**
 * PttMediaTransport — PTT audio media transport utilizing shared MediaSession.
 *
 * Laws:
 * - PREWARM: Connects in receive-only mode with microphone disabled (canPublish = false).
 * - FLOOR LEASE -> MEDIA PUBLISH AUTHORITY:
 *   Microphone is ONLY unmuted if a valid, unexpired FloorLease with matching fencingToken is presented.
 *   Client UI cannot bypass this server-authoritative lease verification.
 */
@Singleton
class PttMediaTransport(
    private val mediaSession: MediaSession,
) {
    @Inject
    constructor(liveKitMediaSession: LiveKitMediaSession) : this(liveKitMediaSession as MediaSession)

    suspend fun prewarmReceiveOnly(
        serverUrl: String,
        participantToken: String,
    ): Result<Unit> {
        return mediaSession.connect(
            serverUrl = serverUrl,
            participantToken = participantToken,
            enableMicrophoneOnConnect = false,
        )
    }

    suspend fun startTransmitting(
        lease: FloorLease,
        principalId: String,
        deviceId: String,
        fencingToken: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): PttTransmitOutcome {
        if (!lease.isValidHolder(principalId, deviceId, fencingToken, nowEpochMs)) {
            return PttTransmitOutcome.Denied(
                "INVALID_OR_EXPIRED_FLOOR_LEASE: holder=${lease.holderPrincipalId}, token=${lease.fencingToken}, state=${lease.state}"
            )
        }

        val unmuteResult = mediaSession.setMicrophoneEnabled(true)
        return if (unmuteResult.isSuccess) {
            PttTransmitOutcome.Transmitting
        } else {
            PttTransmitOutcome.Error("FAILED_TO_ENABLE_MICROPHONE: ${unmuteResult.exceptionOrNull()?.message}")
        }
    }

    suspend fun stopTransmitting(): Result<Unit> {
        return mediaSession.setMicrophoneEnabled(false)
    }

    suspend fun disconnect() {
        mediaSession.disconnect()
    }
}
