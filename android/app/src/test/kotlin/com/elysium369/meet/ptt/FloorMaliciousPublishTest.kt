package com.elysium369.meet.ptt

import com.elysium369.meet.communications.CallConnectionState
import com.elysium369.meet.communications.MediaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeMediaSession : MediaSession {
    private val _state = MutableStateFlow(CallConnectionState.IDLE)
    override val state: StateFlow<CallConnectionState> = _state
    var microphoneEnabledCalls = 0
    var lastMicrophoneEnabledValue: Boolean? = null

    override suspend fun connect(serverUrl: String, participantToken: String, enableMicrophoneOnConnect: Boolean): Result<Unit> {
        _state.value = CallConnectionState.ACTIVE
        return Result.success(Unit)
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> {
        microphoneEnabledCalls++
        lastMicrophoneEnabledValue = enabled
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        _state.value = CallConnectionState.ENDED
    }
}

class FloorMaliciousPublishTest {

    private lateinit var fakeMediaSession: FakeMediaSession
    private lateinit var pttTransport: PttMediaTransport

    @Before
    fun setup() {
        fakeMediaSession = FakeMediaSession()
        pttTransport = PttMediaTransport(fakeMediaSession)
    }

    @Test
    fun `malicious client without valid FloorLease is denied transmission`() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Expired lease
        val expiredLease = FloorLease(
            floorLeaseId = "lease-expired",
            channelId = "channel-ptt",
            holderPrincipalId = "user-mallory",
            holderDeviceId = "dev-mallory",
            requestId = "req-1",
            priority = FloorPriority.NORMAL,
            state = FloorState.GRANTED,
            fencingToken = 10L,
            grantedAt = now - 60_000L,
            expiresAt = now - 1000L, // Expired!
        )

        val expiredOutcome = pttTransport.startTransmitting(
            lease = expiredLease,
            principalId = "user-mallory",
            deviceId = "dev-mallory",
            fencingToken = 10L,
            nowEpochMs = now,
        )
        assertTrue(expiredOutcome is PttTransmitOutcome.Denied)

        // 2. Lease held by Alice, but Mallory attempts to transmit with it
        val aliceLease = FloorLease(
            floorLeaseId = "lease-alice",
            channelId = "channel-ptt",
            holderPrincipalId = "user-alice",
            holderDeviceId = "dev-alice",
            requestId = "req-2",
            priority = FloorPriority.NORMAL,
            state = FloorState.GRANTED,
            fencingToken = 11L,
            grantedAt = now,
            expiresAt = now + 30_000L,
        )

        val spoofOutcome = pttTransport.startTransmitting(
            lease = aliceLease,
            principalId = "user-mallory", // Spoofed sender
            deviceId = "dev-mallory",
            fencingToken = 11L,
            nowEpochMs = now,
        )
        assertTrue(spoofOutcome is PttTransmitOutcome.Denied)

        // 3. Fencing token mismatch (lease has token 11, Mallory presents 9)
        val tokenMismatchOutcome = pttTransport.startTransmitting(
            lease = aliceLease,
            principalId = "user-alice",
            deviceId = "dev-alice",
            fencingToken = 9L,
            nowEpochMs = now,
        )
        assertTrue(tokenMismatchOutcome is PttTransmitOutcome.Denied)

        // Verify setMicrophoneEnabled(true) was NEVER invoked during denied attempts
        assertEquals(0, fakeMediaSession.microphoneEnabledCalls)

        // 4. Valid lease holder successfully transmits
        val validOutcome = pttTransport.startTransmitting(
            lease = aliceLease,
            principalId = "user-alice",
            deviceId = "dev-alice",
            fencingToken = 11L,
            nowEpochMs = now,
        )
        assertEquals(PttTransmitOutcome.Transmitting, validOutcome)
        assertEquals(1, fakeMediaSession.microphoneEnabledCalls)
        assertEquals(true, fakeMediaSession.lastMicrophoneEnabledValue)
    }
}
