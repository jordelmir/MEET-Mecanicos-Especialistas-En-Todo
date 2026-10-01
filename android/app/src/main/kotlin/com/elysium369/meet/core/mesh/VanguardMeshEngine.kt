package com.elysium369.meet.core.mesh

import kotlinx.serialization.Serializable

/**
 * Pure deterministic routing/state simulator. No Android radio, persistence or cryptography.
 * A transition here models an input; it does not prove delivery or physical connectivity.
 * Production participation uses MeshCoordinator, a durable MeshRepository and native transports.
 * Production encrypted transfer remains disabled until an independently reviewed crypto provider
 * is installed, paired on two devices and tested against the physical verification matrix.
 */
// ─── Node Identity ───

@Serializable
data class MeshNodeId(
    val publicKeyFingerprint: String,
    val ephemeralId: String = java.util.UUID.randomUUID().toString(),
) {
    /** Desired simulator rotation interval; this model does not schedule rotation. */
    val rotationIntervalMs: Long = 15 * 60 * 1000L
}

enum class MeshNodeState {
    IDLE,           // Not participating
    DISCOVERING,    // BLE scanning for peers
    ADVERTISING,    // BLE advertising presence
    CONNECTED,      // Active data channel (Wi-Fi Direct)
    RELAYING,       // Forwarding messages for others
    BRIDGING,       // Store-carry-forward mode
}

@Serializable
data class MeshNode(
    val nodeId: MeshNodeId,
    val state: MeshNodeState = MeshNodeState.IDLE,
    val peerCount: Int = 0,
    val messagesRelayed: Int = 0,
    val messagesInCustody: Int = 0,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val relayOnlyWhileCharging: Boolean = false,
    val maxCustodyMessages: Int = 100,
    val maxHopsPerMessage: Int = 10,
) {
    val canRelay: Boolean
        get() = when {
            relayOnlyWhileCharging && !isCharging -> false
            batteryPercent < 15 -> false // preserve battery
            messagesInCustody >= maxCustodyMessages -> false
            else -> true
        }
}

// ─── Mesh Messages ───

enum class MeshMessageType {
    DIRECT,         // Point-to-point encrypted message
    BROADCAST,      // Emergency broadcast to all nearby
    DISCOVERY,      // "I'm here, these are my capabilities"
    ACK,            // Delivery acknowledgment
    RELAY_REQUEST,  // "Please carry this message for me"
}

@Serializable
data class MeshMessage(
    val messageId: String,
    val type: MeshMessageType,
    val senderFingerprint: String,
    val recipientFingerprint: String? = null, // null = broadcast
    val encryptedPayload: String,
    val ttlSeconds: Int = 86400, // 24 hours default
    val maxHops: Int = 10,
    val currentHops: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long = System.currentTimeMillis() + (ttlSeconds * 1000L),
    val payloadSizeBytes: Int = 0,
    val isEmergencyBroadcast: Boolean = false,
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAtEpochMs

    val hasHopsRemaining: Boolean
        get() = currentHops < maxHops

    val isDeliverable: Boolean
        get() = !isExpired && hasHopsRemaining

    /** Forwarded message with hop count incremented */
    fun forwarded(): MeshMessage = copy(currentHops = currentHops + 1)
}

// ─── Discovery Advertisement ───

@Serializable
data class MeshAdvertisement(
    val ephemeralId: String,
    val capabilities: Set<MeshCapability>,
    val meshVersion: Int = 1,
    val signalStrengthDbm: Int = -70,
    val supportsWifiDirect: Boolean = true,
    val supportsBle: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class MeshCapability {
    RELAY,                  // Will forward messages
    STORE_CARRY_FORWARD,    // Will physically carry messages
    EMERGENCY_RESPONDER,    // Can respond to emergencies
    GATEWAY,                // Has internet, can bridge to cloud
    HIGH_BANDWIDTH,         // Wi-Fi Direct available
}

// ─── Peer Connection ───

@Serializable
data class MeshPeer(
    val ephemeralId: String,
    val fingerprint: String? = null, // revealed after trust handshake
    val signalStrengthDbm: Int = -70,
    val estimatedDistanceMeters: Int = 0,
    val capabilities: Set<MeshCapability> = emptySet(),
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val connectionState: PeerConnectionState = PeerConnectionState.DISCOVERED,
)

enum class PeerConnectionState {
    DISCOVERED,     // BLE advertisement seen
    HANDSHAKING,    // Key exchange in progress
    AUTHENTICATED,  // Crypto handshake complete
    DATA_CHANNEL,   // Wi-Fi Direct channel open
    DISCONNECTED,   // Lost contact
}

// ─── The Mesh Engine ───

class MeshRoutingSimulator {

    companion object {
        /** Simulator assumption only; physical range has not been measured. */
        const val BLE_RANGE_METERS = 100
        /** Simulator assumption only; physical range has not been measured. */
        const val WIFI_DIRECT_RANGE_METERS = 200
        /** Max message size for BLE */
        const val BLE_MAX_PAYLOAD_BYTES = 512
        /** Max message size for Wi-Fi Direct */
        const val WIFI_DIRECT_MAX_PAYLOAD_BYTES = 10_000_000 // 10 MB
        /** Ephemeral ID rotation interval */
        const val EPHEMERAL_ROTATION_MS = 15 * 60 * 1000L
        /** Max custody messages per node */
        const val DEFAULT_MAX_CUSTODY = 100
        /** Emergency broadcast TTL */
        const val EMERGENCY_TTL_SECONDS = 3600 // 1 hour
    }

    private var localNode: MeshNode? = null
    private val peers = mutableListOf<MeshPeer>()
    private val messageStore = mutableListOf<MeshMessage>()
    private val deliveredIds = mutableSetOf<String>()
    private val seenIds = mutableSetOf<String>()

    // ─── Node Lifecycle ───

    fun initialize(fingerprint: String): MeshNode {
        val node = MeshNode(
            nodeId = MeshNodeId(publicKeyFingerprint = fingerprint),
            state = MeshNodeState.DISCOVERING,
        )
        localNode = node
        return node
    }

    fun localNode(): MeshNode? = localNode

    fun startAdvertising(): Boolean {
        localNode = localNode?.copy(state = MeshNodeState.ADVERTISING) ?: return false
        return true
    }

    fun stopAdvertising(): Boolean {
        localNode = localNode?.copy(state = MeshNodeState.IDLE) ?: return false
        return true
    }

    // ─── Peer Discovery ───

    fun onPeerDiscovered(peer: MeshPeer) {
        val existing = peers.indexOfFirst { it.ephemeralId == peer.ephemeralId }
        if (existing >= 0) {
            peers[existing] = peer.copy(lastSeenEpochMs = System.currentTimeMillis())
        } else {
            peers.add(peer)
        }
        localNode = localNode?.copy(peerCount = peers.size)
    }

    fun activePeers(): List<MeshPeer> {
        val threshold = System.currentTimeMillis() - 60_000 // 1 min
        return peers.filter { it.lastSeenEpochMs > threshold }
    }

    fun gatewayPeers(): List<MeshPeer> {
        return activePeers().filter { MeshCapability.GATEWAY in it.capabilities }
    }

    // ─── Message Operations ───

    /**
     * Sends a direct encrypted message to a specific recipient.
     * Stores intent in local custody. Actual transport and signed receipts live in
     * CommunicationNearbyTransport; this model must never claim a radio delivery.
     */
    fun sendDirectMessage(
        recipientFingerprint: String,
        encryptedPayload: String,
    ): MeshMessage {
        val node = localNode ?: throw IllegalStateException("Node not initialized")
        val message = MeshMessage(
            messageId = "msg-${System.currentTimeMillis()}-${messageStore.size}",
            type = MeshMessageType.DIRECT,
            senderFingerprint = node.nodeId.publicKeyFingerprint,
            recipientFingerprint = recipientFingerprint,
            encryptedPayload = encryptedPayload,
            payloadSizeBytes = encryptedPayload.length,
        )

        // This legacy custody model has no transport receipt. Presence is never delivery.
        messageStore.add(message)
        seenIds.add(message.messageId)
        localNode = localNode?.copy(messagesInCustody = messageStore.count { it.isDeliverable })

        return message
    }

    /**
     * Broadcasts an emergency message to ALL nearby nodes.
     * Emergency broadcasts have max priority and shorter TTL.
     */
    fun broadcastEmergency(
        encryptedPayload: String,
    ): MeshMessage {
        val node = localNode ?: throw IllegalStateException("Node not initialized")
        val message = MeshMessage(
            messageId = "emergency-${System.currentTimeMillis()}",
            type = MeshMessageType.BROADCAST,
            senderFingerprint = node.nodeId.publicKeyFingerprint,
            recipientFingerprint = null, // broadcast = all
            encryptedPayload = encryptedPayload,
            ttlSeconds = EMERGENCY_TTL_SECONDS,
            maxHops = 20, // more hops for emergencies
            isEmergencyBroadcast = true,
        )
        messageStore.add(message)
        return message
    }

    /**
     * Called when a relay message is received from another node.
     * Decides whether to accept and forward.
     */
    fun onRelayMessageReceived(message: MeshMessage): RelayDecision {
        val node = localNode ?: return RelayDecision.REJECTED_NOT_INITIALIZED

        // Already delivered?
        if (message.messageId in seenIds) {
            return RelayDecision.DUPLICATE
        }

        // Expired?
        if (!message.isDeliverable) {
            return RelayDecision.EXPIRED
        }

        // Can this node relay?
        if (!node.canRelay) {
            return RelayDecision.REJECTED_CAPACITY
        }

        // Is this message for us?
        if (message.recipientFingerprint == node.nodeId.publicKeyFingerprint) {
            seenIds.add(message.messageId)
            // Local model receipt is not a signed radio ACK and cannot increase delivery statistics.
            return RelayDecision.DELIVERED_TO_SELF
        }

        // Accept for relay — track ID to prevent duplicate acceptance
        seenIds.add(message.messageId)
        val forwarded = message.forwarded()
        messageStore.add(forwarded)
        localNode = localNode?.copy(
            messagesInCustody = messageStore.count { it.isDeliverable },
        )
        return RelayDecision.ACCEPTED
    }

    /**
     * Returns messages in custody that should be forwarded
     * to a specific peer (if they're the recipient or can relay).
     */
    fun messagesForPeer(peerFingerprint: String): List<MeshMessage> {
        return messageStore.filter { msg ->
            msg.isDeliverable && (
                msg.recipientFingerprint == peerFingerprint ||
                    msg.type == MeshMessageType.BROADCAST
                )
        }
    }

    /**
     * Purge expired messages from custody.
     */
    fun purgeExpired(): Int {
        val before = messageStore.size
        messageStore.removeAll { it.isExpired || !it.hasHopsRemaining }
        val purged = before - messageStore.size
        localNode = localNode?.copy(
            messagesInCustody = messageStore.count { it.isDeliverable },
        )
        return purged
    }

    // ─── Mesh Statistics ───

    fun meshStats(): MeshStats {
        return MeshStats(
            nodeState = localNode?.state ?: MeshNodeState.IDLE,
            activePeers = activePeers().size,
            gatewayPeers = gatewayPeers().size,
            messagesInCustody = messageStore.count { it.isDeliverable },
            totalRelayed = localNode?.messagesRelayed ?: 0,
            totalDelivered = deliveredIds.size,
            emergencyBroadcasts = messageStore.count { it.isEmergencyBroadcast },
        )
    }
}

enum class RelayDecision {
    ACCEPTED,               // Will carry and forward
    DELIVERED_TO_SELF,      // Message was for this node
    DUPLICATE,              // Already seen this message
    EXPIRED,                // TTL or hops exhausted
    REJECTED_CAPACITY,      // Node is full or battery low
    REJECTED_NOT_INITIALIZED, // Node not started
}

@Serializable
data class MeshStats(
    val nodeState: MeshNodeState,
    val activePeers: Int,
    val gatewayPeers: Int,
    val messagesInCustody: Int,
    val totalRelayed: Int,
    val totalDelivered: Int,
    val emergencyBroadcasts: Int,
)

/** Compatibility name for callers of the historical routing simulator. */
typealias VanguardMeshEngine = MeshRoutingSimulator
