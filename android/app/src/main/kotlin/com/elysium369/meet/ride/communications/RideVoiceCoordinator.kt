package com.elysium369.meet.ride.communications

import android.util.Log
import com.elysium369.meet.communications.CallConnectionState
import com.elysium369.meet.communications.CallTransportOutcome
import com.elysium369.meet.communications.ElysiumCallTransport
import com.elysium369.meet.communications.LiveKitMediaSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

import com.elysium369.meet.communications.CallAudioTransport
import com.elysium369.meet.communications.MediaSession
import com.elysium369.meet.communications.RideLiveVoiceBridge

sealed interface RideVoiceState {
    data object Idle : RideVoiceState
    data class Connecting(val rideId: String) : RideVoiceState
    data class Connected(
        val rideId: String,
        val isMuted: Boolean = false,
        val connectedAtEpochMs: Long = System.currentTimeMillis(),
    ) : RideVoiceState
    data class Ended(val reason: String) : RideVoiceState
    data class Error(val message: String) : RideVoiceState
}

@Singleton
class RideVoiceCoordinator(
    private val callTransport: CallAudioTransport,
    private val mediaSession: MediaSession,
    private val auditoryFeedback: AuditoryFeedbackEngine,
    private val liveVoiceBridge: RideLiveVoiceBridge? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Inject
    constructor(
        callTransport: ElysiumCallTransport,
        mediaSession: LiveKitMediaSession,
        auditoryFeedback: RideAuditoryFeedback,
        liveVoiceBridge: RideLiveVoiceBridge,
    ) : this(
        callTransport = callTransport as CallAudioTransport,
        mediaSession = mediaSession as MediaSession,
        auditoryFeedback = auditoryFeedback as AuditoryFeedbackEngine,
        liveVoiceBridge = liveVoiceBridge,
        dispatcher = Dispatchers.Default,
    )
    private val tag = "RideVoiceCoordinator"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _voiceState = MutableStateFlow<RideVoiceState>(RideVoiceState.Idle)
    val voiceState: StateFlow<RideVoiceState> = _voiceState.asStateFlow()

    private var activeRideId: String? = null
    private var isMuted: Boolean = false

    init {
        scope.launch {
            callTransport.state.collect { connectionState ->
                when (connectionState) {
                    CallConnectionState.ACTIVE -> {
                        val currentRide = activeRideId
                        if (currentRide != null) {
                            _voiceState.value = RideVoiceState.Connected(
                                rideId = currentRide,
                                isMuted = isMuted,
                            )
                            auditoryFeedback.playCue(RideAuditoryCue.CALL_CONNECTED)
                        }
                    }
                    CallConnectionState.ENDED -> {
                        if (_voiceState.value is RideVoiceState.Connected || _voiceState.value is RideVoiceState.Connecting) {
                            _voiceState.value = RideVoiceState.Ended("CALL_DISCONNECTED")
                            auditoryFeedback.playCue(RideAuditoryCue.CALL_ENDED)
                        }
                        activeRideId = null
                    }
                    CallConnectionState.FAILED -> {
                        _voiceState.value = RideVoiceState.Error("CONNECTION_FAILED")
                        activeRideId = null
                    }
                    else -> Unit
                }
            }
        }

        liveVoiceBridge?.let { bridge ->
            scope.launch {
                bridge.callState.collect { lState ->
                    when (lState) {
                        is com.elysium369.meet.communications.LiveCallState.Active -> {
                            val currentRide = activeRideId ?: lState.rideId
                            _voiceState.value = RideVoiceState.Connected(
                                rideId = currentRide,
                                isMuted = lState.isMuted,
                                connectedAtEpochMs = lState.startedAtEpochMs,
                            )
                        }
                        is com.elysium369.meet.communications.LiveCallState.Ended -> {
                            if (_voiceState.value is RideVoiceState.Connected || _voiceState.value is RideVoiceState.Connecting) {
                                _voiceState.value = RideVoiceState.Ended(lState.reason)
                                auditoryFeedback.playCue(RideAuditoryCue.CALL_ENDED)
                            }
                            activeRideId = null
                        }
                        is com.elysium369.meet.communications.LiveCallState.Error -> {
                            _voiceState.value = RideVoiceState.Error(lState.message)
                            activeRideId = null
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    suspend fun startRideCall(rideId: String, principalId: String): Result<Unit> = mutex.withLock {
        if (_voiceState.value is RideVoiceState.Connected && activeRideId == rideId) {
            return Result.success(Unit) // Already connected
        }

        activeRideId = rideId
        isMuted = false
        _voiceState.value = RideVoiceState.Connecting(rideId)

        val outcome = callTransport.connectAudio(
            conversationId = "ride_call_$rideId",
            principalId = principalId,
        )

        return when (outcome) {
            is CallTransportOutcome.Connected -> {
                _voiceState.value = RideVoiceState.Connected(rideId = rideId, isMuted = false)
                auditoryFeedback.playCue(RideAuditoryCue.CALL_CONNECTED)
                Result.success(Unit)
            }
            is CallTransportOutcome.Failed -> {
                _voiceState.value = RideVoiceState.Error(outcome.safeCode)
                Result.failure(IllegalStateException(outcome.safeCode))
            }
            CallTransportOutcome.AuthenticationRequired -> {
                _voiceState.value = RideVoiceState.Error("AUTHENTICATION_REQUIRED")
                Result.failure(IllegalStateException("AUTHENTICATION_REQUIRED"))
            }
            CallTransportOutcome.NotConfigured -> {
                Log.i(tag, "Call endpoint not configured. Activating direct Realtime Live Voice Bridge.")
                val bridge = liveVoiceBridge
                if (bridge != null) {
                    val role = if (principalId.startsWith("driver", ignoreCase = true)) "DRIVER" else "PASSENGER"
                    val bridgeOutcome = bridge.startCall(rideId, role)
                    if (bridgeOutcome.isSuccess) {
                        _voiceState.value = RideVoiceState.Connected(rideId = rideId, isMuted = false)
                        auditoryFeedback.playCue(RideAuditoryCue.CALL_CONNECTED)
                        Result.success(Unit)
                    } else {
                        _voiceState.value = RideVoiceState.Error("BRIDGE_FAILURE")
                        Result.failure(bridgeOutcome.exceptionOrNull() ?: IllegalStateException("BRIDGE_FAILURE"))
                    }
                } else {
                    _voiceState.value = RideVoiceState.Connected(rideId = rideId, isMuted = false)
                    auditoryFeedback.playCue(RideAuditoryCue.CALL_CONNECTED)
                    Result.success(Unit)
                }
            }
            CallTransportOutcome.RejectedInsecureEndpoint -> {
                _voiceState.value = RideVoiceState.Error("INSECURE_ENDPOINT")
                Result.failure(SecurityException("INSECURE_ENDPOINT"))
            }
        }
    }

    suspend fun endRideCall(reason: String = "USER_TERMINATED") = mutex.withLock {
        callTransport.end()
        liveVoiceBridge?.endCall(reason)
        activeRideId = null
        isMuted = false
        _voiceState.value = RideVoiceState.Ended(reason)
        auditoryFeedback.playCue(RideAuditoryCue.CALL_ENDED)
    }

    suspend fun toggleMute(): Result<Boolean> = mutex.withLock {
        val current = _voiceState.value
        if (current !is RideVoiceState.Connected) {
            return Result.failure(IllegalStateException("NO_ACTIVE_CALL"))
        }

        val bridge = liveVoiceBridge
        if (bridge != null) {
            val targetMute = bridge.toggleMute()
            isMuted = targetMute
            _voiceState.value = current.copy(isMuted = targetMute)
            return Result.success(targetMute)
        }

        val targetMute = !isMuted
        val result = mediaSession.setMicrophoneEnabled(!targetMute)
        return if (result.isSuccess) {
            isMuted = targetMute
            _voiceState.value = current.copy(isMuted = targetMute)
            Result.success(targetMute)
        } else {
            Result.failure(result.exceptionOrNull() ?: IllegalStateException("MUTE_FAILED"))
        }
    }
}
