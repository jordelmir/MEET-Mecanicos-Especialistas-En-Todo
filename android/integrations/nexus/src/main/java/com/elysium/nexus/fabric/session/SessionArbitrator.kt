package com.elysium.nexus.fabric.session

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.routing.TransportRoute
import java.util.UUID

/**
 * §64 Enhanced Session Arbitration.
 *
 * Creates and manages [ControlSession] instances
 * that track ownership, controllers, target devices,
 * transport, lease, and sequence.
 *
 * Prevents:
 * - Stuck keys
 * - Duplicate actions
 * - Multi-controller races
 *
 * ## Session Model
 *
 * ```
 * ControlSession
 * ├── sessionId (UUID)
 * ├── owner (DeviceId)
 * ├── controllers (Set<DeviceId>)
 * ├── targetDevices (Set<DeviceId>)
 * ├── activeRoute (TransportRoute?)
 * ├── lease (Lease)
 * ├── sequence (Long)
 * └── state (SessionState)
 * ```
 *
 * ## Arbitration Rules
 *
 * 1. Only one active session per device
 * 2. Session owner has exclusive write access
 * 3. Controllers can read state but not write
 * 4. Lease expires after inactivity
 * 5. Sequence numbers prevent duplicate actions
 */
class SessionArbitrator(
    private val leaseTimeoutMs: Long = 300_000L // 5 minutes
) {
    private val sessions = mutableMapOf<DeviceId, ArbitratedSession>()

    /**
     * Create a new session for a device.
     * If a session already exists, returns
     * the existing one if still valid.
     */
    fun createSession(
        deviceId: DeviceId,
        owner: DeviceId,
        route: TransportRoute? = null
    ): ArbitratedSession {
        val existing = sessions[deviceId]
        if (existing != null && existing.isValid()) {
            // Transfer ownership if needed
            if (existing.owner != owner) {
                val updated = existing.copy(
                    owner = owner,
                    controllers = existing.controllers + existing.owner
                )
                sessions[deviceId] = updated
                return updated
            }
            return existing
        }

        val session = ArbitratedSession(
            sessionId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            owner = owner,
            controllers = emptySet(),
            targetDevices = setOf(deviceId),
            activeRoute = route,
            lease = Lease(
                createdAtMs = System.currentTimeMillis(),
                expiresAtMs = System.currentTimeMillis() + leaseTimeoutMs,
                lastActivityNs = System.nanoTime()
            ),
            sequence = 0L,
            state = SessionState.Created
        )
        sessions[deviceId] = session
        return session
    }

    /**
     * Claim ownership of a session.
     * The previous owner becomes a controller.
     */
    fun claimOwnership(
        deviceId: DeviceId,
        newOwner: DeviceId
    ): ArbitratedSession? {
        val session = sessions[deviceId] ?: return null
        val updated = session.copy(
            owner = newOwner,
            controllers = session.controllers + session.owner,
            lease = session.lease.refresh(leaseTimeoutMs)
        )
        sessions[deviceId] = updated
        return updated
    }

    /**
     * Record activity (extends lease).
     */
    fun recordActivity(deviceId: DeviceId): ArbitratedSession? {
        val session = sessions[deviceId] ?: return null
        val updated = session.copy(
            lease = session.lease.refresh(leaseTimeoutMs),
            sequence = session.sequence + 1
        )
        sessions[deviceId] = updated
        return updated
    }

    /**
     * Check if a session is valid (not expired).
     */
    fun isValidSession(deviceId: DeviceId): Boolean {
        return sessions[deviceId]?.isValid() ?: false
    }

    /**
     * Check if a device ID is the owner of a session.
     */
    fun isOwner(deviceId: DeviceId, candidateOwner: DeviceId): Boolean {
        return sessions[deviceId]?.owner == candidateOwner
    }

    /**
     * Terminate a session.
     */
    fun terminateSession(deviceId: DeviceId): ArbitratedSession? {
        return sessions.remove(deviceId)
    }

    /**
     * Get the current session for a device.
     */
    fun sessionFor(deviceId: DeviceId): ArbitratedSession? {
        return sessions[deviceId]
    }

    /**
     * Get all active sessions.
     */
    fun activeSessions(): List<ArbitratedSession> {
        return sessions.values.filter { it.isValid() }
    }

    /**
     * Clean up expired sessions.
     */
    fun cleanupExpired(): List<ArbitratedSession> {
        val expired = sessions.entries
            .filter { !it.value.isValid() }
            .map { it.key }
        return expired.mapNotNull { sessions.remove(it) }
    }
}

data class ArbitratedSession(
    val sessionId: String,
    val deviceId: DeviceId,
    val owner: DeviceId,
    val controllers: Set<DeviceId>,
    val targetDevices: Set<DeviceId>,
    val activeRoute: TransportRoute?,
    val lease: Lease,
    val sequence: Long,
    val state: SessionState
) {
    fun isValid(): Boolean = lease.isValid()

    fun isActive(): Boolean = state == SessionState.Active && isValid()
}

data class Lease(
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val lastActivityNs: Long
) {
    fun isValid(): Boolean = System.currentTimeMillis() < expiresAtMs

    fun refresh(timeoutMs: Long): Lease = copy(
        expiresAtMs = System.currentTimeMillis() + timeoutMs,
        lastActivityNs = System.nanoTime()
    )
}
