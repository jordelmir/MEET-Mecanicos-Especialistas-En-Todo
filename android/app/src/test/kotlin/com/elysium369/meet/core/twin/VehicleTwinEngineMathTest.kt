package com.elysium369.meet.core.twin

import com.elysium369.meet.data.local.dao.VehicleTwinDao
import com.elysium369.meet.data.local.entities.TwinAnomalyEntity
import com.elysium369.meet.data.local.entities.VehicleTwinProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure-math tests for the algorithms inside VehicleTwinEngine.
 *
 * These tests do NOT touch the DAO. They exercise the statistical pipeline
 * (Kalman filter step, EWMA smoothing, Z-Score, anomaly triggers) by
 * calling the engine with a fake TwinDao (in-memory implementation).
 *
 * Goal: lock down the math so future refactors of the engine do not silently
 * change the diagnostic thresholds.
 */
class VehicleTwinEngineMathTest {

    /**
     * Minimal in-memory VehicleTwinDao for tests. We bypass the real Room DAO
     * because we only care about the math.
     */
    private class FakeTwinDao : VehicleTwinDao {
        private val profiles = mutableMapOf<String, VehicleTwinProfileEntity>()
        private val anomalies = mutableMapOf<String, TwinAnomalyEntity>()

        override suspend fun getTwinProfile(vehicleId: String): VehicleTwinProfileEntity? =
            profiles[vehicleId]

        override suspend fun insertTwinProfile(profile: VehicleTwinProfileEntity) {
            profiles[profile.vehicleId] = profile
        }

        override fun getAnomaliesForVehicle(vehicleId: String): kotlinx.coroutines.flow.Flow<List<TwinAnomalyEntity>> =
            kotlinx.coroutines.flow.flowOf(anomalies.values.filter { it -> it.vehicleId == vehicleId })

        override suspend fun insertAnomaly(anomaly: TwinAnomalyEntity) {
            anomalies[anomaly.anomalyId] = anomaly
        }

        override suspend fun clearAnomaliesForVehicle(vehicleId: String) {
            val toRemove = anomalies.entries.filter { it.value.vehicleId == vehicleId }.map { it.key }
            toRemove.forEach { anomalies.remove(it) }
        }
    }

    private fun newEngine(): Pair<VehicleTwinEngine, FakeTwinDao> {
        val dao = FakeTwinDao()
        val engine = VehicleTwinEngine(dao)
        return engine to dao
    }

    // ─────────────────────────────────────────────────────────────────────
    // Baseline training
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun trainOrInitializeProfile_withEmptyHistory_returnsDefaultProfile() = kotlinx.coroutines.runBlocking {
        val (engine, _) = newEngine()
        val profile = engine.trainOrInitializeProfile("v1", emptyList())
        assertNotNull(profile)
        assertEquals("v1", profile.vehicleId)
        // Default stdDev = 1.0 for any PID with no history
        assertTrue(profile.confidence in 40.0..95.0)
    }

    @Test
    fun trainOrInitializeProfile_returnsExistingProfileOnSecondCall() = kotlinx.coroutines.runBlocking {
        val (engine, dao) = newEngine()
        val first = engine.trainOrInitializeProfile("v1", emptyList())
        val second = engine.trainOrInitializeProfile("v1", emptyList())
        // Second call returns the same persisted profile (same profileId by dao key)
        assertEquals(first.profileId, second.profileId)
        assertEquals(first.confidence, second.confidence, 0.001)
        // dao has the profile stored
        assertNotNull(dao.getTwinProfile("v1"))
    }

    @Test
    fun trainOrInitializeProfile_withEnoughHistoryComputesRealMeanAndStd() = kotlinx.coroutines.runBlocking {
        val (engine, _) = newEngine()
        // 20 frames where PID 0105 is always 90.0 — mean=90, std≈0
        val history = (1..20).map { mapOf("0105" to 90.0f) }
        val profile = engine.trainOrInitializeProfile("v1", history)
        val baselines = engine.decodeBaselinesForTest(profile)
        val mean = baselines["0105"] ?: error("missing baseline")
        // mean should be 90 ± 0.01
        assertTrue("mean was $mean", abs(mean - 90f) < 0.01f)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Frame evaluation — anomaly triggers
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun evaluateFrame_underThreshold_producesNoAnomalies() = kotlinx.coroutines.runBlocking {
        val (engine, _) = newEngine()
        // Train on flat history
        val history = (1..30).map { mapOf("0105" to 90.0f, "0142" to 14.0f) }
        engine.trainOrInitializeProfile("v1", history)

        // Frame close to baseline → no anomalies
        val anomalies = engine.evaluateFrame("v1", mapOf("0105" to 90.0f, "0142" to 14.0f))
        assertTrue("Expected no anomalies, got $anomalies", anomalies.isEmpty())
    }

    @Test
    fun evaluateFrame_extremeZScore_triggersHighSeverity() = kotlinx.coroutines.runBlocking {
        val (engine, _) = newEngine()
        val history = (1..30).map { mapOf("0105" to 90.0f) }
        engine.trainOrInitializeProfile("v1", history)

        // 0105 jumps to 250 — wildly off baseline (mean 90, std≈0 → huge Z)
        val anomalies = engine.evaluateFrame("v1", mapOf("0105" to 250.0f))
        assertTrue("Expected ≥1 anomaly", anomalies.isNotEmpty())
        val hit = anomalies.first { it.parameter.contains("Coolant", ignoreCase = true) }
        assertEquals("HIGH", hit.severity)
    }

    @Test
    fun evaluateFrame_batteryVoltageTrendDecline_triggersHighSeverity() = kotlinx.coroutines.runBlocking {
        val (engine, _) = newEngine()
        // Train on 0142 stable around 14V
        val history = (1..30).map { mapOf("0142" to 14.0f) }
        engine.trainOrInitializeProfile("v1", history)

        // Feed a sequence of declining 0142 values: 13.4, 13.0, 12.6, 12.2
        val sequence = listOf(13.4f, 13.0f, 12.6f, 12.2f)
        var lastAnomaly: TwinAnomalyEntity? = null
        for (v in sequence) {
            val anomalies = engine.evaluateFrame("v1", mapOf("0142" to v))
            anomalies.firstOrNull { it.parameter.contains("Battery", ignoreCase = true) }?.let {
                lastAnomaly = it
            }
        }
        assertNotNull("Expected battery-voltage trend anomaly", lastAnomaly)
        assertEquals("HIGH", lastAnomaly!!.severity)
    }

    @Test
    fun evaluateFrame_relativeDeviationOver15Percent_triggersMedium() = kotlinx.coroutines.runBlocking {
        val (engine, _) = newEngine()
        val history = (1..30).map { mapOf("010C" to 800.0f) } // Engine RPM baseline 800
        engine.trainOrInitializeProfile("v1", history)

        // +20% deviation on RPM
        val anomalies = engine.evaluateFrame("v1", mapOf("010C" to 960.0f))
        assertTrue("Expected anomaly from relative deviation", anomalies.isNotEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────
    // Health-score bookkeeping
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun healthScore_decreasesOnHighSeverityAnomaly() = kotlinx.coroutines.runBlocking {
        val (engine, dao) = newEngine()
        engine.trainOrInitializeProfile("v1", (1..30).map { mapOf("0105" to 90.0f) })
        val before = dao.getTwinProfile("v1")!!.healthScore

        // Trigger HIGH
        engine.evaluateFrame("v1", mapOf("0105" to 250.0f))
        val after = dao.getTwinProfile("v1")!!.healthScore
        assertTrue("health should drop after HIGH anomaly: $before → $after", after < before)
    }

    @Test
    fun healthScore_floorsAt10UnderSustainedAnomalies() = kotlinx.coroutines.runBlocking {
        val (engine, dao) = newEngine()
        engine.trainOrInitializeProfile("v1", (1..30).map { mapOf("0105" to 90.0f) })

        // Hammer it with 30 HIGH anomalies
        repeat(30) {
            engine.evaluateFrame("v1", mapOf("0105" to 250.0f + it))
        }
        val score = dao.getTwinProfile("v1")!!.healthScore
        assertTrue("score should be floored to >=10, was $score", score >= 10)
        assertTrue("score should drop below start, was $score", score < 100)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pure-math sanity (Kalman step, EWMA) — referenced via the public API
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun ewma_convergesToTrueValue() = kotlinx.coroutines.runBlocking {
        val (engine, _) = newEngine()
        // Feed same value many times, ensure subsequent evaluations don't keep flagging anomalies
        val history = (1..50).map { mapOf("0105" to 90.0f) }
        engine.trainOrInitializeProfile("v1", history)

        // First evaluation at 90 (matches baseline) → no anomaly
        val first = engine.evaluateFrame("v1", mapOf("0105" to 90.0f))
        // A few more at 90 → still no anomaly (EWMA stable)
        repeat(5) {
            val r = engine.evaluateFrame("v1", mapOf("0105" to 90.0f))
            assertTrue("EWMA stable iterations should not flag 90 as anomaly", r.none { it.parameter.contains("Coolant") })
        }
        assertTrue("baseline frame should be clean", first.none { it.parameter.contains("Coolant") })
    }
}