package com.elysium.nexus.fabric.healing

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.routing.TransportRoute
import com.elysium.nexus.fabric.evidence.EventResult

/**
 * §59 Self-Healing Routes.
 *
 * When a route fails repeatedly:
 * ```
 * route confidence ↓
 * ```
 *
 * If another route works:
 * ```
 * automatic failover
 * ```
 *
 * After failover:
 * ```
 * background revalidation
 * ```
 *
 * The self-healing engine monitors route health
 * and proactively switches to healthier routes
 * before the user notices failures.
 */
class SelfHealingRouteManager(
    private val failureThreshold: Int = 3,
    private val recoveryCheckIntervalMs: Long = 60_000L,
    /**
     * V06-P31: when injected, failures are classified into the two health
     * axes — auth-class failures ALSO advance the binding axis (the
     * pairing is stale and needs re-pair, not a transport retry);
     * transport-class failures only advance this route axis.
     * Null keeps the single-axis behavior of the original manager.
     */
    private val bindingTracker: BindingHealthTracker? = null
) {
    private val routeHealth = mutableMapOf<RouteKey, RouteHealth>()

    /**
     * Record a successful route usage.
     */
    fun recordSuccess(deviceId: DeviceId, protocol: Protocol) {
        val key = RouteKey(deviceId, protocol)
        val existing = routeHealth[key] ?: RouteHealth()
        routeHealth[key] = existing.copy(
            consecutiveFailures = 0,
            lastSuccessAtMs = System.currentTimeMillis(),
            successCount = existing.successCount + 1
        )
    }

    /**
     * Record a failed route usage. When [isAuthFailure] is true (typed —
     * no message sniffing) and a binding tracker is injected, the binding
     * axis is advanced too: the pairing itself is suspect.
     */
    fun recordFailure(
        deviceId: DeviceId,
        protocol: Protocol,
        reason: String,
        isAuthFailure: Boolean = false,
        bindingId: String? = null
    ) {
        val key = RouteKey(deviceId, protocol)
        val existing = routeHealth[key] ?: RouteHealth()
        val newFailures = existing.consecutiveFailures + 1
        routeHealth[key] = existing.copy(
            consecutiveFailures = newFailures,
            lastFailureAtMs = System.currentTimeMillis(),
            lastFailureReason = reason,
            isUnhealthy = newFailures >= failureThreshold
        )
        if (isAuthFailure) {
            bindingTracker?.recordAuthFailure(
                bindingId = bindingId ?: "${deviceId.value}@${protocol.name}",
                deviceId = deviceId,
                protocol = protocol,
                reason = reason
            )
        } else {
            bindingTracker?.recordTransportFailure(
                bindingId = bindingId ?: "${deviceId.value}@${protocol.name}",
                reason = reason
            )
        }
    }

    /**
     * Check if a route is healthy.
     */
    fun isHealthy(deviceId: DeviceId, protocol: Protocol): Boolean {
        val key = RouteKey(deviceId, protocol)
        val health = routeHealth[key] ?: return true
        return !health.isUnhealthy
    }

    /**
     * Get the best available route for a device.
     * Returns routes sorted by health (best first).
     */
    fun rankRoutes(
        deviceId: DeviceId,
        routes: List<TransportRoute>
    ): List<TransportRoute> {
        return routes.sortedBy { route ->
            val key = RouteKey(deviceId, route.protocol)
            val health = routeHealth[key]
            when {
                health == null -> 0 // Unknown = healthy
                health.isUnhealthy -> 2 // Unhealthy = last
                health.consecutiveFailures > 0 -> 1 // Degraded = middle
                else -> 0 // Healthy = first
            }
        }
    }

    /**
     * Get routes that need revalidation.
     */
    fun routesNeedingRevalidation(): List<RouteKey> {
        val now = System.currentTimeMillis()
        return routeHealth.entries
            .filter { (_, health) ->
                health.isUnhealthy &&
                    (now - health.lastFailureAtMs) > recoveryCheckIntervalMs
            }
            .map { it.key }
    }

    /**
     * Reset health for a route (e.g. after revalidation).
     */
    fun resetHealth(deviceId: DeviceId, protocol: Protocol) {
        routeHealth.remove(RouteKey(deviceId, protocol))
    }

    /**
     * V06-P31: when the route dies and the bindings were still valid, the
     * background revalidation candidate is the ROUTE (not the pair). When
     * the auth axis entered STALE, the candidate is the BINDING.
     */
    fun revalidationCandidates(
        bindingId: String,
        deviceId: DeviceId,
        protocol: Protocol
    ): RevalidationPlan {
        val route = routeHealth[RouteKey(deviceId, protocol)]
        val routeStale = route != null && route.isUnhealthy
        val bindingStale = bindingTracker?.isBindingHealthy(bindingId) == false
        return when {
            bindingStale && routeStale -> RevalidationPlan.REBIND_AND_FAILOVER
            bindingStale -> RevalidationPlan.REBIND
            routeStale -> RevalidationPlan.ROUTE_RECHECK
            else -> RevalidationPlan.NONE
        }
    }

    /**
     * Health summary for a device on the route axis.
     */
    fun healthSummary(deviceId: DeviceId): Map<Protocol, RouteHealth> {
        return routeHealth.entries
            .filter { it.key.deviceId == deviceId }
            .associate { it.key.protocol to it.value }
    }
}

/**
 * V06-P31: what the background heal should re-check.
 */
enum class RevalidationPlan {
    /** Nothing broken. */
    NONE,
    /** Transport link — reconnect / failover, revalidate the route. */
    ROUTE_RECHECK,
    /** Pairing axis — re-pair / revalidate the binding. */
    REBIND,
    /** Both — failover first, then repair the binding. */
    REBIND_AND_FAILOVER
}

data class RouteKey(
    val deviceId: DeviceId,
    val protocol: Protocol
)

data class RouteHealth(
    val consecutiveFailures: Int = 0,
    val lastSuccessAtMs: Long = 0L,
    val lastFailureAtMs: Long = 0L,
    val lastFailureReason: String? = null,
    val successCount: Long = 0L,
    val isUnhealthy: Boolean = false
) {
    val healthScore: Double
        get() {
            if (isUnhealthy) return 0.0
            val total = successCount + consecutiveFailures
            if (total == 0L) return 1.0
            return successCount.toDouble() / total
        }
}
