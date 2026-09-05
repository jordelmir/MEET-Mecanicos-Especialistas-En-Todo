package com.elysium369.meet.ptt

/**
 * Vanguard PTT — Mission-Grade Push-to-Talk Domain.
 *
 * Truth Laws:
 * - FLOOR AUTHORITY DECIDES WHO MAY TRANSMIT.
 * - LIVEKIT TRANSPORTS THE MEDIA.
 * - THE CLIENT UI IS NOT THE AUTHORITY FOR EITHER.
 * - PTT_BUTTON_DOWN != FLOOR_REQUEST_ACCEPTED
 * - FLOOR_REQUESTED != FLOOR_GRANTED
 * - FLOOR_GRANTED != MEDIA_PUBLISH_AUTHORIZED
 * - MEDIA_PUBLISH_AUTHORIZED != AUDIO_DELIVERED
 * - AUDIO_RENDER_STARTED != HUMAN_HEARD_AUDIO
 * - QUEUED_PTT != LIVE_PTT
 * - SERVER_ACCEPTED != DEVICE_DELIVERED
 * - DEVICE_DELIVERED != PLAYED
 * - PLAYBACK_STARTED != HUMAN_LISTENED
 * - REMOTE_PRINCIPAL != MICROPHONE_AUTHORITY
 */

enum class PttChannelContextType {
    DIRECT,
    FAMILY,
    GROUP,
    RIDE,
    FLEET,
    WORKSHOP,
    SERVICE,
    INCIDENT,
    TEMPORARY
}

data class PttChannelBinding(
    val channelId: String,
    val name: String,
    val contextType: PttChannelContextType,
    val contextReferenceId: String,
    val createdAtEpochMs: Long,
    val isEncrypted: Boolean = true,
)

enum class PttChannelType {
    DIRECT,
    GROUP,
    VEHICLE,
    RIDE,
    FLEET,
    EMERGENCY;

    val isPersistent: Boolean get() = this in listOf(VEHICLE, FLEET, EMERGENCY)
}

enum class PttChannelState {
    ACTIVE,
    PAUSED,
    ARCHIVED;

    val isActive: Boolean get() = this == ACTIVE
}

enum class PttMemberRole {
    OWNER,
    MODERATOR,
    MEMBER,
    LISTENER;

    val canSpeak: Boolean get() = this in listOf(OWNER, MODERATOR, MEMBER)
    val canMute: Boolean get() = this in listOf(OWNER, MODERATOR)
    val canRemove: Boolean get() = this in listOf(OWNER, MODERATOR)
    val canDisband: Boolean get() = this == OWNER
}

enum class PttMemberState {
    JOINED,
    LEFT,
    MUTED,
    KICKED;

    val isActive: Boolean get() = this == JOINED
}

data class PttChannel(
    val channelId: String,
    val name: String,
    val type: PttChannelType,
    val state: PttChannelState = PttChannelState.ACTIVE,
    val ownerPrincipalId: String,
    val createdAtEpochMs: Long,
    val memberCount: Int = 0,
    val maxMembers: Int = 50,
    val isEncrypted: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "Channel name required" }
        require(name.length <= 50) { "Channel name too long" }
    }
}

data class PttMember(
    val channelId: String,
    val principalId: String,
    val role: PttMemberRole,
    val state: PttMemberState,
    val joinedAtEpochMs: Long,
    val lastActiveAtEpochMs: Long? = null,
    val mutedUntilEpochMs: Long? = null,
) {
    fun isCurrentlyActive(): Boolean = state == PttMemberState.JOINED
    fun isMuted(nowEpochMs: Long): Boolean =
        mutedUntilEpochMs?.let { it > nowEpochMs } ?: false
}

sealed interface PttJoinResult {
    data object ACCEPTED : PttJoinResult
    data class DENIED(val reason: String) : PttJoinResult
}

data class FloorGrant(
    val channelId: String,
    val principalId: String,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val sequence: Long,
    val priority: Int = 0,
) {
    fun isExpired(nowEpochMs: Long): Boolean = expiresAtEpochMs < nowEpochMs
    fun isActive(nowEpochMs: Long): Boolean = !isExpired(nowEpochMs)
}

sealed interface FloorRequestResult {
    data class GRANTED(val sequence: Long) : FloorRequestResult
    data object QUEUED : FloorRequestResult
    data class DENIED(val reason: String) : FloorRequestResult
}

data class PttTransmission(
    val transmissionId: String,
    val channelId: String,
    val senderPrincipalId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val durationMs: Long? = null,
    val sequence: Long,
    val floorWasGranted: Boolean,
    val audioWasDelivered: Boolean,
    val deliveryConfirmedBy: List<String> = emptyList(),
    val failureReason: String? = null,
) {
    val isComplete: Boolean get() = endedAtEpochMs != null
    val wasSuccessful: Boolean get() = floorWasGranted && audioWasDelivered
}

data class PttQueuedTransmission(
    val queueId: String,
    val channelId: String,
    val senderPrincipalId: String,
    val recipientPrincipalId: String,
    val audioDataEncrypted: ByteArray,
    val queuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val deliveredAtEpochMs: Long? = null,
    val attemptCount: Int = 0,
) {
    fun isExpired(nowEpochMs: Long): Boolean = expiresAtEpochMs < nowEpochMs
    fun isDelivered(): Boolean = deliveredAtEpochMs != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PttQueuedTransmission) return false
        return queueId == other.queueId
    }
    override fun hashCode(): Int = queueId.hashCode()
}

object PttPolicy {
    const val MAX_CHANNELS_PER_PERSON = 20
    const val MAX_TRANSMISSION_DURATION_MS = 60_000L
    const val FLOOR_GRANT_DURATION_MS = 30_000L
    const val FLOOR_TIMEOUT_MS = 10_000L
    const val MAX_QUEUED_TRANSMISSIONS = 50
    const val QUEUED_TRANSMISSION_EXPIRY_MS = 24 * 60 * 60 * 1000L

    fun canSpeak(role: PttMemberRole, state: PttMemberState): Boolean {
        return role.canSpeak && state == PttMemberState.JOINED
    }

    fun calculatePriority(role: PttMemberRole, channelType: PttChannelType): Int {
        var priority = 0
        if (channelType == PttChannelType.EMERGENCY) priority += 100
        when (role) {
            PttMemberRole.OWNER -> priority += 10
            PttMemberRole.MODERATOR -> priority += 5
            PttMemberRole.MEMBER -> priority += 1
            PttMemberRole.LISTENER -> priority += 0
        }
        return priority
    }
}

enum class FloorPriority(val level: Int) {
    NORMAL(10),
    IMPORTANT(50),
    EMERGENCY(100);

    fun canPreempt(other: FloorPriority): Boolean = this.level > other.level
}

enum class FloorState {
    IDLE,
    REQUESTED,
    ARBITRATING,
    GRANTING_MEDIA_PERMISSION,
    GRANTED,
    TRANSMITTING,
    REVOKING,
    RELEASED,
    EXPIRED,
    DENIED,
    PREEMPTING,
    FAILED_SAFE;

    val isHoldingFloor: Boolean get() = this in listOf(GRANTING_MEDIA_PERMISSION, GRANTED, TRANSMITTING)
    val canPublishAudio: Boolean get() = this in listOf(GRANTED, TRANSMITTING)
}

/**
 * Authoritative Floor Lease granted by FloorAuthority.
 * Carries a strictly monotonic fencingToken per channel to protect against split-brain / stale commands.
 */
data class FloorLease(
    val floorLeaseId: String,
    val channelId: String,
    val holderPrincipalId: String,
    val holderDeviceId: String,
    val requestId: String,
    val priority: FloorPriority,
    val state: FloorState,
    val fencingToken: Long,
    val grantedAt: Long,
    val expiresAt: Long,
    val lastHeartbeatAt: Long = grantedAt,
    val revocationReason: String? = null,
) {
    init {
        require(fencingToken >= 1L) { "fencingToken must be positive and monotonic" }
        require(expiresAt >= grantedAt) { "expiresAt cannot precede grantedAt" }
    }

    fun isExpired(nowEpochMs: Long): Boolean = nowEpochMs > expiresAt

    fun isValidHolder(principalId: String, deviceId: String, token: Long, nowEpochMs: Long): Boolean {
        return holderPrincipalId == principalId &&
            holderDeviceId == deviceId &&
            fencingToken == token &&
            state.canPublishAudio &&
            !isExpired(nowEpochMs)
    }
}

data class FloorRequest(
    val requestId: String,
    val channelId: String,
    val principalId: String,
    val deviceId: String,
    val priority: FloorPriority = FloorPriority.NORMAL,
    val requestedAtEpochMs: Long,
)

sealed interface FloorArbitrationResult {
    data class Granted(val lease: FloorLease) : FloorArbitrationResult
    data class Queued(val queuePosition: Int, val channelId: String) : FloorArbitrationResult
    data class Denied(val reason: String) : FloorArbitrationResult
    data class Preempted(val previousHolderPrincipalId: String, val newLease: FloorLease) : FloorArbitrationResult
    data class Error(val code: String, val message: String) : FloorArbitrationResult
}

data class PttTransmissionReceipt(
    val transmissionId: String,
    val channelId: String,
    val senderPrincipalId: String,
    val fencingToken: Long,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val audioDurationMs: Long,
    val floorWasGranted: Boolean,
    val mediaPermissionConfirmed: Boolean,
    val serverAccepted: Boolean,
    val deviceDeliveredCount: Int = 0,
    val playbackCompletedCount: Int = 0,
)
