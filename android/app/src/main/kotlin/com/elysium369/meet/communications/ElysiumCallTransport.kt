package com.elysium369.meet.communications

import android.content.Context
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.remote.SupabaseModule
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.auth
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CallConnectionState {
    IDLE,
    REQUESTING_AUTHORIZATION,
    CONNECTING,
    ACTIVE,
    ENDED,
    FAILED,
}

sealed interface CallTransportOutcome {
    data object Connected : CallTransportOutcome
    data object NotConfigured : CallTransportOutcome
    data object AuthenticationRequired : CallTransportOutcome
    data object RejectedInsecureEndpoint : CallTransportOutcome
    data class Failed(val safeCode: String) : CallTransportOutcome
}

/**
 * Real LiveKit audio transport. It accepts only a server-minted token obtained
 * with the current Supabase session and never accepts literal client tokens.
 */
@Singleton
class ElysiumCallTransport @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var activeRoom: Room? = null
    private val _state = MutableStateFlow(CallConnectionState.IDLE)
    val state: StateFlow<CallConnectionState> = _state

    suspend fun connectAudio(conversationId: String, principalId: String): CallTransportOutcome = mutex.withLock {
        val endpoint = BuildConfig.COMMUNICATION_CALL_TOKEN_URL.trim()
        if (endpoint.isEmpty()) return CallTransportOutcome.NotConfigured
        if (!endpoint.startsWith("https://")) return CallTransportOutcome.RejectedInsecureEndpoint

        val accessToken = SupabaseModule.client.auth.currentSessionOrNull()?.accessToken
            ?.takeIf(String::isNotBlank)
            ?: return CallTransportOutcome.AuthenticationRequired

        endLocked()
        _state.value = CallConnectionState.REQUESTING_AUTHORIZATION
        val credentials = runCatching {
            TokenSource.fromEndpoint(
                url = endpoint,
                headers = mapOf(
                    "Authorization" to "Bearer $accessToken",
                    "apikey" to BuildConfig.SUPABASE_KEY,
                ),
            ).fetch(
                TokenRequestOptions(
                    roomName = conversationId,
                    participantName = "Elysium user",
                    participantIdentity = principalId,
                ),
            ).getOrThrow()
        }.getOrElse {
            _state.value = CallConnectionState.FAILED
            return CallTransportOutcome.Failed("TOKEN_ENDPOINT_REJECTED")
        }

        if (!credentials.serverUrl.startsWith("wss://")) {
            _state.value = CallConnectionState.FAILED
            return CallTransportOutcome.RejectedInsecureEndpoint
        }

        _state.value = CallConnectionState.CONNECTING
        val room = LiveKit.create(context.applicationContext)
        return runCatching {
            room.connect(credentials.serverUrl, credentials.participantToken)
            room.localParticipant.setMicrophoneEnabled(true)
            activeRoom = room
            _state.value = CallConnectionState.ACTIVE
            CallTransportOutcome.Connected
        }.getOrElse {
            room.disconnect()
            room.release()
            _state.value = CallConnectionState.FAILED
            CallTransportOutcome.Failed("WEBRTC_CONNECTION_FAILED")
        }
    }

    suspend fun end() = mutex.withLock { endLocked() }

    private fun endLocked() {
        activeRoom?.disconnect()
        activeRoom?.release()
        activeRoom = null
        _state.value = CallConnectionState.ENDED
    }
}
