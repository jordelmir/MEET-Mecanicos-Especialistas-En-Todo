package com.elysium.nexus.fabric.session

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.routing.TransportRoute

/**
 * The §4.5 control session.
 *
 * A [ControlSession] tracks the lifecycle of a user's
 * interaction with a single device. The session is the
 * scope for permission gates, route negotiation,
 * disconnect neutralization, and evidence recording.
 *
 * ## Lifecycle
 *
 * ```
 * Created → PermissionCheck → RouteNegotiated → Active
 *               ↓                                 ↓
 *           PermissionDenied              Disconnecting → Terminated
 * ```
 *
 * The [SessionManager] enforces one active session per
 * device. A new session for the same device terminates
 * the previous one (with neutralization).
 */
data class ControlSession(
    /** Unique session id (UUID). */
    val sessionId: String,
    /** The target device for this session. */
    val deviceId: DeviceId,
    /** Current session state. */
    val state: SessionState = SessionState.Created,
    /** The negotiated route (set after RouteNegotiated). */
    val activeRoute: TransportRoute? = null,
    /** Wall-clock nanos when session was created. */
    val createdNs: Long = System.nanoTime(),
    /** Wall-clock nanos of last activity (action dispatched). */
    val lastActivityNs: Long = createdNs,
    /** Number of actions dispatched in this session. */
    val actionCount: Long = 0L
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must be non-blank." }
        require(createdNs >= 0L) { "createdNs must be non-negative." }
    }

    /** @return true if the session is in a terminal state. */
    val isTerminated: Boolean get() = state == SessionState.Terminated
            || state == SessionState.PermissionDenied

    /** @return true if the session can dispatch actions. */
    val isActive: Boolean get() = state == SessionState.Active

    /** @return a copy with updated last activity and incremented action count. */
    fun recordActivity(nowNs: Long = System.nanoTime()): ControlSession =
        copy(lastActivityNs = nowNs, actionCount = actionCount + 1)

    /** @return a copy transitioned to the new state. */
    fun transitionTo(newState: SessionState): ControlSession {
        require(isValidTransition(state, newState)) {
            "Invalid session transition: $state → $newState"
        }
        return copy(state = newState)
    }

    /** @return a copy with the active route set. */
    fun withRoute(route: TransportRoute): ControlSession =
        copy(activeRoute = route, state = SessionState.RouteNegotiated)

    companion object {
        /** Valid state transitions. */
        fun isValidTransition(from: SessionState, to: SessionState): Boolean = when (from) {
            SessionState.Created -> to in setOf(
                SessionState.PermissionCheck,
                SessionState.Terminated
            )
            SessionState.PermissionCheck -> to in setOf(
                SessionState.RouteNegotiated,
                SessionState.PermissionDenied,
                SessionState.Terminated
            )
            SessionState.RouteNegotiated -> to in setOf(
                SessionState.Active,
                SessionState.Terminated
            )
            SessionState.Active -> to in setOf(
                SessionState.Disconnecting,
                SessionState.Terminated
            )
            SessionState.Disconnecting -> to == SessionState.Terminated
            SessionState.PermissionDenied -> false
            SessionState.Terminated -> false
        }
    }
}

/**
 * Session lifecycle states. See [ControlSession] for
 * the valid transition graph.
 */
enum class SessionState {
    /** Just created; no work done yet. */
    Created,
    /** Checking permissions for the target route. */
    PermissionCheck,
    /** Route negotiated; ready to activate. */
    RouteNegotiated,
    /** Session is actively dispatching actions. */
    Active,
    /** Disconnecting: neutralizing inflight inputs. */
    Disconnecting,
    /** Permission was denied; terminal state. */
    PermissionDenied,
    /** Session is done; all resources released. */
    Terminated
}

/**
 * Manages the lifecycle of [ControlSession]s.
 *
 * Enforces one active session per device. Creating a
 * session for a device that already has one terminates
 * the previous session (with [onSessionTerminated]
 * callback for neutralization).
 */
class SessionManager(
    /** Called when a session is forcefully terminated. */
    private val onSessionTerminated: (ControlSession) -> Unit = {}
) {
    private val sessions = mutableMapOf<DeviceId, ControlSession>()

    /** All active (non-terminated) sessions. */
    fun activeSessions(): List<ControlSession> =
        sessions.values.filter { !it.isTerminated }

    /** Get the session for a device, or null. */
    fun sessionFor(deviceId: DeviceId): ControlSession? = sessions[deviceId]

    /**
     * Create a new session for [deviceId]. If a previous
     * session exists, it is terminated first (neutralization
     * callback fires).
     */
    fun createSession(
        sessionId: String,
        deviceId: DeviceId,
        nowNs: Long = System.nanoTime()
    ): ControlSession {
        // Terminate previous session for same device
        val previous = sessions[deviceId]
        if (previous != null && !previous.isTerminated) {
            val terminated = if (previous.state == SessionState.Active) {
                previous.transitionTo(SessionState.Disconnecting)
                    .transitionTo(SessionState.Terminated)
            } else if (ControlSession.isValidTransition(previous.state, SessionState.Terminated)) {
                previous.transitionTo(SessionState.Terminated)
            } else {
                previous.copy(state = SessionState.Terminated)
            }
            sessions[deviceId] = terminated
            onSessionTerminated(terminated)
        }

        val session = ControlSession(
            sessionId = sessionId,
            deviceId = deviceId,
            createdNs = nowNs
        )
        sessions[deviceId] = session
        return session
    }

    /**
     * Update the session in the manager.
     * The session is identified by [deviceId].
     */
    fun updateSession(session: ControlSession) {
        sessions[session.deviceId] = session
    }

    /**
     * Terminate the session for [deviceId].
     * Returns the terminated session or null.
     */
    fun terminateSession(deviceId: DeviceId): ControlSession? {
        val session = sessions[deviceId] ?: return null
        if (session.isTerminated) return session

        val terminated = if (session.state == SessionState.Active) {
            session.transitionTo(SessionState.Disconnecting)
                .transitionTo(SessionState.Terminated)
        } else if (ControlSession.isValidTransition(session.state, SessionState.Terminated)) {
            session.transitionTo(SessionState.Terminated)
        } else {
            session.copy(state = SessionState.Terminated)
        }
        sessions[deviceId] = terminated
        onSessionTerminated(terminated)
        return terminated
    }

    /**
     * Terminate all active sessions.
     * Returns the list of terminated sessions.
     */
    fun terminateAll(): List<ControlSession> {
        val active = activeSessions()
        return active.mapNotNull { terminateSession(it.deviceId) }
    }

    /** Total session count (including terminated). */
    val totalSessions: Int get() = sessions.size
}
