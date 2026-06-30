package com.elysium369.meet.core.health

import com.elysium369.meet.data.local.dao.HealthSnapshotDao
import com.elysium369.meet.data.local.dao.SensorHistoryDao
import com.elysium369.meet.data.local.dao.PredictionEventDao
import com.elysium369.meet.data.local.entities.SensorHistoryEntity
import com.elysium369.meet.data.local.entities.HealthSnapshotEntity
import com.elysium369.meet.data.local.entities.PredictionEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveHealthEngineTest {

    // ── Fake DAOs to run tests without Mockito ──
    private class FakeSensorHistoryDao(
        private val recordedPids: List<String>,
        private val averages: List<SensorHistoryEntity>
    ) : SensorHistoryDao {
        override suspend fun insertAll(records: List<SensorHistoryEntity>) {}
        override suspend fun getSensorTrend(vehicleId: String, pid: String): List<SensorHistoryEntity> = emptyList()
        override suspend fun getSessionAverages(vehicleId: String, pid: String): List<SensorHistoryEntity> = averages
        override suspend fun getRecentReadings(vehicleId: String, pid: String, limit: Int): List<SensorHistoryEntity> = emptyList()
        override suspend fun getRecordedPids(vehicleId: String): List<String> = recordedPids
        override suspend fun getRecordCount(vehicleId: String): Int = averages.size
        override suspend fun deleteOlderThan(olderThan: Long) {}
        override suspend fun deleteAllForVehicle(vehicleId: String) {}
    }

    private class FakeHealthSnapshotDao : HealthSnapshotDao {
        override suspend fun insert(snapshot: HealthSnapshotEntity) {}
        override suspend fun getRecentSnapshots(vehicleId: String, limit: Int): List<HealthSnapshotEntity> = emptyList()
        override suspend fun getLatestSnapshot(vehicleId: String): HealthSnapshotEntity? = null
        override fun observeSnapshots(vehicleId: String): Flow<List<HealthSnapshotEntity>> = emptyFlow()
        override suspend fun deleteOlderThan(olderThan: Long) {}
    }

    private class FakePredictionEventDao : PredictionEventDao {
        override suspend fun insert(event: PredictionEventEntity) {}
        override suspend fun insertAll(events: List<PredictionEventEntity>) {}
        override fun observeEventsForVehicle(vehicleId: String): Flow<List<PredictionEventEntity>> = emptyFlow()
        override suspend fun getEventsForVehicle(vehicleId: String): List<PredictionEventEntity> = emptyList()
        override suspend fun deleteOlderThan(olderThan: Long) {}
        override suspend fun deleteAllForVehicle(vehicleId: String) {}
    }

    @Test
    fun testComputeHealthReportGeneratesCorrectAlerts() {
        val vehicleId = "veh_test_1"
        val coolantPid = "0105"
        val t0 = System.currentTimeMillis() - 5 * 86400000L // 5 days ago

        // Simulate rising Coolant Temperature over 5 days (from 80°C to 105°C)
        val historyData = listOf(
            SensorHistoryEntity(1, vehicleId, "sess_1", coolantPid, "Temp. Refrigerante", 80f, "°C", t0),
            SensorHistoryEntity(2, vehicleId, "sess_2", coolantPid, "Temp. Refrigerante", 85f, "°C", t0 + 86400000L),
            SensorHistoryEntity(3, vehicleId, "sess_3", coolantPid, "Temp. Refrigerante", 90f, "°C", t0 + 2 * 86400000L),
            SensorHistoryEntity(4, vehicleId, "sess_4", coolantPid, "Temp. Refrigerante", 95f, "°C", t0 + 3 * 86400000L),
            SensorHistoryEntity(5, vehicleId, "sess_5", coolantPid, "Temp. Refrigerante", 100f, "°C", t0 + 4 * 86400000L),
            SensorHistoryEntity(6, vehicleId, "sess_6", coolantPid, "Temp. Refrigerante", 105f, "°C", t0 + 5 * 86400000L)
        )

        val fakeHistoryDao = FakeSensorHistoryDao(listOf(coolantPid), historyData)
        val fakeSnapshotDao = FakeHealthSnapshotDao()
        val fakeEventDao = FakePredictionEventDao()

        val engine = PredictiveHealthEngine(fakeHistoryDao, fakeSnapshotDao, fakeEventDao)

        // Compute report
        val report = kotlinx.coroutines.runBlocking {
            engine.computeHealthReport(
                vehicleId = vehicleId,
                currentLiveData = mapOf(coolantPid to 105f),
                activeDtcCount = 0,
                pendingDtcCount = 0,
                anomalyCount = 0
            )
        }

        // Assertions
        assertTrue(report.overallScore > 0)
        assertTrue(report.alerts.isNotEmpty())
        
        // Should predict failure soon since current is 105°C (max allowed is 110°C) and it's rising 5°C/day
        val alert = report.alerts.find { it.pid == coolantPid }
        assertTrue(alert != null)
        assertEquals(AlertSeverity.HIGH, alert?.severity)
        assertTrue(alert?.predictedDaysToFailure in 1..2)
        assertTrue("subiendo" in (alert?.message ?: ""))
    }
}
