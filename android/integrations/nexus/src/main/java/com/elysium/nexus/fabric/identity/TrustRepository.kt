package com.elysium.nexus.fabric.identity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.elysium.nexus.fabric.canonical.DeviceId

/**
 * V0.6.2 PR4 Phase 16 — Room entity for durable device trust records.
 *
 * §71: Trust state must survive process death. Every trust-checked
 * action reads from here, not from an in-memory map.
 */
@Entity(tableName = "device_trust")
data class DeviceTrustRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "trust_state")
    val trustState: String,

    @ColumnInfo(name = "trust_score")
    val trustScore: Float,

    @ColumnInfo(name = "evidence_count")
    val evidenceCount: Int,

    @ColumnInfo(name = "last_activity_ms")
    val lastActivityMs: Long,

    @ColumnInfo(name = "last_verified_ms")
    val lastVerifiedMs: Long,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),

    /** Serialized evidence list (JSON array of TrustEvidence names). */
    @ColumnInfo(name = "evidence_types")
    val evidenceTypes: String = ""
)

@Dao
interface TrustDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DeviceTrustRecordEntity)

    @Query("SELECT * FROM device_trust WHERE device_id = :deviceId LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): DeviceTrustRecordEntity?

    @Query("SELECT * FROM device_trust ORDER BY last_activity_ms DESC")
    suspend fun getAll(): List<DeviceTrustRecordEntity>

    @Query("UPDATE device_trust SET trust_state = :state, trust_score = :score, last_activity_ms = :activityMs WHERE device_id = :deviceId")
    suspend fun updateTrust(deviceId: String, state: String, score: Float, activityMs: Long)

    @Query("DELETE FROM device_trust WHERE device_id = :deviceId")
    suspend fun deleteByDeviceId(deviceId: String)

    @Query("DELETE FROM device_trust")
    suspend fun deleteAll()
}

/**
 * Bridges [TrustDao] to in-memory [DeviceTrustRecord].
 * Trust state is persisted in Room and loaded on startup.
 */
class DeviceTrustRepository(
    private val dao: TrustDao
) {
    /**
     * Load trust record for a device. Returns null if never seen.
     */
    suspend fun getTrustRecord(deviceId: DeviceId): DeviceTrustRecord? {
        val entity = dao.getByDeviceId(deviceId.value) ?: return null
        return DeviceTrustRecord(
            deviceId = deviceId,
            currentState = try { TrustState.valueOf(entity.trustState) } catch (_: Exception) { TrustState.UNPAIRED },
            evidence = emptyList(),
            createdAtMs = entity.createdAtMs,
            trustScore = entity.trustScore.toDouble(),
            lastActivityMs = entity.lastActivityMs,
            lastVerifiedMs = entity.lastVerifiedMs
        )
    }

    /**
     * Persist a trust record. Creates or updates.
     */
    suspend fun saveTrustRecord(record: DeviceTrustRecord) {
        val entity = DeviceTrustRecordEntity(
            deviceId = record.deviceId.value,
            trustState = record.currentState.name,
            trustScore = record.trustScore.toFloat(),
            evidenceCount = record.evidence.size,
            lastActivityMs = record.lastActivityMs,
            lastVerifiedMs = record.lastVerifiedMs ?: 0L,
            createdAtMs = record.createdAtMs,
            evidenceTypes = record.evidence.map { it::class.simpleName ?: "Unknown" }.joinToString(",")
        )
        dao.upsert(record = entity)
    }

    /**
     * Upgrade trust state. Validates transition via [TrustStateMachine].
     */
    suspend fun upgradeTrust(
        deviceId: DeviceId,
        newState: TrustState,
        evidence: TrustEvidence
    ): DeviceTrustRecord? {
        val current = getTrustRecord(deviceId) ?: DeviceTrustRecord(
            deviceId = deviceId,
            currentState = TrustState.UNPAIRED,
            evidence = emptyList(),
            createdAtMs = System.currentTimeMillis(),
            lastActivityMs = System.currentTimeMillis()
        )
        if (!current.currentState.canUpgradeTo(newState)) return null
        val updated = current.copy(
            currentState = newState,
            evidence = current.evidence + evidence,
            trustScore = (newState.ordinal.toDouble() / TrustState.MANUFACTURER_CERTIFIED.ordinal)
                .coerceIn(0.0, 1.0),
            lastActivityMs = System.currentTimeMillis(),
            lastVerifiedMs = System.currentTimeMillis()
        )
        saveTrustRecord(updated)
        return updated
    }
}

/**
 * Trust audit log entity — §72: every trust-checked action is auditable.
 */
@Entity(tableName = "trust_audit_log")
data class TrustAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "required_trust")
    val requiredTrust: String,

    @ColumnInfo(name = "actual_trust")
    val actualTrust: String,

    @ColumnInfo(name = "authorized")
    val authorized: Boolean,

    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long = System.currentTimeMillis()
)

@Dao
interface TrustAuditDao {

    @Insert
    suspend fun insert(entry: TrustAuditLogEntity)

    @Query("SELECT * FROM trust_audit_log WHERE device_id = :deviceId ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun getByDeviceId(deviceId: String, limit: Int = 50): List<TrustAuditLogEntity>

    @Query("SELECT * FROM trust_audit_log WHERE authorized = 0 ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun getDenied(limit: Int = 50): List<TrustAuditLogEntity>
}
