package com.elysium369.meet.communications

import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Real LiveKit audio transport for normal 1-on-1 calls.
 * Delegates to shared LiveKitMediaSession, connecting with microphone enabled.
 */
@Singleton
class ElysiumLiveKitCallTransport @Inject constructor(
    private val mediaSession: LiveKitMediaSession,
) : CallAudioTransport {
    override val state: StateFlow<CallConnectionState> = mediaSession.state

    override suspend fun connectAudio(conversationId: String, principalId: String): CallTransportOutcome {
        val endpoint = BuildConfig.COMMUNICATION_CALL_TOKEN_URL.trim()
        if (endpoint.isEmpty()) return CallTransportOutcome.NotConfigured
        if (!endpoint.startsWith("https://")) return CallTransportOutcome.RejectedInsecureEndpoint

        val accessToken = SupabaseModule.client.auth.currentSessionOrNull()?.accessToken
            ?.takeIf(String::isNotBlank)
            ?: return CallTransportOutcome.AuthenticationRequired

        mediaSession.disconnect()
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
            return CallTransportOutcome.Failed("TOKEN_ENDPOINT_REJECTED")
        }

        if (!credentials.serverUrl.startsWith("wss://")) {
            return CallTransportOutcome.RejectedInsecureEndpoint
        }

        // For normal calls, enable microphone on connect
        val connectResult = mediaSession.connect(
            serverUrl = credentials.serverUrl,
            participantToken = credentials.participantToken,
            enableMicrophoneOnConnect = true,
        )

        return if (connectResult.isSuccess) {
            CallTransportOutcome.Connected
        } else {
            CallTransportOutcome.Failed("WEBRTC_CONNECTION_FAILED")
        }
    }

    override suspend fun end() {
        mediaSession.disconnect()
    }
}
