package com.elysium369.meet.ptt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FloorSingleHolderTest {

    private lateinit var floorAuthority: FloorAuthority
    private val liveKitPermissions = mutableMapOf<String, Boolean>() // principalId -> canPublish

    @Before
    fun setup() {
        liveKitPermissions.clear()
        floorAuthority = FloorAuthority { _, principalId, canPublish ->
            liveKitPermissions[principalId] = canPublish
            true // LiveKit succeeds
        }
    }

    @Test
    fun `concurrent floor race yields exactly one granted speaker and N-1 denials`() = runBlocking {
        val channelId = "channel-family-radio"
        val n = 10
        val deferreds = (1..n).map { i ->
            async(Dispatchers.IO) {
                val request = FloorRequest(
                    requestId = "req-user-$i",
                    channelId = channelId,
                    principalId = "user-$i",
                    deviceId = "device-$i",
                    priority = FloorPriority.NORMAL,
                    requestedAtEpochMs = System.currentTimeMillis(),
                )
                floorAuthority.requestFloor(request)
            }
        }

        val results = deferreds.awaitAll()

        val grantedCount = results.count { it is FloorArbitrationResult.Granted }
        val deniedCount = results.count { it is FloorArbitrationResult.Denied }

        assertEquals("Exactly 1 caller must be granted the floor", 1, grantedCount)
        assertEquals("Exactly N-1 callers must be denied", n - 1, deniedCount)

        // Only the single winner has LiveKit canPublish=true
        val activePublishers = liveKitPermissions.filter { it.value }
        assertEquals("Exactly 1 LiveKit participant can publish", 1, activePublishers.size)
    }

    @Test
    fun `emergency priority preempts normal floor holder with media revocation`() = runBlocking {
        val channelId = "channel-conv-1"

        // 1. Normal user acquires floor
        val normalReq = FloorRequest(
            requestId = "req-normal",
            channelId = channelId,
            principalId = "user-normal",
            deviceId = "dev-normal",
            priority = FloorPriority.NORMAL,
            requestedAtEpochMs = System.currentTimeMillis(),
        )
        val normalResult = floorAuthority.requestFloor(normalReq)
        assertTrue(normalResult is FloorArbitrationResult.Granted)
        assertTrue(liveKitPermissions["user-normal"] == true)

        // 2. Emergency user requests floor -> Preemption triggers!
        val emergencyReq = FloorRequest(
            requestId = "req-emergency",
            channelId = channelId,
            principalId = "user-medic",
            deviceId = "dev-medic",
            priority = FloorPriority.EMERGENCY,
            requestedAtEpochMs = System.currentTimeMillis(),
        )
        val emergencyResult = floorAuthority.requestFloor(emergencyReq)
        assertTrue(emergencyResult is FloorArbitrationResult.Granted)

        // Verify normal user was revoked from LiveKit publication
        assertEquals(false, liveKitPermissions["user-normal"])
        // Verify emergency user was granted LiveKit publication
        assertEquals(true, liveKitPermissions["user-medic"])
    }

    @Test
    fun `idempotent retry returns identical lease without creating duplicate`() = runBlocking {
        val channelId = "channel-conv-2"
        val req = FloorRequest(
            requestId = "req-idempotent-001",
            channelId = channelId,
            principalId = "user-alice",
            deviceId = "dev-alice",
            requestedAtEpochMs = System.currentTimeMillis(),
        )

        val first = floorAuthority.requestFloor(req) as FloorArbitrationResult.Granted
        val second = floorAuthority.requestFloor(req) as FloorArbitrationResult.Granted

        assertEquals(first.lease.floorLeaseId, second.lease.floorLeaseId)
        assertEquals(first.lease.fencingToken, second.lease.fencingToken)
        assertEquals(1L, floorAuthority.getCurrentChannelEpoch(channelId))
    }
}
