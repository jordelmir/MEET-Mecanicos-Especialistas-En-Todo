package com.elysium.nexus.fabric.healing

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-P31: the binding axis — pairing/identity health, independent of the
 * transport axis.
 */
class BindingHealthTrackerTest {

    private val deviceId = DeviceId("tv-binding-001")

    @Test
    fun `unknown bindings are healthy`() {
        val tracker = BindingHealthTracker()
        assertTrue(tracker.isBindingHealthy("ir-1"))
        assertTrue(tracker.bindingsNeedingRepair().isEmpty())
    }

    @Test
    fun `auth failures below threshold keep binding valid`() {
        val tracker = BindingHealthTracker(authFailureThreshold = 3)
        tracker.recordAuthFailure("ir-1", deviceId, Protocol.DirectIr, "token stale")
        tracker.recordAuthFailure("ir-1", deviceId, Protocol.DirectIr, "token stale")
        assertTrue(tracker.isBindingHealthy("ir-1"))
        assertEquals(2, tracker.bindingHealth("ir-1")!!.consecutiveAuthFailures)
    }

    @Test
    fun `auth failures at threshold mark the binding stale for repair`() {
        val tracker = BindingHealthTracker(authFailureThreshold = 3)
        repeat(3) { tracker.recordAuthFailure("ir-1", deviceId, Protocol.DirectIr, "sig mismatch") }
        assertFalse(tracker.isBindingHealthy("ir-1"))
        val repair = tracker.bindingsNeedingRepair()
        assertEquals(1, repair.size)
        assertEquals(BindingHealthStatus.STALE, repair.first().status)
        assertEquals(Protocol.DirectIr, repair.first().protocol)
    }

    @Test
    fun `validation heals a stale binding`() {
        val tracker = BindingHealthTracker(authFailureThreshold = 2)
        repeat(2) { tracker.recordAuthFailure("ir-1", deviceId, Protocol.DirectIr, "auth") }
        assertFalse(tracker.isBindingHealthy("ir-1"))
        tracker.recordBindingValidated("ir-1")
        assertTrue(tracker.isBindingHealthy("ir-1"))
        assertTrue(tracker.bindingsNeedingRepair().isEmpty())
        assertEquals(0, tracker.bindingHealth("ir-1")!!.consecutiveAuthFailures)
    }

    @Test
    fun `transport failures do not advance the auth axis`() {
        val tracker = BindingHealthTracker(authFailureThreshold = 3)
        repeat(5) { tracker.recordTransportFailure("ir-1", "wifi down") }
        assertTrue(tracker.isBindingHealthy("ir-1"))
        assertEquals(0, tracker.bindingHealth("ir-1")!!.consecutiveAuthFailures)
        assertEquals(5, tracker.bindingHealth("ir-1")!!.consecutiveTransportFailures)
    }

    @Test
    fun `binding health is keyed per binding`() {
        val tracker = BindingHealthTracker(authFailureThreshold = 2)
        repeat(2) { tracker.recordAuthFailure("ir-1", deviceId, Protocol.DirectIr, "auth") }
        assertFalse(tracker.isBindingHealthy("ir-1"))
        assertTrue("ir-2 must be unaffected", tracker.isBindingHealthy("ir-2"))
    }
}

/**
 * V06-P31: route manager classifies failures into the two axes when a
 * binding tracker is injected (typed flag, no message sniffing).
 */
class SelfHealingRouteManagerBindingTest {

    private val deviceId = DeviceId("tv-two-axis-001")

    @Test
    fun `auth-class failures heal as binding repair not transport retry`() {
        val binding = BindingHealthTracker(authFailureThreshold = 2)
        val manager = SelfHealingRouteManager(failureThreshold = 3, bindingTracker = binding)

        manager.recordFailure(
            deviceId, Protocol.DirectIr,
            reason = "auth rejected",
            isAuthFailure = true,
            bindingId = "ir-1"
        )
        manager.recordFailure(
            deviceId, Protocol.DirectIr,
            reason = "auth rejected",
            isAuthFailure = true,
            bindingId = "ir-1"
        )

        assertFalse(binding.isBindingHealthy("ir-1"))
        assertEquals(
            RevalidationPlan.REBIND,
            manager.revalidationCandidates("ir-1", deviceId, Protocol.DirectIr)
        )
    }

    @Test
    fun `transport-class failures keep the binding valid and recheck the route`() {
        val binding = BindingHealthTracker(authFailureThreshold = 2)
        val manager = SelfHealingRouteManager(failureThreshold = 2, bindingTracker = binding)

        repeat(2) {
            manager.recordFailure(
                deviceId, Protocol.WiFi,
                reason = "no route to host",
                isAuthFailure = false,
                bindingId = "wifi-1"
            )
        }

        assertTrue(binding.isBindingHealthy("wifi-1"))
        assertEquals(
            RevalidationPlan.ROUTE_RECHECK,
            manager.revalidationCandidates("wifi-1", deviceId, Protocol.WiFi)
        )
    }

    @Test
    fun `both axes broken yields rebind and failover`() {
        val binding = BindingHealthTracker(authFailureThreshold = 2)
        val manager = SelfHealingRouteManager(failureThreshold = 2, bindingTracker = binding)

        repeat(2) {
            manager.recordFailure(
                deviceId, Protocol.DirectIr,
                reason = "auth",
                isAuthFailure = true,
                bindingId = "ir-1"
            )
        }
        repeat(2) {
            manager.recordFailure(
                deviceId, Protocol.DirectIr,
                reason = "timeout",
                isAuthFailure = false,
                bindingId = "ir-1"
            )
        }

        assertEquals(
            RevalidationPlan.REBIND_AND_FAILOVER,
            manager.revalidationCandidates("ir-1", deviceId, Protocol.DirectIr)
        )
    }

    @Test
    fun `without a tracker the manager behaves exactly as before`() {
        val manager = SelfHealingRouteManager(failureThreshold = 2)
        repeat(2) {
            manager.recordFailure(deviceId, Protocol.DirectIr, "boom", isAuthFailure = true)
        }
        assertFalse(manager.isHealthy(deviceId, Protocol.DirectIr))
        // V06-P31: even without a binding tracker, the route axis is
        // unhealthy → revalidation candidate is ROUTE_RECHECK (not NONE).
        assertEquals(
            RevalidationPlan.ROUTE_RECHECK,
            manager.revalidationCandidates("ir-1", deviceId, Protocol.DirectIr)
        )
    }
}

/**
 * V06-P31: two-axis verdict — the honest diagnostic answer.
 */
class DeviceHealthViewTest {

    private val deviceId = DeviceId("tv-verdict-001")

    @Test
    fun `healthy axes produce OK with no action`() {
        val v = DeviceHealthView.verdict(deviceId, BindingHealthStatus.VALID, RouteAxisStatus.HEALTHY)
        assertEquals(AxisOutcome.OK, v.outcome)
        assertEquals(HealingAction.NONE, v.recommendedAction)
    }

    @Test
    fun `route down alone is a failover symptom`() {
        val v = DeviceHealthView.verdict(deviceId, BindingHealthStatus.VALID, RouteAxisStatus.UNHEALTHY)
        assertEquals(AxisOutcome.ROUTE_DEGRADED, v.outcome)
        assertEquals(HealingAction.FAILOVER_AND_REVALIDATE_ROUTE, v.recommendedAction)
    }

    @Test
    fun `stale binding alone means re-pair not retry`() {
        val v = DeviceHealthView.verdict(deviceId, BindingHealthStatus.STALE, RouteAxisStatus.HEALTHY)
        assertEquals(AxisOutcome.BINDING_STALE, v.outcome)
        assertEquals(HealingAction.REPAIR_BINDING, v.recommendedAction)
    }

    @Test
    fun `both axes broken prioritise failover then rebind`() {
        val v = DeviceHealthView.verdict(deviceId, BindingHealthStatus.STALE, RouteAxisStatus.DEGRADED)
        assertEquals(AxisOutcome.BINDING_STALE_AND_ROUTE_DEGRADED, v.outcome)
        assertEquals(HealingAction.FAILOVER_THEN_REPAIR_BINDING, v.recommendedAction)
    }

    @Test
    fun `route axis derives from health score`() {
        assertEquals(RouteAxisStatus.UNHEALTHY, DeviceHealthView.routeAxisFromScore(0.0))
        assertEquals(RouteAxisStatus.DEGRADED, DeviceHealthView.routeAxisFromScore(0.5))
        assertEquals(RouteAxisStatus.HEALTHY, DeviceHealthView.routeAxisFromScore(1.0))
    }
}