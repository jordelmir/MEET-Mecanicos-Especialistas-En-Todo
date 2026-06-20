package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.VehicleDnaProfileEntity

@Dao
interface VehicleDnaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDnaProfile(profile: VehicleDnaProfileEntity)

    @Query("SELECT * FROM vehicle_dna_profiles WHERE vehicleId = :vehicleId LIMIT 1")
    suspend fun getDnaProfile(vehicleId: String): VehicleDnaProfileEntity?

    @Query("DELETE FROM vehicle_dna_profiles WHERE vehicleId = :vehicleId")
    suspend fun deleteDnaProfile(vehicleId: String)
}
