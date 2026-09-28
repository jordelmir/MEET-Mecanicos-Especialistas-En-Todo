package com.elysium.nexus.fabric.diagnostics

import com.elysium.nexus.core.latency.LatencyTracker
import com.elysium.nexus.fabric.evidence.FlightEntry
import com.elysium.nexus.fabric.evidence.FlightRecorder
import com.elysium.nexus.fabric.evidence.TransportResult

/**
 * V06-P34 — KPI harness (MASTER_ORDER §1 Success Metrics).
 *
 * Computes the §1 KPIs from REAL recorded sources — today: the
 * [FlightRecorder] (the only production data source with latency,
 * route and outcome telemetry). Every KPI declares its source and its
 * threshold; a KPI without a data source is reported `UNMEASURED`,
 * never invented ("Unproven production signals = 0" is enforced by
 * honesty, not by silence).
 *
 * Measured now (flight-backed):
 * - `dispatch_success_rate`             (≥ 0.99)
 * - `lan_command_p50_ms` / `p95_ms`     (< 50 / < 150, §1)
 * - `ir_first_candidate_rate`           (≥ 0.80, §1 — first evaluated
 *   route is DirectIr AND it succeeded)
 * - `silent_fallback_count`             (= 0, §1 — Fallback flights are
 *   evidence-flagged, never disguised as success)
 * - `wrong_device_dispatch`             (= 0, §1 — single-target dispatch
 *   loop; counted from flights only as an integrity check)
 *
 * Explicitly UNMEASURED until their physical/database sources exist:
 * discovery success, known-device reconnect, migration loss,
 * HIL regression, median setup time, crash-free sessions.
 */
class KpiHarness(private val recorder: FlightRecorder) {

    fun snapshot(): KpiSnapshot = snapshotFor(recorder.all())

    companion object {

        fun snapshotFor(entries: List<FlightEntry>): KpiSnapshot {
            val attempts = entries.size

            val successes = entries.filter { it.result == TransportResult.Success }
            val failures = entries.filter { it.result != TransportResult.Success }

            val successfulLatencies = successes.map { it.totalLatencyNs.toDouble() / 1_000_000.0 }
            val latencyTracker = LatencyTracker().also { tracker ->
                successfulLatencies.forEach { tracker.record((it * 1_000_000.0).toLong()) }
            }
            val latencySnapshot = latencyTracker.snapshot()

            val irFirstCandidates = entries.filter { entry ->
                entry.routesEvaluated.firstOrNull()?.protocol
                    ?.name?.startsWith("DirectIr") == true
            }
            val irFirstSuccesses = irFirstCandidates.count {
                it.result == TransportResult.Success
            }

            val silentFallbacks = failures.count { it.result == TransportResult.Fallback }

            val kpis = listOf(
                KpiMeasure(
                    key = "dispatch_success_rate",
                    value = if (attempts > 0) successes.size.toDouble() / attempts else null,
                    unit = "ratio",
                    threshold = "≥ 0.99",
                    source = "FlightRecorder"
                ),
                KpiMeasure(
                    key = "lan_command_p50_ms",
                    value = if (latencySnapshot.count > 0) latencySnapshot.p50?.toDouble()?.div(1_000_000.0) else null,
                    unit = "ms",
                    threshold = "< 50",
                    source = "FlightRecorder (successful flights, total latency)"
                ),
                KpiMeasure(
                    key = "lan_command_p95_ms",
                    value = if (latencySnapshot.count > 0) latencySnapshot.p95?.toDouble()?.div(1_000_000.0) else null,
                    unit = "ms",
                    threshold = "< 150",
                    source = "FlightRecorder (successful flights, total latency)"
                ),
                KpiMeasure(
                    key = "ir_first_candidate_rate",
                    value = if (irFirstCandidates.isNotEmpty()) {
                        irFirstSuccesses.toDouble() / irFirstCandidates.size
                    } else null,
                    unit = "ratio",
                    threshold = "≥ 0.80 (§1 IR first candidate)",
                    source = "FlightRecorder (first evaluated route DirectIr)"
                ),
                KpiMeasure(
                    key = "silent_fallback_count",
                    value = silentFallbacks.toDouble(),
                    unit = "count",
                    threshold = "= 0 (§1 no silent fallback)",
                    source = "FlightRecorder (Fallback results)"
                ),
                KpiMeasure(
                    key = "wrong_device_dispatch",
                    value = wrongDeviceDispatches(entries).toDouble(),
                    unit = "count",
                    threshold = "= 0 (§1 wrong-device dispatch)",
                    source = "FlightRecorder (correlationId re-use with different targets)"
                )
            )

            val unmeasured = listOf(
                "lan_discovery_success" to "no discovery telemetry source yet",
                "known_device_reconnect" to "needs pairing-session telemetry",
                "profile_migration_loss" to "needs profile migration runs",
                "known_hil_regression" to "HIL not running (hardware blocked)",
                "median_known_tv_setup_s" to "needs user-facing setup telemetry",
                "crash_free_sessions" to "needs session telemetry"
            ).map { (key, why) ->
                KpiMeasure(
                    key = key,
                    value = null,
                    unit = null,
                    threshold = "UNMEASURED",
                    source = why
                )
            }

            return KpiSnapshot(kpis + unmeasured)
        }

        /**
         * A correlationId reused for TWO different target devices is a
         * wrong-device-dispatch signal; this is a constructed impossibility
         * in the single-target dispatch loop, checked here as evidence.
         */
        private fun wrongDeviceDispatches(entries: List<FlightEntry>): Int {
            val correlationTargets = mutableMapOf<String, MutableSet<String>>()
            for (entry in entries) {
                correlationTargets
                    .getOrPut(entry.correlationId) { mutableSetOf() }
                    .add(entry.targetDeviceId.value)
            }
            return correlationTargets.count { it.value.size > 1 }
        }
    }
}

enum class KpiStatus { PASS, FAIL, UNMEASURED }

data class KpiMeasure(
    val key: String,
    val value: Double?,
    val unit: String?,
    val threshold: String,
    val source: String,
    val status: KpiStatus = when {
        value == null -> KpiStatus.UNMEASURED
        threshold == "= 0 (§1 no silent fallback)" -> if (value == 0.0) KpiStatus.PASS else KpiStatus.FAIL
        threshold == "= 0 (§1 wrong-device dispatch)" -> if (value == 0.0) KpiStatus.PASS else KpiStatus.FAIL
        threshold == "≥ 0.99" -> if (value >= 0.99) KpiStatus.PASS else KpiStatus.FAIL
        threshold == "≥ 0.80 (§1 IR first candidate)" -> if (value >= 0.80) KpiStatus.PASS else KpiStatus.FAIL
        threshold.startsWith("< 50") -> if (value < 50) KpiStatus.PASS else KpiStatus.FAIL
        threshold.startsWith("< 150") -> if (value < 150) KpiStatus.PASS else KpiStatus.FAIL
        else -> KpiStatus.PASS
    }
)

data class KpiSnapshot(
    val kpis: List<KpiMeasure>
) {
    fun byKey(key: String): KpiMeasure? = kpis.firstOrNull { it.key == key }
    val unmeasuredCount: Int get() = kpis.count { it.status == KpiStatus.UNMEASURED }
    val failingCount: Int get() = kpis.count { it.status == KpiStatus.FAIL }
}