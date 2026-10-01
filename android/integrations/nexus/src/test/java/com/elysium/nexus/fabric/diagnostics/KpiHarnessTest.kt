package com.elysium.nexus.fabric.diagnostics

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.evidence.CandidateRoute
import com.elysium.nexus.fabric.evidence.FlightEntry
import com.elysium.nexus.fabric.evidence.TransportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-P34: §1 KPIs computed from real flight telemetry; unmeasured KPIs
 * are reported, never invented.
 */
class KpiHarnessTest {

    private val deviceId = DeviceId("tv-kpi-001")

    private fun flight(
        latencyMs: Long,
        result: TransportResult = TransportResult.Success,
        evaluated: List<CandidateRoute> = listOf(
            CandidateRoute(Protocol.DirectIr, score = 0.9, isSelected = true)
        ),
        correlationId: String = "corr-$latencyMs"
    ): FlightEntry {
        val started = 1_000_000_000L
        return FlightEntry(
            traceId = "trace-$latencyMs",
            actionType = "PowerOn",
            correlationId = correlationId,
            targetDeviceId = deviceId,
            routesEvaluated = evaluated,
            winningRoute = null,
            commandPayload = null,
            result = result,
            observedState = null,
            startedAtNs = started,
            completedAtNs = started + latencyMs * 1_000_000L,
            resolveLatencyNs = 0L,
            translateLatencyNs = 0L,
            sendLatencyNs = 0L,
            confirmLatencyNs = 0L,
            errorMessage = null,
            circuitBreakerTripped = false
        )
    }

    @Test
    fun `empty recorder reports unmeasured latencies, zero fallbacks`() {
        val snapshot = KpiHarness.snapshotFor(emptyList())
        assertNull(snapshot.byKey("lan_command_p50_ms")!!.value)
        assertEquals(KpiStatus.UNMEASURED, snapshot.byKey("lan_command_p50_ms")!!.status)
        assertEquals(KpiStatus.UNMEASURED, snapshot.byKey("lan_command_p95_ms")!!.status)
        assertEquals(KpiMeasureStatusOf(snapshot, "silent_fallback_count"), KpiStatus.PASS)
        assertEquals(0.0, snapshot.byKey("silent_fallback_count")!!.value!!, 0.0)
        assertTrue("unmeasured KPIs must be explicitly listed",
            snapshot.byKey("lan_discovery_success")!!.status == KpiStatus.UNMEASURED)
    }

    @Test
    fun `p50 and p95 come from successful flight latencies`() {
        val entries = listOf(
            flight(10), flight(20), flight(30), flight(40), flight(50)
        )
        val snapshot = KpiHarness.snapshotFor(entries)
        // type-7 linear percentiles of 10,20,30,40,50 → p50 = 30, p95 = 48
        assertEquals(30.0, snapshot.byKey("lan_command_p50_ms")!!.value!!, 1.0)
        assertEquals(48.0, snapshot.byKey("lan_command_p95_ms")!!.value!!, 1.0)
    }

    @Test
    fun `slow latency drives the KPI to FAIL`() {
        val entries = listOf(flight(60), flight(200))
        val snapshot = KpiHarness.snapshotFor(entries)
        assertEquals(KpiStatus.FAIL, snapshot.byKey("lan_command_p50_ms")!!.status)
        assertEquals(KpiStatus.FAIL, snapshot.byKey("lan_command_p95_ms")!!.status)
    }

    @Test
    fun `fallback flights count as silent fallback evidence`() {
        val entries = listOf(
            flight(10, result = TransportResult.Fallback),
            flight(15)
        )
        val snapshot = KpiHarness.snapshotFor(entries)
        assertEquals(1.0, snapshot.byKey("silent_fallback_count")!!.value!!, 0.0)
        assertEquals(KpiStatus.FAIL, snapshot.byKey("silent_fallback_count")!!.status)
    }

    @Test
    fun `ir first-candidate rate measures the first evaluated route`() {
        val irFirst = listOf(
            flight(10),
            flight(11),
            flight(12, result = TransportResult.AdapterError)
        )
        val nonIrFirst = flight(
            13,
            evaluated = listOf(CandidateRoute(Protocol.WiFi, score = 0.5, isSelected = true))
        )
        val snapshot = KpiHarness.snapshotFor(irFirst + nonIrFirst)
        // 2 of 3 IR-first flights succeeded → 0.667 ≥? < 0.80 → FAIL
        assertEquals(2.0 / 3.0, snapshot.byKey("ir_first_candidate_rate")!!.value!!, 1e-9)
        assertEquals(KpiStatus.FAIL, snapshot.byKey("ir_first_candidate_rate")!!.status)
    }

    @Test
    fun `ir first-candidate pass at eighty percent`() {
        val entries = listOf(
            flight(1), flight(2), flight(3), flight(4),
            flight(5, result = TransportResult.AdapterError)
        )
        val snapshot = KpiHarness.snapshotFor(entries)
        assertEquals(0.8, snapshot.byKey("ir_first_candidate_rate")!!.value!!, 1e-9)
        assertEquals(KpiStatus.PASS, snapshot.byKey("ir_first_candidate_rate")!!.status)
    }

    @Test
    fun `wrong-device dispatch is zero when correlation ids are consistent`() {
        val entries = listOf(
            flight(1, correlationId = "c1"),
            flight(2, correlationId = "c1")
        )
        assertEquals(0.0, KpiHarness.snapshotFor(entries).byKey("wrong_device_dispatch")!!.value!!, 0.0)
    }

    @Test
    fun `success rate over attempts`() {
        val entries = listOf(flight(1), flight(2), flight(3, result = TransportResult.AdapterError))
        val rate = KpiHarness.snapshotFor(entries).byKey("dispatch_success_rate")!!.value!!
        assertEquals(2.0 / 3.0, rate, 1e-9)
    }

    private fun KpiMeasureStatusOf(snapshot: KpiSnapshot, key: String): KpiStatus =
        snapshot.byKey(key)!!.status
}