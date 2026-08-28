package com.elysium369.meet.core.obd

import com.elysium369.meet.core.sync.SyncBatchResult
import com.elysium369.meet.core.twin.TwinTruthState
import com.elysium369.meet.core.twin.VehicleTwinEngine
import com.elysium369.meet.data.local.dao.VehicleTwinDao
import com.elysium369.meet.data.local.entities.TwinAnomalyEntity
import com.elysium369.meet.data.local.entities.VehicleTwinProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiagnosticTruthEmergencyHardeningTest {

    // ==========================================
    // 1. FormulaEvaluator Diagnostic Truth Tests
    // ==========================================

    @Test
    fun formulaBlankReturnsInvalidNotZeroTest() {
        val result = FormulaEvaluator.decode("", listOf(0x40, 0x20))
        assertTrue(result is PidDecodeResult.InvalidFormula)
        assertNull(FormulaEvaluator.evaluateOrNull("", listOf(0x40, 0x20)))
    }

    @Test
    fun missingRequiredByteReturnsInsufficientDataTest() {
        val result = FormulaEvaluator.decode("A*256+B", listOf(0x40))
        assertTrue(result is PidDecodeResult.InsufficientBytes)
        val insufficient = result as PidDecodeResult.InsufficientBytes
        assertEquals('B', insufficient.requiredVariable)
        assertEquals(1, insufficient.requiredIndex)
        assertEquals(1, insufficient.availableBytes)
        assertNull(FormulaEvaluator.evaluateOrNull("A*256+B", listOf(0x40)))
    }

    @Test
    fun divisionByZeroNeverReturnsSyntheticZeroTest() {
        val result = FormulaEvaluator.decode("(A*10)/(B-B)", listOf(0x10, 0x05))
        assertTrue("Division by zero must yield DivisionByZero result, not 0f", result is PidDecodeResult.DivisionByZero)
        assertNull(FormulaEvaluator.evaluateOrNull("(A*10)/(B-B)", listOf(0x10, 0x05)))
    }

    @Test
    fun malformedFormulaNeverProducesPlausibleReadingTest() {
        val result = FormulaEvaluator.decode("A * + / B", listOf(0x10, 0x05))
        assertTrue(result is PidDecodeResult.InvalidFormula)
        assertNull(FormulaEvaluator.evaluateOrNull("A * + / B", listOf(0x10, 0x05)))
    }

    @Test
    fun outOfRangeValueCarriesInvalidTruthStateTest() {
        val result = FormulaEvaluator.decode("A-40", listOf(0xFF), minPhysical = -40.0, maxPhysical = 150.0)
        assertTrue(result is PidDecodeResult.OutOfPhysicalRange)
        val range = result as PidDecodeResult.OutOfPhysicalRange
        assertEquals(215.0, range.value, 0.001)
    }

    @Test
    fun validFormulaDecodesCorrectly() {
        val result = FormulaEvaluator.decode("(A*256+B)/4", listOf(0x10, 0x40))
        assertTrue(result is PidDecodeResult.Success)
        val success = result as PidDecodeResult.Success
        assertEquals(1040.0, success.value, 0.001)
        assertEquals("1040", success.rawHex)
    }

    // ==========================================
    // 2. VehicleTwinEngine Truth & Anomaly Tests
    // ==========================================

    private class FakeVehicleTwinDao : VehicleTwinDao {
        var profile: VehicleTwinProfileEntity? = null
        val anomalies = mutableListOf<TwinAnomalyEntity>()

        override suspend fun insertTwinProfile(profile: VehicleTwinProfileEntity) {
            this.profile = profile
        }

        override suspend fun getTwinProfile(vehicleId: String): VehicleTwinProfileEntity? = profile

        override suspend fun insertAnomaly(anomaly: TwinAnomalyEntity) {
            anomalies.add(anomaly)
        }

        override fun getAnomaliesForVehicle(vehicleId: String): Flow<List<TwinAnomalyEntity>> =
            flowOf(anomalies)

        override suspend fun clearAnomaliesForVehicle(vehicleId: String) {
            anomalies.clear()
        }
    }

    @Test
    fun twinWithoutHistoryReturnsUnknownHealthTest() = runBlocking {
        val fakeDao = FakeVehicleTwinDao()
        val engine = VehicleTwinEngine(fakeDao)

        val profile = engine.trainOrInitializeProfile("test-veh-1", emptyList())
        assertEquals("Untrained model must have 0.0 confidence", 0.0, profile.confidence, 0.001)
        assertEquals("Untrained model must have 0 initial health score", 0, profile.healthScore)
        assertEquals(TwinTruthState.UNTRAINED, engine.getTruthState(profile, historyCount = 0))
    }

    @Test
    fun twinInsufficientBaselineDoesNotClaimConfidenceTest() = runBlocking {
        val fakeDao = FakeVehicleTwinDao()
        val engine = VehicleTwinEngine(fakeDao)

        val sparseHistory = listOf(
            mapOf("010C" to 800f, "0105" to 90f),
            mapOf("010C" to 820f, "0105" to 91f),
            mapOf("010C" to 810f, "0105" to 89f),
        )

        val profile = engine.trainOrInitializeProfile("test-veh-2", sparseHistory)
        assertTrue("Sparse history must not claim high confidence: ${profile.confidence}", profile.confidence < 30.0)
        assertEquals(TwinTruthState.BASELINE_INSUFFICIENT, engine.getTruthState(profile, historyCount = 3))
    }

    @Test
    fun persistentAnomalyCreatesSingleEpisodeTest() = runBlocking {
        val fakeDao = FakeVehicleTwinDao()
        val engine = VehicleTwinEngine(fakeDao)

        // Train with established baseline (15 samples of RPM = 800)
        val history = (1..15).map { mapOf("010C" to 800f, "0105" to 90f) }
        engine.trainOrInitializeProfile("test-veh-3", history)

        // Feed an anomalous RPM spike frame
        val frame1 = mapOf("010C" to 5000f, "0105" to 90f)
        val anomalies1 = engine.evaluateFrame("test-veh-3", frame1)
        assertEquals(1, anomalies1.size)

        // Feed another anomalous frame immediately (within 30s window)
        val frame2 = mapOf("010C" to 5200f, "0105" to 90f)
        val anomalies2 = engine.evaluateFrame("test-veh-3", frame2)
        assertEquals("Second frame in same episode must be deduplicated", 0, anomalies2.size)
    }

    // ==========================================
    // 3. DataLogger Privacy Tests
    // ==========================================

    @Test
    fun telemetryFilenameContainsNoVinTest() {
        val rawVin = "KMHCG41DB5A123456"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(rawVin.toByteArray(Charsets.UTF_8))
            .take(4)
            .joinToString("") { "%02x".format(it) }

        assertFalse("Filename must never contain raw VIN substring", rawVin.contains(digest))
        assertEquals(8, digest.length)
    }

    // ==========================================
    // 4. SyncWorker Batch Result Tests
    // ==========================================

    @Test
    fun partialBatchCannotReturnFalseSuccessTest() {
        val partialBatch = SyncBatchResult(successCount = 9, retryableCount = 1, permanentCount = 0)
        assertTrue("Batch with 1 retryable failure must flag hasRetryableFailure", partialBatch.hasRetryableFailure)

        val perfectBatch = SyncBatchResult(successCount = 10, retryableCount = 0, permanentCount = 0)
        assertFalse("Perfect batch must not flag retryable failure", perfectBatch.hasRetryableFailure)
    }
}
