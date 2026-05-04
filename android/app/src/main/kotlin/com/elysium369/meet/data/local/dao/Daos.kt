package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicleById(id: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)
}

@Dao
interface DiagnosticSessionDao {
    @Query("SELECT * FROM diagnostic_sessions WHERE vehicleId = :vehicleId ORDER BY startedAt DESC")
    fun getSessionsForVehicle(vehicleId: String): Flow<List<DiagnosticSessionEntity>>

    @Query("SELECT * FROM diagnostic_sessions WHERE synced = 0")
    suspend fun getPendingSync(): List<DiagnosticSessionEntity>

    @Query("UPDATE diagnostic_sessions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DiagnosticSessionEntity)
}

@Dao
interface DtcDao {
    @Query("SELECT * FROM dtc_events WHERE sessionId = :sessionId")
    fun getDtcsForSession(sessionId: String): Flow<List<DtcEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDtc(dtc: DtcEventEntity)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId ORDER BY startedAt DESC")
    fun getTripsForVehicle(vehicleId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE synced = 0")
    suspend fun getPendingSync(): List<TripEntity>

    @Query("UPDATE trips SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)
}

@Dao
interface AdapterProfileDao {
    @Query("SELECT * FROM adapter_profiles WHERE deviceAddress = :address")
    suspend fun getProfile(address: String): AdapterProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: AdapterProfileEntity)
}

@Dao
interface DtcDefinitionDao {
    @Query("SELECT * FROM dtc_definitions WHERE code = :code")
    suspend fun getDefinitions(code: String): List<DtcDefinitionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefinitions(definitions: List<DtcDefinitionEntity>)
    
    @Query("SELECT COUNT(*) FROM dtc_definitions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM dtc_definitions WHERE code LIKE '%' || :query || '%'")
    suspend fun searchDefinitions(query: String): List<DtcDefinitionEntity>
}

@Dao
interface MaintenanceAlertDao {
    @Query("SELECT * FROM maintenance_alerts WHERE vehicleId = :vehicleId")
    fun getAlertsForVehicle(vehicleId: String): Flow<List<MaintenanceAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: MaintenanceAlertEntity)
}

@Dao
interface AiConsultDao {
    @Query("SELECT * FROM ai_consults WHERE sessionId = :sessionId")
    fun getConsultsForSession(sessionId: String): Flow<List<AiConsultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsult(consult: AiConsultEntity)
}
