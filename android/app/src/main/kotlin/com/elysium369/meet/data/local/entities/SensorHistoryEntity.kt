package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SensorHistoryEntity — Persistent telemetry archive for predictive health analysis.
 *
 * Each row is a single sensor reading captured during a live diagnostic session.
 * The PredictiveHealthEngine performs linear regression over these records to
 * detect degradation trends (e.g., coolant temp rising 2°C/month → thermostat failure).
 *
 * Indexes are designed for fast time-series queries:
 *   - vehicleId + pid + timestamp → trend analysis per sensor per vehicle
 *   - sessionId → session replay / export
 */
@Entity(
    tableName = "sensor_history",
    indices = [
        Index(value = ["vehicleId", "pid", "timestamp"]),
        Index(value = ["sessionId"])
    ]
)
data class SensorHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: String,
    val sessionId: String,
    val pid: String,          // OBD PID code (e.g., "0105" for coolant temp)
    val pidLabel: String,     // Human label (e.g., "Coolant Temp")
    val value: Float,         // Raw sensor value in standard units
    val unit: String,         // "°C", "V", "%", "RPM", etc.
    val timestamp: Long       // System.currentTimeMillis() at capture
)

/**
 * HealthSnapshotEntity — Periodic health score checkpoints.
 *
 * Stored once per session or at configurable intervals to track
 * the vehicle's overall health trajectory over weeks/months.
 */
@Entity(
    tableName = "health_snapshots",
    indices = [Index(value = ["vehicleId", "timestamp"])]
)
data class HealthSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: String,
    val sessionId: String,
    val overallScore: Int,           // 0-100
    val engineScore: Int,            // 0-100 subsystem
    val fuelScore: Int,              // 0-100 subsystem
    val coolingScore: Int,           // 0-100 subsystem
    val electricalScore: Int,        // 0-100 subsystem
    val emissionsScore: Int,         // 0-100 subsystem
    val activeDtcCount: Int,
    val pendingDtcCount: Int,
    val anomalyCount: Int,
    val sensorSummaryJson: String,   // JSON snapshot of key sensor values at capture time
    val timestamp: Long
)
