package com.elysium.nexus.fabric.healing

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol

/**
 * V06-P31 — Binding-health axis (MASTER_ORDER §57–§61, §17).
 *
 * A device has TWO independent health axes:
 * - **Binding health**: is the pairing/identity link valid? (auth failures,
 *   signature mismatches, stale tokens — the "who are you" axis).
 * - Route health (see [SelfHealingRouteManager]): is the transport link up?
 *   (timeouts, network errors — the "can I reach you" axis).
 *
 * A WiFi TV with the AP down: binding VALID, route DOWN. A TV with a stale
 * pairing key: route UP, binding STALE. The healing actions differ
 * (reconnect transport vs re-pair) — conflating them (as device+protocol
 * health alone does) heals the wrong thing.
 *
 * This tracker owns the binding axis, keyed by `bindingId`.
 */
class BindingHealthTracker(
    private val authFailureThreshold: Int = 3
) {
    init {
        require(authFailureThreshold > 0) { "authFailureThreshold must be positive." }
    }

    private val bindingHealth = mutableMapOf<String, BindingHealth>()

    /**
     * Record a failure that came from the binding/identity layer
     * (auth, signature, credentials). Consecutive auth failures mark
     * the binding STALE — it needs re-pair/revalidation, not a
     * transport retry.
     */
    fun recordAuthFailure(
        bindingId: String,
        deviceId: DeviceId,
        protocol: Protocol,
        reason: String
    ) {
        val existing = bindingHealth[bindingId] ?: BindingHealth(bindingId = bindingId)
        val consecutive = existing.consecutiveAuthFailures + 1
        bindingHealth[bindingId] = existing.copy(
            deviceId = deviceId,
            protocol = protocol,
            consecutiveAuthFailures = consecutive,
            totalAuthFailures = existing.totalAuthFailures + 1,
            lastAuthFailureAtMs = System.currentTimeMillis(),
            lastFailureReason = reason,
            status = if (consecutive >= authFailureThreshold) {
                BindingHealthStatus.STALE
            } else {
                existing.status
            }
        )
    }

    /**
     * Record a transport-layer failure for this binding (the link died,
     * not the pairing). Does NOT advance the auth axis.
     */
    fun recordTransportFailure(
        bindingId: String,
        reason: String
    ) {
        val existing = bindingHealth[bindingId] ?: BindingHealth(bindingId = bindingId)
        bindingHealth[bindingId] = existing.copy(
            consecutiveTransportFailures = existing.consecutiveTransportFailures + 1,
            lastTransportFailureAtMs = System.currentTimeMillis(),
            lastFailureReason = reason
        )
    }

    /**
     * Record that the binding was validated (successful auth/verification).
     * A validated binding is healthy again.
     */
    fun recordBindingValidated(bindingId: String) {
        val existing = bindingHealth[bindingId] ?: return
        bindingHealth[bindingId] = existing.copy(
            consecutiveAuthFailures = 0,
            consecutiveTransportFailures = 0,
            lastValidatedAtMs = System.currentTimeMillis(),
            status = BindingHealthStatus.VALID,
            lastFailureReason = null
        )
    }

    /** Whether the binding is currently VALID. Unknown bindings are valid. */
    fun isBindingHealthy(bindingId: String): Boolean {
        val health = bindingHealth[bindingId] ?: return true
        return health.status != BindingHealthStatus.STALE
    }

    /** Bindings that have entered STALE and need re-pair/revalidation. */
    fun bindingsNeedingRepair(): List<BindingHealth> =
        bindingHealth.values.filter { it.status == BindingHealthStatus.STALE }

    /** Full view of one binding's health. */
    fun bindingHealth(bindingId: String): BindingHealth? = bindingHealth[bindingId]

    /** Summary of all tracked bindings. */
    fun all(): List<BindingHealth> = bindingHealth.values.toList()

    /** Clear all state (e.g. after a successful re-pair). */
    fun reset(bindingId: String) = bindingHealth.remove(bindingId)

    fun clear() = bindingHealth.clear()
}

enum class BindingHealthStatus {
    /** Unknown or validated — usable. */
    VALID,
    /** Repeated auth failures — must re-pair / revalidate. */
    STALE
}

data class BindingHealth(
    val bindingId: String,
    val deviceId: DeviceId? = null,
    val protocol: Protocol? = null,
    val status: BindingHealthStatus = BindingHealthStatus.VALID,
    val consecutiveAuthFailures: Int = 0,
    val totalAuthFailures: Int = 0,
    val consecutiveTransportFailures: Int = 0,
    val lastAuthFailureAtMs: Long = 0L,
    val lastTransportFailureAtMs: Long = 0L,
    val lastValidatedAtMs: Long = 0L,
    val lastFailureReason: String? = null
) {
    /** Overall: STALE wins (a stale binding is unusable even with a live link). */
    val isUsable: Boolean
        get() = status != BindingHealthStatus.STALE && consecutiveTransportFailures == 0
}