package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.ActiveVehicleSelectionEntity

@Dao
interface ActiveVehicleSelectionDao {
    @Query("SELECT * FROM active_vehicle_selections WHERE ownerPrincipalId = :ownerPrincipalId LIMIT 1")
    suspend fun get(ownerPrincipalId: String): ActiveVehicleSelectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(selection: ActiveVehicleSelectionEntity)

    @Query("DELETE FROM active_vehicle_selections WHERE ownerPrincipalId = :ownerPrincipalId AND vehicleId = :vehicleId")
    suspend fun deleteIfSelected(ownerPrincipalId: String, vehicleId: String): Int
}
