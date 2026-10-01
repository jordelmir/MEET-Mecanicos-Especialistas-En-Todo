package com.elysium.nexus.fabric.routing

import com.elysium.nexus.fabric.canonical.Protocol

/**
 * §57 Circuit Breaker.
 *
 * After [failureThreshold] consecutive failures on
 * a protocol, the circuit opens and blocks all
 * further attempts for [cooldownMs]. After the
 * cooldown, the circuit enters half-open state
 * and allows a single probe. If the probe
 * succeeds, the circuit closes; if it fails,
 * the circuit reopens.
 *
 * ## States
 *
 * - CLOSED: normal operation; failures counted.
 * - OPEN: blocking all attempts; cooldown pending.
 * - HALF_OPEN: allowing one probe; waiting for result.
 *
 * ## Why per-protocol
 *
 * A network failure on WiFi doesn't mean IR is
 * broken. The circuit breaker is per-protocol
 * so one protocol's failure doesn't block
 * another's attempts.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val cooldownMs: Long = 30_000L,
    private val halfOpenMaxProbes: Int = 1
) {
    init {
        require(failureThreshold > 0) { "failureThreshold must be positive." }
        require(cooldownMs > 0) { "cooldownMs must be positive." }
        require(halfOpenMaxProbes > 0) { "halfOpenMaxProbes must be positive." }
    }

    private val circuits = mutableMapOf<Protocol, CircuitState>()

    /**
     * Check if the circuit is open for [protocol].
     * If open and cooldown expired, transitions
     * to half-open and allows the probe.
     */
    fun allowAttempt(protocol: Protocol): Boolean {
        val state = circuits[protocol] ?: return true
        val now = System.currentTimeMillis()

        return when (state.status) {
            CircuitStatus.Closed -> true
            CircuitStatus.Open -> {
                if (now - state.openedAtMs >= cooldownMs) {
                    // Transition to half-open
                    circuits[protocol] = state.copy(
                        status = CircuitStatus.HalfOpen,
                        halfOpenProbes = 0
                    )
                    true
                } else {
                    false
                }
            }
            CircuitStatus.HalfOpen -> {
                state.halfOpenProbes < halfOpenMaxProbes
            }
        }
    }

    /**
     * Record a successful attempt. If the circuit
     * was half-open, close it. If closed, reset
     * the failure count.
     */
    fun recordSuccess(protocol: Protocol) {
        val state = circuits[protocol]
        if (state == null) {
            circuits[protocol] = CircuitState(
                status = CircuitStatus.Closed,
                consecutiveFailures = 0,
                openedAtMs = 0L,
                halfOpenProbes = 0
            )
            return
        }

        when (state.status) {
            CircuitStatus.HalfOpen -> {
                // Probe succeeded — close the circuit
                circuits[protocol] = CircuitState(
                    status = CircuitStatus.Closed,
                    consecutiveFailures = 0,
                    openedAtMs = 0L,
                    halfOpenProbes = 0
                )
            }
            CircuitStatus.Closed -> {
                // Reset failure count
                circuits[protocol] = state.copy(consecutiveFailures = 0)
            }
            CircuitStatus.Open -> {
                // Shouldn't happen, but handle gracefully
            }
        }
    }

    /**
     * Record a failed attempt. If failures reach
     * the threshold, open the circuit.
     */
    fun recordFailure(protocol: Protocol) {
        val state = circuits[protocol] ?: run {
            circuits[protocol] = CircuitState(
                status = if (failureThreshold <= 1) CircuitStatus.Open else CircuitStatus.Closed,
                consecutiveFailures = 1,
                openedAtMs = if (failureThreshold <= 1) System.currentTimeMillis() else 0L,
                halfOpenProbes = 0
            )
            return
        }

        when (state.status) {
            CircuitStatus.Closed -> {
                val newFailures = state.consecutiveFailures + 1
                if (newFailures >= failureThreshold) {
                    circuits[protocol] = CircuitState(
                        status = CircuitStatus.Open,
                        consecutiveFailures = newFailures,
                        openedAtMs = System.currentTimeMillis(),
                        halfOpenProbes = 0
                    )
                } else {
                    circuits[protocol] = state.copy(consecutiveFailures = newFailures)
                }
            }
            CircuitStatus.HalfOpen -> {
                // Probe failed — reopen
                circuits[protocol] = CircuitState(
                    status = CircuitStatus.Open,
                    consecutiveFailures = state.consecutiveFailures + 1,
                    openedAtMs = System.currentTimeMillis(),
                    halfOpenProbes = state.halfOpenProbes + 1
                )
            }
            CircuitStatus.Open -> {
                // Already open, nothing to do
            }
        }
    }

    /**
     * Get the current state for a protocol.
     */
    fun stateFor(protocol: Protocol): CircuitState =
        circuits[protocol] ?: CircuitState(
            status = CircuitStatus.Closed,
            consecutiveFailures = 0,
            openedAtMs = 0L,
            halfOpenProbes = 0
        )

    /**
     * Reset the circuit for a protocol.
     */
    fun reset(protocol: Protocol) {
        circuits.remove(protocol)
    }

    /**
     * Reset all circuits.
     */
    fun resetAll() {
        circuits.clear()
    }
}

data class CircuitState(
    val status: CircuitStatus,
    val consecutiveFailures: Int,
    val openedAtMs: Long,
    val halfOpenProbes: Int
) {
    val isBlocking: Boolean get() = status == CircuitStatus.Open
}

enum class CircuitStatus {
    /** Normal operation; failures counted. */
    Closed,
    /** Blocking all attempts; cooldown pending. */
    Open,
    /** Allowing one probe; waiting for result. */
    HalfOpen
}
