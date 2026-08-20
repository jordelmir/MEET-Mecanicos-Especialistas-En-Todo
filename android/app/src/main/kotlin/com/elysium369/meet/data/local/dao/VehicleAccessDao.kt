package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.AccessAuditEventEntity
import com.elysium369.meet.data.local.entities.AccessGrantEntity
import com.elysium369.meet.data.local.entities.VehicleAccessCredentialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleAccessDao {

    // ── Credentials ──
    @Query("SELECT * FROM vehicle_access_credentials WHERE vehicleId = :vehicleId ORDER BY slotNumber ASC")
    fun getCredentialsForVehicle(vehicleId: String): Flow<List<VehicleAccessCredentialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredentials(credentials: List<VehicleAccessCredentialEntity>)

    @Update
    suspend fun updateCredential(credential: VehicleAccessCredentialEntity)

    @Query("DELETE FROM vehicle_access_credentials WHERE credentialId = :credentialId")
    suspend fun deleteCredential(credentialId: String)

    // ── Grants ──
    @Query("SELECT * FROM vehicle_access_grants WHERE vehicleId = :vehicleId ORDER BY validFromEpochMs DESC")
    fun getGrantsForVehicle(vehicleId: String): Flow<List<AccessGrantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrant(grant: AccessGrantEntity)

    @Update
    suspend fun updateGrant(grant: AccessGrantEntity)

    // ── Audit Events ──
    @Query("SELECT * FROM vehicle_access_audit_events WHERE vehicleId = :vehicleId ORDER BY timestampEpochMs DESC LIMIT 50")
    fun getAuditEventsForVehicle(vehicleId: String): Flow<List<AccessAuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEvent(event: AccessAuditEventEntity)
}
