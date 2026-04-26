package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.CustomPidEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPidDao {
    @Query("SELECT * FROM custom_pids")
    fun getAllCustomPids(): Flow<List<CustomPidEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomPid(pid: CustomPidEntity)

    @Delete
    suspend fun deleteCustomPid(pid: CustomPidEntity)
}
