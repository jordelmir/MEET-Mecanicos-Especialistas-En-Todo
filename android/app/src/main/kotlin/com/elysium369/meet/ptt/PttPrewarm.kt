package com.elysium369.meet.ptt

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PttPrewarm — Pre-establishes transport connections before button-down (Section 68).
 *
 * Laws:
 * - Do not wait for button-down to complete handshake
 * - Prewarm on channel entry
 * - Prepare muted track
 * - Prepare authorization
 * - LiveKit handles audio transport
 * - PTT handles floor control
 */
@Singleton
class PttPrewarm @Inject constructor() {

    private val prewarmedChannels = mutableMapOf<String, PrewarmState>()

    data class PrewarmState(
        val channelId: String,
        val principalId: String,
        val transportReady: Boolean = false,
        val subscriptionReady: Boolean = false,
        val mutedTrackReady: Boolean = false,
        val authorizationReady: Boolean = false,
        val prewarmedAtEpochMs: Long = System.currentTimeMillis(),
    ) {
        val isFullyPrewarmed: Boolean
            get() = transportReady && subscriptionReady && mutedTrackReady && authorizationReady
    }

    /** Start prewarm for a channel. */
    fun startPrewarm(channelId: String, principalId: String): PrewarmState {
        val state = PrewarmState(
            channelId = channelId,
            principalId = principalId,
        )
        prewarmedChannels[channelId] = state
        Log.i("PttPrewarm", "Prewarm started for channel $channelId")
        return state
    }

    /** Mark transport as ready. */
    fun markTransportReady(channelId: String): Boolean {
        val state = prewarmedChannels[channelId] ?: return false
        prewarmedChannels[channelId] = state.copy(transportReady = true)
        return true
    }

    /** Mark subscription as ready. */
    fun markSubscriptionReady(channelId: String): Boolean {
        val state = prewarmedChannels[channelId] ?: return false
        prewarmedChannels[channelId] = state.copy(subscriptionReady = true)
        return true
    }

    /** Mark muted track as ready. */
    fun markMutedTrackReady(channelId: String): Boolean {
        val state = prewarmedChannels[channelId] ?: return false
        prewarmedChannels[channelId] = state.copy(mutedTrackReady = true)
        return true
    }

    /** Mark authorization as ready. */
    fun markAuthorizationReady(channelId: String): Boolean {
        val state = prewarmedChannels[channelId] ?: return false
        prewarmedChannels[channelId] = state.copy(authorizationReady = true)
        return true
    }

    /** Check if a channel is fully prewarmed. */
    fun isPrewarmed(channelId: String): Boolean {
        return prewarmedChannels[channelId]?.isFullyPrewarmed == true
    }

    /** Get prewarm state for a channel. */
    fun getPrewarmState(channelId: String): PrewarmState? {
        return prewarmedChannels[channelId]
    }

    /** Remove prewarm state when leaving a channel. */
    fun removePrewarm(channelId: String) {
        prewarmedChannels.remove(channelId)
        Log.i("PttPrewarm", "Prewarm removed for channel $channelId")
    }

    /** Cleanup stale prewarm states (older than 1 hour). */
    fun cleanup(maxAgeMs: Long = 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        prewarmedChannels.entries.removeIf { (_, state) ->
            state.prewarmedAtEpochMs < cutoff
        }
    }
}
