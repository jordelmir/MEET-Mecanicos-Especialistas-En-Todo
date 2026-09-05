package com.elysium369.meet.ptt

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionFailureTest {

    @Test
    fun `two-phase failure in LiveKit rolls back reservation and emits no FloorGranted event`() = runBlocking {
        val channelId = "channel-failing-livekit"

        // FloorAuthority with failing LiveKit permission controller
        val floorAuthority = FloorAuthority { _, _, _ ->
            false // LiveKit server fails to grant publish permission
        }

        val request = FloorRequest(
            requestId = "req-fail-001",
            channelId = channelId,
            principalId = "user-alice",
            deviceId = "dev-alice",
            requestedAtEpochMs = System.currentTimeMillis(),
        )

        val result = floorAuthority.requestFloor(request)

        // 1. Result must be Denied, NEVER Granted
        assertTrue("Result must be Denied when LiveKit fails", result is FloorArbitrationResult.Denied)
        val denied = result as FloorArbitrationResult.Denied
        assertTrue(denied.reason.contains("LIVEKIT_MEDIA_PERMISSION_FAILED"))

        // 2. Lease in state store must be rolled back to FAILED_SAFE
        val lease = floorAuthority.getCurrentLease(channelId)!!
        assertEquals(FloorState.FAILED_SAFE, lease.state)
        assertEquals("LIVEKIT_PERMISSION_FAILED", lease.revocationReason)

        // 3. Channel epoch remains 0 (not incremented)
        assertEquals(0L, floorAuthority.getCurrentChannelEpoch(channelId))
    }
}
