package com.elysium369.meet.ptt

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StaleFloorFencingTest {

    private lateinit var floorAuthority: FloorAuthority

    @Before
    fun setup() {
        floorAuthority = FloorAuthority { _, _, _ -> true }
    }

    @Test
    fun `stale fencing token is rejected and does not corrupt current active floor`() = runBlocking {
        val channelId = "channel-fencing-test"

        // 1. Alice acquires floor (fencingToken = 1)
        val aliceReq = FloorRequest(
            requestId = "req-alice",
            channelId = channelId,
            principalId = "user-alice",
            deviceId = "dev-alice",
            requestedAtEpochMs = System.currentTimeMillis(),
        )
        val aliceGrant = floorAuthority.requestFloor(aliceReq) as FloorArbitrationResult.Granted
        assertEquals(1L, aliceGrant.lease.fencingToken)

        // 2. Alice releases floor
        val releaseAlice = floorAuthority.releaseFloor(
            channelId = channelId,
            principalId = "user-alice",
            deviceId = "dev-alice",
            fencingToken = 1L,
        )
        assertTrue(releaseAlice.isSuccess)

        // 3. Bob acquires floor (fencingToken = 2)
        val bobReq = FloorRequest(
            requestId = "req-bob",
            channelId = channelId,
            principalId = "user-bob",
            deviceId = "dev-bob",
            requestedAtEpochMs = System.currentTimeMillis(),
        )
        val bobGrant = floorAuthority.requestFloor(bobReq) as FloorArbitrationResult.Granted
        assertEquals(2L, bobGrant.lease.fencingToken)
        assertEquals(2L, floorAuthority.getCurrentChannelEpoch(channelId))

        // 4. Delayed packet from Alice attempts to release using stale fencingToken = 1
        val lateRelease = floorAuthority.releaseFloor(
            channelId = channelId,
            principalId = "user-alice",
            deviceId = "dev-alice",
            fencingToken = 1L, // STALE!
        )

        assertTrue(lateRelease.isFailure)
        val errorMsg = lateRelease.exceptionOrNull()?.message ?: ""
        assertTrue("Error must specify stale fencing token rejection", errorMsg.contains("REJECTED_STALE_FENCING_TOKEN"))

        // 5. Bob's lease remains valid and active
        val currentLease = floorAuthority.getCurrentLease(channelId)!!
        assertEquals("user-bob", currentLease.holderPrincipalId)
        assertEquals(FloorState.GRANTED, currentLease.state)
        assertEquals(2L, currentLease.fencingToken)
    }
}
