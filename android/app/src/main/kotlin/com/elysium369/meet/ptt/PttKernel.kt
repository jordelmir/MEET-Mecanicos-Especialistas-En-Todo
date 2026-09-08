package com.elysium369.meet.ptt

import android.util.Log
import com.elysium369.meet.data.local.dao.PttChannelDao
import com.elysium369.meet.data.local.entities.PttChannelEntity
import com.elysium369.meet.data.local.entities.PttChannelMemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PttKernel — Singleton authority for PTT floor control.
 *
 * Channels and members are backed by Room for persistence.
 * Floor grants, transmissions, and queued transmissions are session-scoped
 * (RAM-only) — they are transient and regenerate on restart.
 *
 * Floor control determines who can speak. LiveKit transports audio.
 * Neither invents delivery or listening.
 */
@Singleton
class PttKernel @Inject constructor(
    private val channelDao: PttChannelDao,
) {

    // Session-scoped (not persisted — regenerated on restart)
    private val floorGrants = mutableMapOf<String, FloorGrant>()
    private val transmissions = mutableMapOf<String, MutableList<PttTransmission>>()
    private val queuedTransmissions = mutableMapOf<String, MutableList<PttQueuedTransmission>>()

    /** Create a PTT channel. */
    suspend fun createChannel(
        name: String,
        type: PttChannelType,
        ownerPrincipalId: String,
    ): PttChannel {
        val channelId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val channel = PttChannel(
            channelId = channelId,
            name = name,
            type = type,
            state = PttChannelState.ACTIVE,
            ownerPrincipalId = ownerPrincipalId,
            createdAtEpochMs = now,
            memberCount = 1,
        )

        channelDao.upsertChannel(channel.toEntity())
        channelDao.upsertMember(PttChannelMemberEntity(
            channelId = channelId,
            principalId = ownerPrincipalId,
            role = PttMemberRole.OWNER.name,
            state = PttMemberState.JOINED.name,
            joinedAtEpochMs = now,
        ))

        Log.i("PttKernel", "Created channel: $channelId ($name, $type)")
        return channel
    }

    /** Join a channel. */
    suspend fun joinChannel(
        channelId: String,
        principalId: String,
        role: PttMemberRole = PttMemberRole.MEMBER,
    ): PttJoinResult {
        val channelEntity = channelDao.getChannelById(channelId)
            ?: return PttJoinResult.DENIED("Channel not found")
        val channel = channelEntity.toDomain()
        if (!channel.state.isActive) return PttJoinResult.DENIED("Channel not active")

        val now = System.currentTimeMillis()
        val member = PttChannelMemberEntity(
            channelId = channelId,
            principalId = principalId,
            role = role.name,
            state = PttMemberState.JOINED.name,
            joinedAtEpochMs = now,
        )
        channelDao.upsertMember(member)

        Log.i("PttKernel", "$principalId joined channel $channelId")
        return PttJoinResult.ACCEPTED
    }

    /** Leave a channel. */
    suspend fun leaveChannel(channelId: String, principalId: String) {
        channelDao.removeMember(channelId, principalId)

        // Release floor if this user held it
        floorGrants.remove(channelId)
        Log.i("PttKernel", "$principalId left channel $channelId")
    }

    /** Request the floor (PTT button down). */
    fun requestFloor(
        channelId: String,
        principalId: String,
    ): FloorRequestResult {
        // Floor control is session-scoped — check in-memory
        // Channel existence check (read from DAO in real usage, but floor is transient)
        val currentGrant = floorGrants[channelId]
        val now = System.currentTimeMillis()

        if (currentGrant != null && currentGrant.isActive(now)) {
            if (currentGrant.principalId == principalId) {
                return FloorRequestResult.DENIED("You already have the floor")
            }
            return FloorRequestResult.QUEUED
        }

        // Grant floor
        val grant = FloorGrant(
            channelId = channelId,
            principalId = principalId,
            grantedAtEpochMs = now,
            expiresAtEpochMs = now + PttPolicy.FLOOR_GRANT_DURATION_MS,
            sequence = (currentGrant?.sequence ?: 0) + 1,
            priority = 1,
        )
        floorGrants[channelId] = grant

        Log.i("PttKernel", "Floor granted to $principalId on $channelId (seq=${grant.sequence})")
        return FloorRequestResult.GRANTED(grant.sequence)
    }

    /** Release the floor (PTT button up). */
    fun releaseFloor(channelId: String, principalId: String) {
        val grant = floorGrants[channelId]
        if (grant?.principalId == principalId) {
            floorGrants.remove(channelId)
            Log.i("PttKernel", "Floor released by $principalId on $channelId")
        }
    }

    /** Get current floor state for a channel. */
    fun getFloorState(channelId: String): FloorState {
        val grant = floorGrants[channelId] ?: return FloorState.IDLE
        val now = System.currentTimeMillis()
        return if (grant.isActive(now)) FloorState.GRANTED else FloorState.IDLE
    }

    /** Get current floor holder for a channel. */
    fun getFloorHolder(channelId: String): String? {
        val grant = floorGrants[channelId] ?: return null
        return if (grant.isActive(System.currentTimeMillis())) grant.principalId else null
    }

    /** Record a transmission. */
    fun recordTransmission(
        channelId: String,
        senderPrincipalId: String,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long,
        floorWasGranted: Boolean,
        audioWasDelivered: Boolean,
        deliveryConfirmedBy: List<String> = emptyList(),
    ): PttTransmission {
        val channelTransmissions = transmissions.getOrPut(channelId) { mutableListOf() }
        val sequence = (channelTransmissions.lastOrNull()?.sequence ?: 0) + 1

        val transmission = PttTransmission(
            transmissionId = UUID.randomUUID().toString(),
            channelId = channelId,
            senderPrincipalId = senderPrincipalId,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            durationMs = endedAtEpochMs - startedAtEpochMs,
            sequence = sequence,
            floorWasGranted = floorWasGranted,
            audioWasDelivered = audioWasDelivered,
            deliveryConfirmedBy = deliveryConfirmedBy,
        )
        channelTransmissions.add(transmission)

        Log.i("PttKernel", "Recorded transmission: ${transmission.transmissionId} on $channelId")
        return transmission
    }

    /** Queue a transmission for offline delivery. */
    fun queueTransmission(
        channelId: String,
        senderPrincipalId: String,
        recipientPrincipalId: String,
        audioDataEncrypted: ByteArray,
    ): PttQueuedTransmission? {
        val channelQueue = queuedTransmissions.getOrPut(recipientPrincipalId) { mutableListOf() }
        if (channelQueue.size >= PttPolicy.MAX_QUEUED_TRANSMISSIONS) return null

        val queue = PttQueuedTransmission(
            queueId = UUID.randomUUID().toString(),
            channelId = channelId,
            senderPrincipalId = senderPrincipalId,
            recipientPrincipalId = recipientPrincipalId,
            audioDataEncrypted = audioDataEncrypted,
            queuedAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = System.currentTimeMillis() + PttPolicy.QUEUED_TRANSMISSION_EXPIRY_MS,
        )
        channelQueue.add(queue)
        return queue
    }

    /** Get queued transmissions for a user. */
    fun getQueuedTransmissions(principalId: String): List<PttQueuedTransmission> {
        val now = System.currentTimeMillis()
        return queuedTransmissions[principalId]?.filter { !it.isExpired(now) && !it.isDelivered() }
            ?: emptyList()
    }

    /** Get channels for a principal. */
    suspend fun getChannelsForPrincipal(principalId: String): List<PttChannel> {
        return channelDao.getChannelsForPrincipal(principalId).map { it.toDomain() }
    }

    /** Get channels for a principal as Flow. */
    fun getChannelsForPrincipalFlow(principalId: String): Flow<List<PttChannel>> {
        return channelDao.getChannelsForPrincipalFlow(principalId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /** Get active channels as Flow. */
    fun getActiveChannelsFlow(): Flow<List<PttChannel>> {
        return channelDao.getActiveChannelsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /** Cleanup expired floor grants and queued transmissions. */
    fun cleanup() {
        val now = System.currentTimeMillis()
        floorGrants.entries.removeIf { (_, grant) -> grant.isExpired(now) }
        queuedTransmissions.values.forEach { queue ->
            queue.removeAll { it.isExpired(now) }
        }
    }

    // ── Helpers ──

    // ── Entity ↔ Domain mapping ──

    private fun PttChannel.toEntity() = PttChannelEntity(
        channelId = channelId,
        name = name,
        type = type.name,
        state = state.name,
        ownerPrincipalId = ownerPrincipalId,
        createdAtEpochMs = createdAtEpochMs,
        memberCount = memberCount,
        maxMembers = maxMembers,
        isEncrypted = isEncrypted,
    )

    private fun PttChannelEntity.toDomain() = PttChannel(
        channelId = channelId,
        name = name,
        type = try { PttChannelType.valueOf(type) } catch (_: Exception) { PttChannelType.GROUP },
        state = try { PttChannelState.valueOf(state) } catch (_: Exception) { PttChannelState.ACTIVE },
        ownerPrincipalId = ownerPrincipalId,
        createdAtEpochMs = createdAtEpochMs,
        memberCount = memberCount,
        maxMembers = maxMembers,
        isEncrypted = isEncrypted,
    )
}
