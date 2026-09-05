package com.elysium369.meet.ptt

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PttKernel — Singleton authority for PTT floor control.
 * Floor control determines who can speak. LiveKit transports audio.
 * Neither invents delivery or listening.
 */
@Singleton
class PttKernel @Inject constructor() {

    private val channels = mutableMapOf<String, PttChannel>()
    private val channelMembers = mutableMapOf<String, MutableList<PttMember>>()
    private val floorGrants = mutableMapOf<String, FloorGrant>()
    private val transmissions = mutableMapOf<String, MutableList<PttTransmission>>()
    private val queuedTransmissions = mutableMapOf<String, MutableList<PttQueuedTransmission>>()

    /** Create a PTT channel. */
    fun createChannel(
        name: String,
        type: PttChannelType,
        ownerPrincipalId: String,
    ): PttChannel {
        val channelId = UUID.randomUUID().toString()
        val channel = PttChannel(
            channelId = channelId,
            name = name,
            type = type,
            state = PttChannelState.ACTIVE,
            ownerPrincipalId = ownerPrincipalId,
            createdAtEpochMs = System.currentTimeMillis(),
            memberCount = 1,
        )
        channels[channelId] = channel

        val ownerMember = PttMember(
            channelId = channelId,
            principalId = ownerPrincipalId,
            role = PttMemberRole.OWNER,
            state = PttMemberState.JOINED,
            joinedAtEpochMs = System.currentTimeMillis(),
        )
        channelMembers[channelId] = mutableListOf(ownerMember)

        Log.i("PttKernel", "Created channel: $channelId ($name, $type)")
        return channel
    }

    /** Join a channel. */
    fun joinChannel(
        channelId: String,
        principalId: String,
        role: PttMemberRole = PttMemberRole.MEMBER,
    ): PttJoinResult {
        val channel = channels[channelId] ?: return PttJoinResult.DENIED("Channel not found")
        if (!channel.state.isActive) return PttJoinResult.DENIED("Channel not active")

        val existing = channelMembers[channelId]?.firstOrNull { it.principalId == principalId }
        if (existing != null && existing.state == PttMemberState.JOINED) {
            return PttJoinResult.DENIED("Already joined")
        }

        val member = PttMember(
            channelId = channelId,
            principalId = principalId,
            role = if (existing != null) existing.role else role,
            state = PttMemberState.JOINED,
            joinedAtEpochMs = System.currentTimeMillis(),
            lastActiveAtEpochMs = System.currentTimeMillis(),
        )

        if (existing != null) {
            channelMembers[channelId]?.removeAll { it.principalId == principalId }
        }
        channelMembers[channelId]?.add(member)

        channels[channelId]?.let { ch ->
            channels[channelId] = ch.copy(memberCount = channelMembers[channelId]?.size ?: 0)
        }

        Log.i("PttKernel", "$principalId joined channel $channelId")
        return PttJoinResult.ACCEPTED
    }

    /** Leave a channel. */
    fun leaveChannel(channelId: String, principalId: String) {
        channelMembers[channelId]?.removeAll { it.principalId == principalId }
        channels[channelId]?.let { ch ->
            channels[channelId] = ch.copy(memberCount = channelMembers[channelId]?.size ?: 0)
        }

        // Release floor if this user held it
        floorGrants.remove(channelId)
        Log.i("PttKernel", "$principalId left channel $channelId")
    }

    /** Request the floor (PTT button down). */
    fun requestFloor(
        channelId: String,
        principalId: String,
    ): FloorRequestResult {
        val channel = channels[channelId] ?: return FloorRequestResult.DENIED("Channel not found")
        val member = channelMembers[channelId]?.firstOrNull { it.principalId == principalId }
            ?: return FloorRequestResult.DENIED("Not a member")
        if (!PttPolicy.canSpeak(member.role, member.state)) {
            return FloorRequestResult.DENIED("Cannot speak in this channel")
        }

        val currentGrant = floorGrants[channelId]
        val now = System.currentTimeMillis()

        if (currentGrant != null && currentGrant.isActive(now)) {
            if (currentGrant.principalId == principalId) {
                return FloorRequestResult.DENIED("You already have the floor")
            }
            // Queue the request
            val priority = PttPolicy.calculatePriority(member.role, channel.type)
            if (priority <= currentGrant.priority) {
                Log.i("PttKernel", "Floor queued for $principalId on $channelId (priority $priority)")
                return FloorRequestResult.QUEUED
            }
            // Higher priority — preempt
            floorGrants.remove(channelId)
        }

        // Grant floor
        val grant = FloorGrant(
            channelId = channelId,
            principalId = principalId,
            grantedAtEpochMs = now,
            expiresAtEpochMs = now + PttPolicy.FLOOR_GRANT_DURATION_MS,
            sequence = (currentGrant?.sequence ?: 0) + 1,
            priority = PttPolicy.calculatePriority(member.role, channel.type),
        )
        floorGrants[channelId] = grant

        // Update member last active
        channelMembers[channelId]?.replaceAll { m ->
            if (m.principalId == principalId) m.copy(lastActiveAtEpochMs = now) else m
        }

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
    fun getChannelsForPrincipal(principalId: String): List<PttChannel> {
        return channels.values.filter { channel ->
            channelMembers[channel.channelId]?.any {
                it.principalId == principalId && it.state == PttMemberState.JOINED
            } == true
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
}
