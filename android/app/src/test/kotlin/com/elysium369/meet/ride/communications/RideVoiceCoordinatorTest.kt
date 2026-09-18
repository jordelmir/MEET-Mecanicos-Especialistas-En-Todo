package com.elysium369.meet.ride.communications

import com.elysium369.meet.communications.CallAudioTransport
import com.elysium369.meet.communications.CallConnectionState
import com.elysium369.meet.communications.CallTransportOutcome
import com.elysium369.meet.communications.MediaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RideVoiceCoordinatorTest {

    private class FakeCallAudioTransport : CallAudioTransport {
        val stateFlow = MutableStateFlow(CallConnectionState.IDLE)
        override val state: StateFlow<CallConnectionState> = stateFlow.asStateFlow()
        var outcome: CallTransportOutcome = CallTransportOutcome.Connected
        var lastConversationId: String? = null
        var lastPrincipalId: String? = null
        var endCallsCount = 0

        override suspend fun connectAudio(conversationId: String, principalId: String): CallTransportOutcome {
            lastConversationId = conversationId
            lastPrincipalId = principalId
            if (outcome is CallTransportOutcome.Connected) {
                stateFlow.value = CallConnectionState.ACTIVE
            }
            return outcome
        }

        override suspend fun end() {
            endCallsCount++
            stateFlow.value = CallConnectionState.ENDED
        }
    }

    private class FakeMediaSession : MediaSession {
        val stateFlow = MutableStateFlow(CallConnectionState.IDLE)
        override val state: StateFlow<CallConnectionState> = stateFlow.asStateFlow()
        var microphoneEnabled = false

        override suspend fun connect(
            serverUrl: String,
            participantToken: String,
            enableMicrophoneOnConnect: Boolean,
        ): Result<Unit> {
            stateFlow.value = CallConnectionState.ACTIVE
            microphoneEnabled = enableMicrophoneOnConnect
            return Result.success(Unit)
        }

        override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> {
            microphoneEnabled = enabled
            return Result.success(Unit)
        }

        override suspend fun disconnect() {
            stateFlow.value = CallConnectionState.ENDED
        }
    }

    private class FakeAuditoryFeedback : AuditoryFeedbackEngine {
        val playedCues = mutableListOf<RideAuditoryCue>()
        override fun playCue(cue: RideAuditoryCue) {
            playedCues.add(cue)
        }
    }

    private lateinit var callTransport: FakeCallAudioTransport
    private lateinit var mediaSession: FakeMediaSession
    private lateinit var auditoryFeedback: FakeAuditoryFeedback

    @Before
    fun setUp() {
        callTransport = FakeCallAudioTransport()
        mediaSession = FakeMediaSession()
        auditoryFeedback = FakeAuditoryFeedback()
    }

    @Test
    fun `startRideCall transitions to Connected and emits auditory cue on success`() = runTest {
        val rideId = "ride-12345"
        val principalId = "driver-principal-01"

        val coordinator = RideVoiceCoordinator(
            callTransport = callTransport,
            mediaSession = mediaSession,
            auditoryFeedback = auditoryFeedback,
            dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        )

        val result = coordinator.startRideCall(rideId, principalId)
        assertTrue(result.isSuccess)
        assertEquals("ride_call_$rideId", callTransport.lastConversationId)
        assertEquals(principalId, callTransport.lastPrincipalId)
        assertTrue(coordinator.voiceState.value is RideVoiceState.Connected)
        val state = coordinator.voiceState.value as RideVoiceState.Connected
        assertEquals(rideId, state.rideId)
        assertEquals(false, state.isMuted)
        assertTrue(auditoryFeedback.playedCues.contains(RideAuditoryCue.CALL_CONNECTED))
    }

    @Test
    fun `endRideCall transitions to Ended and disconnects transport`() = runTest {
        val rideId = "ride-999"
        val principalId = "driver-p"

        val coordinator = RideVoiceCoordinator(
            callTransport = callTransport,
            mediaSession = mediaSession,
            auditoryFeedback = auditoryFeedback,
            dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        )

        coordinator.startRideCall(rideId, principalId)
        coordinator.endRideCall("USER_TERMINATED")

        assertTrue(coordinator.voiceState.value is RideVoiceState.Ended)
        assertEquals(1, callTransport.endCallsCount)
        assertTrue(auditoryFeedback.playedCues.contains(RideAuditoryCue.CALL_ENDED))
    }

    @Test
    fun `toggleMute updates mute state on active call`() = runTest {
        val rideId = "ride-abc"
        val principalId = "driver-abc"

        val coordinator = RideVoiceCoordinator(
            callTransport = callTransport,
            mediaSession = mediaSession,
            auditoryFeedback = auditoryFeedback,
            dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        )

        coordinator.startRideCall(rideId, principalId)
        val muteResult = coordinator.toggleMute()
        assertTrue(muteResult.isSuccess)
        assertEquals(true, muteResult.getOrNull())
        val connectedState = coordinator.voiceState.value as RideVoiceState.Connected
        assertTrue(connectedState.isMuted)

        // Toggle back to unmuted
        val unmuteResult = coordinator.toggleMute()
        assertTrue(unmuteResult.isSuccess)
        assertEquals(false, unmuteResult.getOrNull())
        val unmutedState = coordinator.voiceState.value as RideVoiceState.Connected
        assertEquals(false, unmutedState.isMuted)
    }

    @Test
    fun `startRideCall fails gracefully when transport rejects authentication`() = runTest {
        callTransport.outcome = CallTransportOutcome.AuthenticationRequired

        val coordinator = RideVoiceCoordinator(
            callTransport = callTransport,
            mediaSession = mediaSession,
            auditoryFeedback = auditoryFeedback,
            dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        )

        val result = coordinator.startRideCall("ride-secure", "driver-auth")
        assertTrue(result.isFailure)
        assertTrue(coordinator.voiceState.value is RideVoiceState.Error)
    }
}
