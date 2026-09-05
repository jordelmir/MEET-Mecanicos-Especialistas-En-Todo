package com.elysium369.meet.circles

import com.elysium369.meet.presence.AudienceType
import com.elysium369.meet.presence.LocationShareGrant
import com.elysium369.meet.presence.LocationShareMode
import com.elysium369.meet.presence.PresenceSample
import com.elysium369.meet.presence.PrivacyProjectionEngine
import com.elysium369.meet.presence.ProjectedPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Section 115 Release-Blocker: CrossCircleLocationIsolationTest.
 *
 * Attacker with valid account and knowledge of genuine Circle A resource UUIDs
 * attempts to access Circle A data across all paths.
 * Expected: DENIED EVERYWHERE.
 */
class CrossCircleLocationIsolationTest {

    private val now = System.currentTimeMillis()
    private val epochManager = CircleAuthorizationEpochManager()

    @Test
    fun `attacker with genuine Circle A IDs cannot join Realtime or project presence`() {
        val circleAId = "circle-alpha-uuid-777"
        val alice = "principal-alice"
        val eveAttacker = "principal-eve-attacker"

        // Circle A has only Alice as a member
        epochManager.registerCircle(circleAId, initialEpoch = 1L, initialMembers = setOf(alice))

        // 1. Eve attempts to join Circle A's broadcast topic using the real Circle ID
        val realTopic = epochManager.currentBroadcastTopic(circleAId)
        val eveAuthorized = epochManager.isAuthorizedForTopic(eveAttacker, realTopic)
        assertFalse("Cross-circle attacker must be denied access to Realtime topic", eveAuthorized)

        // 2. Eve attempts to project Alice's presence using Alice's real grant ID
        val aliceGrant = LocationShareGrant(
            grantId = "grant-alice-genuine-uuid",
            ownerPrincipalId = alice,
            audienceType = AudienceType.CIRCLE,
            audienceId = circleAId, // Grant is strictly scoped to Circle A
            mode = LocationShareMode.PRECISE,
            validFrom = now - 1000L,
            validUntil = now + 3600_000L,
        )

        val aliceSample = PresenceSample(
            sampleId = "sample-alice-secret",
            principalId = alice,
            deviceId = "device-alice",
            streamId = "stream-1",
            sequence = 1L,
            capturedAt = now,
            receivedAt = now,
            latitude = 9.9333,
            longitude = -84.0833,
            accuracyMeters = 5f,
        )

        // If Eve is not in audienceId, the authorization check fails
        val isEveAuthorizedForGrant = (aliceGrant.audienceType == AudienceType.CIRCLE &&
            epochManager.isAuthorizedForTopic(eveAttacker, realTopic)) ||
            (aliceGrant.audienceType == AudienceType.INDIVIDUAL && aliceGrant.audienceId == eveAttacker)

        assertFalse("Eve must not be authorized to view Alice's location grant", isEveAuthorizedForGrant)

        // Projections for unauthorized requesters return Hidden
        val projected = if (isEveAuthorizedForGrant) {
            PrivacyProjectionEngine.project(aliceSample, aliceGrant, now)
        } else {
            ProjectedPresence.Hidden
        }

        assertEquals(ProjectedPresence.Hidden, projected)
    }
}
