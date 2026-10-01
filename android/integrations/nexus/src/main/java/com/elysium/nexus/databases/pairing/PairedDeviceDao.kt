package com.elysium.nexus.databases.pairing

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {

    @Query("SELECT * FROM paired_devices ORDER BY last_seen_timestamp DESC")
    fun getAllDevicesFlow(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE auth_status = 'PAIRED' ORDER BY last_seen_timestamp DESC")
    fun getPairedDevicesFlow(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: String): PairedDeviceEntity?

    @Query("SELECT * FROM paired_devices WHERE protocol_type = :protocolType")
    suspend fun getDevicesByProtocol(protocolType: String): List<PairedDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: PairedDeviceEntity)

    @Update
    suspend fun update(device: PairedDeviceEntity)

    @Query("UPDATE paired_devices SET pairing_token = :token, auth_status = :authStatus, last_seen_timestamp = :timestamp WHERE id = :id")
    suspend fun updatePairingStatus(id: String, token: String?, authStatus: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(device: PairedDeviceEntity)

    @Query("DELETE FROM paired_devices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM paired_devices")
    suspend fun deleteAll()
}
