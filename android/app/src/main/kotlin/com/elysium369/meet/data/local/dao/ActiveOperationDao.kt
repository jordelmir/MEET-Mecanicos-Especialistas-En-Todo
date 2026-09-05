package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.core.operations.ActiveOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ActiveOperationEntity — durable operation tracking.
 */
@Dao
interface ActiveOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActiveOperationEntity)

    @Query("SELECT * FROM active_operations WHERE operationId = :id LIMIT 1")
    suspend fun getById(id: String): ActiveOperationEntity?

    @Query("SELECT * FROM active_operations WHERE ownerPrincipalId = :ownerId ORDER BY startedAt DESC")
    suspend fun getByOwner(ownerId: String): List<ActiveOperationEntity>

    @Query("SELECT * FROM active_operations WHERE ownerPrincipalId = :ownerId AND state IN ('RUNNING', 'STARTING', 'PAUSED') ORDER BY startedAt DESC")
    suspend fun getActiveByOwner(ownerId: String): List<ActiveOperationEntity>

    @Query("SELECT * FROM active_operations WHERE vehicleId = :vehicleId AND state IN ('RUNNING', 'STARTING', 'PAUSED')")
    suspend fun getActiveByVehicle(vehicleId: String): List<ActiveOperationEntity>

    @Query("SELECT * FROM active_operations WHERE rideId = :rideId AND state IN ('RUNNING', 'STARTING', 'PAUSED')")
    suspend fun getActiveByRide(rideId: String): List<ActiveOperationEntity>

    @Query("SELECT * FROM active_operations WHERE scope = :scope AND state IN ('RUNNING', 'STARTING', 'PAUSED')")
    suspend fun getActiveByScope(scope: String): List<ActiveOperationEntity>

    @Query("DELETE FROM active_operations WHERE operationId = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM active_operations WHERE ownerPrincipalId = :ownerId AND state IN ('STOPPED', 'FAILED', 'COMPLETED')")
    suspend fun cleanupTerminalByOwner(ownerId: String): Int

    @Query("UPDATE active_operations SET state = 'STOPPED', error = :err, lastHeartbeat = :now WHERE operationId = :id")
    suspend fun markStopped(id: String, err: String?, now: Long): Int
}