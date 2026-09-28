package com.elysium.nexus.core.engine

/**
 * The 10 state machine states a session transitions through, per
 * `MASTER_ORDER.md` §32:
 *
 * ```
 * Idle → Discovering → Pairing → Authenticating → Negotiating
 *       → Connected → Active → Suspended → Reconnecting → Disconnected
 * ```
 *
 * The order is the *legal forward path*. The engine does not
 * enforce the path — the higher-level transport layer (Phase 2+)
 * drives the transitions. The engine's only job with respect to
 * this enum is to:
 *
 *  1. expose the current [EngineState] as a `StateFlow` so the
 *     UI and the transport can observe it;
 *  2. **emit a neutral frame on every transition** per §32 / §38,
 *     so an `Active → Suspended` (or any other transition out
 *     of any state) leaves the input surface in a known-zero
 *     state.
 */
enum class EngineState {
    /** No session. The starting point. */
    Idle,

    /** Looking for a peer (host, agent, receiver). */
    Discovering,

    /** Peer found, exchange pairing material. */
    Pairing,

    /** Pairing complete, mutual authentication in progress. */
    Authenticating,

    /** Authenticated, capabilities / version / profile in
     *  negotiation. */
    Negotiating,

    /** Negotiated. The session exists but the user has not started
     *  producing input yet. */
    Connected,

    /** The user is producing input. The canonical state is being
     *  updated by the touch / motion / button surfaces. */
    Active,

    /** Temporarily paused (e.g. the host went to sleep). The
     *  session is alive but input is being held at neutral. */
    Suspended,

    /** Trying to re-establish a dropped session. The previous
     *  session's identity is still being held; if reconnect
     *  fails within a timeout, the engine transitions to
     *  [Disconnected] and the identity is dropped. */
    Reconnecting,

    /** Session is gone. Identity is dropped. The engine will
     *  refuse new input until the transport layer transitions
     *  back to [Idle] (or starts a fresh [Discovering]
     *  sequence). */
    Disconnected;

    /**
     * @return `true` if the engine is currently in [Active] —
     *   the only state in which user input is emitted to the
     *   transport. All other states are *non-emitting*: the
     *   canonical state stays neutral.
     */
    fun isActive(): Boolean = this == Active

    /**
     * @return `true` if the engine has a live session (i.e. is
     *   in [Connected], [Active], [Suspended], or
     *   [Reconnecting]). Used by the transport layer to decide
     *   whether to keep the underlying socket alive.
     */
    fun hasSession(): Boolean = this == Connected ||
        this == Active ||
        this == Suspended ||
        this == Reconnecting
}
