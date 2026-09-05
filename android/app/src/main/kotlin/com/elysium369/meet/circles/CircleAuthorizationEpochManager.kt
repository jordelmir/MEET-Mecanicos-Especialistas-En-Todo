package com.elysium369.meet.circles

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CircleAuthorizationEpochManager — Immediate Realtime Revocation Engine.
 *
 * Problem:
 * Supabase Realtime authorises channels on initial WebSocket join and caches the authorization.
 * Simply updating a database RLS row does NOT sever a pre-existing hostile WebSocket connection.
 *
 * Solution:
 * Maintain an authoritative accessEpoch per Circle.
 * On membership revocation or principal block, accessEpoch is incremented.
 * All subsequent realtime broadcasts are published strictly to:
 *   "circle:<id>:epoch:<accessEpoch>"
 * The hostile client holding the old topic receives 0 subsequent packets.
 */
@Singleton
class CircleAuthorizationEpochManager @Inject constructor() {
    private val circleEpochs = ConcurrentHashMap<String, Long>()
    private val activeMembers = ConcurrentHashMap<String, MutableSet<String>>() // circleId -> Set<principalId>
    private val blockedPrincipals = ConcurrentHashMap<String, MutableSet<String>>() // principalId -> Set<blockedPrincipalId>

    fun registerCircle(circleId: String, initialEpoch: Long = 1L, initialMembers: Set<String> = emptySet()) {
        circleEpochs[circleId] = initialEpoch
        activeMembers[circleId] = ConcurrentHashMap.newKeySet<String>().apply { addAll(initialMembers) }
    }

    fun currentEpoch(circleId: String): Long {
        return circleEpochs.getOrPut(circleId) { 1L }
    }

    fun currentBroadcastTopic(circleId: String): String {
        return "circle:$circleId:epoch:${currentEpoch(circleId)}"
    }

    /**
     * Increments epoch and severs access for a removed member.
     * Returns the newly rotated topic.
     */
    fun revokeMemberAndRotateEpoch(circleId: String, revokedPrincipalId: String): String {
        activeMembers[circleId]?.remove(revokedPrincipalId)
        val newEpoch = (circleEpochs[circleId] ?: 1L) + 1L
        circleEpochs[circleId] = newEpoch
        return currentBroadcastTopic(circleId)
    }

    /**
     * Records a block between principals and immediately rotates epochs on all shared circles.
     */
    fun recordBlock(blockerPrincipalId: String, blockedPrincipalId: String): List<String> {
        blockedPrincipals.computeIfAbsent(blockerPrincipalId) { ConcurrentHashMap.newKeySet() }.add(blockedPrincipalId)

        val rotatedTopics = mutableListOf<String>()
        activeMembers.forEach { (circleId, members) ->
            if (members.contains(blockerPrincipalId) && members.contains(blockedPrincipalId)) {
                members.remove(blockedPrincipalId)
                val newEpoch = (circleEpochs[circleId] ?: 1L) + 1L
                circleEpochs[circleId] = newEpoch
                rotatedTopics.add(currentBroadcastTopic(circleId))
            }
        }
        return rotatedTopics
    }

    fun isAuthorizedForTopic(principalId: String, requestedTopic: String): Boolean {
        val parts = requestedTopic.split(":")
        if (parts.size != 4 || parts[0] != "circle" || parts[2] != "epoch") return false
        val circleId = parts[1]
        val epoch = parts[3].toLongOrNull() ?: return false

        // Must be the active epoch
        val activeEpoch = circleEpochs[circleId] ?: 1L
        if (epoch != activeEpoch) return false

        // Must be an active member
        val members = activeMembers[circleId] ?: return false
        return members.contains(principalId)
    }
}
