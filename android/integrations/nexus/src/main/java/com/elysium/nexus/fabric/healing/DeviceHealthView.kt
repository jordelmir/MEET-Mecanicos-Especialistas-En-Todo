package com.elysium.nexus.fabric.healing

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol

/**
 * V06-P31 — Two-axis device health view (§57–§61 diagnostics).
 *
 * Combines the binding axis ([BindingHealthTracker]) and the route axis
 * ([SelfHealingRouteManager]) into one verdict per device: what exactly is
 * broken — the pairing, the transport, both, or nothing — and what healing
 * action applies. This is the honest input a diagnostics screen and the
 * self-healing loop consume instead of a single conflated health number.
 */
data class TwoAxisVerdict(
    val deviceId: DeviceId,
    val binding: BindingHealthStatus? = null,
    val route: RouteAxisStatus? = null,
    val outcome: AxisOutcome,
    val recommendedAction: HealingAction
)

enum class AxisOutcome {
    OK,
    ROUTE_DEGRADED,
    BINDING_STALE,
    BINDING_STALE_AND_ROUTE_DEGRADED
}

enum class RouteAxisStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY
}

enum class HealingAction {
    /** Nothing to heal. */
    NONE,
    /** Transport link down — reconnect / failover; revalidate route in background. */
    FAILOVER_AND_REVALIDATE_ROUTE,
    /** Pairing stale — re-pair / revalidate the binding (auth axis). */
    REPAIR_BINDING,
    /** Both axes broken — failover first, then repair the binding. */
    FAILOVER_THEN_REPAIR_BINDING
}

object DeviceHealthView {

    /**
     * Combine the two axes into one verdict.
     *
     * @param bindingStatus the binding axis state (null = unknown/not tracked)
     * @param routeStatus the route axis state (null = healthy)
     */
    fun verdict(
        deviceId: DeviceId,
        bindingStatus: BindingHealthStatus?,
        routeStatus: RouteAxisStatus?
    ): TwoAxisVerdict {
        val bindingStale = bindingStatus == BindingHealthStatus.STALE
        val routeBad = routeStatus != null && routeStatus != RouteAxisStatus.HEALTHY

        val outcome = when {
            bindingStale && routeBad -> AxisOutcome.BINDING_STALE_AND_ROUTE_DEGRADED
            bindingStale -> AxisOutcome.BINDING_STALE
            routeBad -> AxisOutcome.ROUTE_DEGRADED
            else -> AxisOutcome.OK
        }
        val action = when (outcome) {
            AxisOutcome.OK -> HealingAction.NONE
            AxisOutcome.ROUTE_DEGRADED -> HealingAction.FAILOVER_AND_REVALIDATE_ROUTE
            AxisOutcome.BINDING_STALE -> HealingAction.REPAIR_BINDING
            AxisOutcome.BINDING_STALE_AND_ROUTE_DEGRADED -> HealingAction.FAILOVER_THEN_REPAIR_BINDING
        }
        return TwoAxisVerdict(
            deviceId = deviceId,
            binding = bindingStatus,
            route = routeStatus,
            outcome = outcome,
            recommendedAction = action
        )
    }

    /**
     * Convenience for the route axis: derive it from a route health score
     * (see [RouteHealth.healthScore]).
     */
    fun routeAxisFromScore(healthScore: Double): RouteAxisStatus = when {
        healthScore <= 0.0 -> RouteAxisStatus.UNHEALTHY
        healthScore >= 1.0 -> RouteAxisStatus.HEALTHY
        else -> RouteAxisStatus.DEGRADED
    }
}