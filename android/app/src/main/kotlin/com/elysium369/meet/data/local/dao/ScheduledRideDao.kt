package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elysium369.meet.data.local.entities.FavoriteRouteEntity
import com.elysium369.meet.data.local.entities.ScheduledRideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledRideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduledRideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(schedules: List<ScheduledRideEntity>)

    @Update
    suspend fun update(schedule: ScheduledRideEntity)

    @Query("SELECT * FROM scheduled_rides WHERE scheduleId = :scheduleId")
    suspend fun getById(scheduleId: String): ScheduledRideEntity?

    @Query("SELECT * FROM scheduled_rides WHERE userId = :userId AND status IN ('PENDING', 'REMINDER_SENT', 'DISPATCHING') ORDER BY scheduledAtEpochMs ASC")
    fun getUpcomingFlow(userId: String): Flow<List<ScheduledRideEntity>>

    @Query("SELECT * FROM scheduled_rides WHERE userId = :userId AND status IN ('PENDING', 'REMINDER_SENT', 'DISPATCHING') ORDER BY scheduledAtEpochMs ASC")
    suspend fun getUpcoming(userId: String): List<ScheduledRideEntity>

    @Query("SELECT * FROM scheduled_rides WHERE status = 'DISPATCHING' AND dispatchAtEpochMs <= :nowEpochMs")
    suspend fun getDueForDispatch(nowEpochMs: Long): List<ScheduledRideEntity>

    @Query("UPDATE scheduled_rides SET status = :status WHERE scheduleId = :scheduleId")
    suspend fun updateStatus(scheduleId: String, status: String)

    @Query("UPDATE scheduled_rides SET status = :status, matchedDriverId = :driverId, rideId = :rideId WHERE scheduleId = :scheduleId")
    suspend fun assignDriver(scheduleId: String, status: String, driverId: String, rideId: String)

    @Query("DELETE FROM scheduled_rides WHERE scheduleId = :scheduleId")
    suspend fun delete(scheduleId: String): Int

    @Query("DELETE FROM scheduled_rides WHERE status IN ('COMPLETED', 'CANCELLED', 'EXPIRED') AND createdAtEpochMs < :cutoffEpochMs")
    suspend fun purgeTerminal(cutoffEpochMs: Long): Int

    // ── Favorite Routes ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(route: FavoriteRouteEntity)

    @Query("SELECT * FROM favorite_routes ORDER BY usageCount DESC")
    fun getFavoritesFlow(): Flow<List<FavoriteRouteEntity>>

    @Query("SELECT * FROM favorite_routes ORDER BY usageCount DESC")
    suspend fun getFavorites(): List<FavoriteRouteEntity>

    @Query("SELECT * FROM favorite_routes WHERE routeId = :routeId")
    suspend fun getFavoriteById(routeId: String): FavoriteRouteEntity?

    @Query("UPDATE favorite_routes SET usageCount = usageCount + 1, lastUsedEpochMs = :nowEpochMs WHERE routeId = :routeId")
    suspend fun incrementFavoriteUsage(routeId: String, nowEpochMs: Long)

    @Query("DELETE FROM favorite_routes WHERE routeId = :routeId")
    suspend fun deleteFavorite(routeId: String): Int
}
