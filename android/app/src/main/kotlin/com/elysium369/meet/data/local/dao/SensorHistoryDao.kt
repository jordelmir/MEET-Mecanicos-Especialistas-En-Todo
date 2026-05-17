package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.HealthSnapshotEntity
import com.elysium369.meet.data.local.entities.SensorHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * SensorHistoryDao — High-performance DAO for telemetry time-series operations.
 *
 * Design principles:
 *   - Batch inserts only (insertAll) to minimize I/O during live capture
 *   - Aggregation queries run on Dispatchers.IO, never main thread
 *   - Automatic cleanup of records older than retention period
 */
@Dao
interface SensorHistoryDao {

    // ── Batch Insert (called every N seconds during live session) ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SensorHistoryEntity>)

    // ── Trend Query: Get all readings for a specific PID on a vehicle ──
    @Query("""
        SELECT * FROM sensor_history 
        WHERE vehicleId = :vehicleId AND pid = :pid 
        ORDER BY timestamp ASC
    """)
    suspend fun getSensorTrend(vehicleId: String, pid: String): List<SensorHistoryEntity>

    // ── Aggregated Average per Session (for trend charting) ──
    @Query("""
        SELECT sessionId, AVG(value) as value, pid, pidLabel, unit, 
               MIN(timestamp) as timestamp, vehicleId, 0 as id
        FROM sensor_history 
        WHERE vehicleId = :vehicleId AND pid = :pid 
        GROUP BY sessionId 
        ORDER BY timestamp ASC
    """)
    suspend fun getSessionAverages(vehicleId: String, pid: String): List<SensorHistoryEntity>

    // ── Latest N readings for a PID (sparkline display) ──
    @Query("""
        SELECT * FROM sensor_history 
        WHERE vehicleId = :vehicleId AND pid = :pid 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getRecentReadings(vehicleId: String, pid: String, limit: Int = 100): List<SensorHistoryEntity>

    // ── Get distinct PIDs recorded for a vehicle ──
    @Query("""
        SELECT DISTINCT pid FROM sensor_history 
        WHERE vehicleId = :vehicleId
    """)
    suspend fun getRecordedPids(vehicleId: String): List<String>

    // ── Count total records for a vehicle (data volume indicator) ──
    @Query("SELECT COUNT(*) FROM sensor_history WHERE vehicleId = :vehicleId")
    suspend fun getRecordCount(vehicleId: String): Int

    // ── Cleanup: Delete records older than retention period ──
    @Query("DELETE FROM sensor_history WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    // ── Full wipe for a vehicle ──
    @Query("DELETE FROM sensor_history WHERE vehicleId = :vehicleId")
    suspend fun deleteAllForVehicle(vehicleId: String)
}

@Dao
interface HealthSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: HealthSnapshotEntity)

    @Query("""
        SELECT * FROM health_snapshots 
        WHERE vehicleId = :vehicleId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getRecentSnapshots(vehicleId: String, limit: Int = 30): List<HealthSnapshotEntity>

    @Query("""
        SELECT * FROM health_snapshots 
        WHERE vehicleId = :vehicleId 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getLatestSnapshot(vehicleId: String): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots WHERE vehicleId = :vehicleId ORDER BY timestamp ASC")
    fun observeSnapshots(vehicleId: String): Flow<List<HealthSnapshotEntity>>

    @Query("DELETE FROM health_snapshots WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
