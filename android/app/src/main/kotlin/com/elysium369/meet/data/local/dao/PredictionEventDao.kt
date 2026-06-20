package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.PredictionEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PredictionEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PredictionEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PredictionEventEntity>)

    @Query("SELECT * FROM prediction_events WHERE vehicleId = :vehicleId ORDER BY createdAt DESC")
    fun observeEventsForVehicle(vehicleId: String): Flow<List<PredictionEventEntity>>

    @Query("SELECT * FROM prediction_events WHERE vehicleId = :vehicleId ORDER BY createdAt DESC")
    suspend fun getEventsForVehicle(vehicleId: String): List<PredictionEventEntity>

    @Query("DELETE FROM prediction_events WHERE createdAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("DELETE FROM prediction_events WHERE vehicleId = :vehicleId")
    suspend fun deleteAllForVehicle(vehicleId: String)
}
