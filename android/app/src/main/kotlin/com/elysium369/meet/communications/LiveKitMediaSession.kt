package com.elysium369.meet.communications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MediaSession {
    val state: StateFlow<CallConnectionState>
    suspend fun connect(serverUrl: String, participantToken: String, enableMicrophoneOnConnect: Boolean): Result<Unit>
    suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit>
    suspend fun disconnect()
}

/**
 * Shared lower-level LiveKit media session abstraction.
 *
 * Used by:
 * - ElysiumCallTransport (normal 1-on-1 audio calls, enableMicrophoneOnConnect = true)
 * - PttMediaTransport (Vanguard PTT, receive-only prewarm, enableMicrophoneOnConnect = false)
 */
@Singleton
class LiveKitMediaSession @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaSession {
    private val mutex = Mutex()
    private var activeRoom: Room? = null
    private val _state = MutableStateFlow(CallConnectionState.IDLE)
    override val state: StateFlow<CallConnectionState> = _state

    override suspend fun connect(
        serverUrl: String,
        participantToken: String,
        enableMicrophoneOnConnect: Boolean,
    ): Result<Unit> = mutex.withLock {
        disconnectLocked()
        _state.value = CallConnectionState.CONNECTING
        val room = LiveKit.create(context.applicationContext)

        return runCatching {
            room.connect(serverUrl, participantToken)
            if (enableMicrophoneOnConnect) {
                room.localParticipant.setMicrophoneEnabled(true)
            } else {
                // PTT Receive-Only Prewarm: Microphone strictly stays disabled
                room.localParticipant.setMicrophoneEnabled(false)
            }
            activeRoom = room
            _state.value = CallConnectionState.ACTIVE
            Result.success(Unit)
        }.getOrElse { error ->
            room.disconnect()
            room.release()
            _state.value = CallConnectionState.FAILED
            Result.failure(error)
        }
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> = mutex.withLock {
        val room = activeRoom ?: return Result.failure(IllegalStateException("ROOM_NOT_CONNECTED"))
        return runCatching {
            room.localParticipant.setMicrophoneEnabled(enabled)
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun disconnect() = mutex.withLock { disconnectLocked() }

    private fun disconnectLocked() {
        activeRoom?.disconnect()
        activeRoom?.release()
        activeRoom = null
        _state.value = CallConnectionState.ENDED
    }
}
