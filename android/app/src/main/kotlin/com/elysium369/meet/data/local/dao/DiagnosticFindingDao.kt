package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.DiagnosticFindingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticFindingDao {
    @Query("SELECT * FROM diagnostic_findings WHERE vehicleId = :vehicleId ORDER BY createdAtMs DESC")
    fun observeForVehicle(vehicleId: String): Flow<List<DiagnosticFindingEntity>>

    @Query("SELECT * FROM diagnostic_findings WHERE id = :findingId LIMIT 1")
    fun observeById(findingId: String): Flow<DiagnosticFindingEntity?>

    @Query("SELECT * FROM diagnostic_findings WHERE id = :findingId LIMIT 1")
    suspend fun getById(findingId: String): DiagnosticFindingEntity?

    @Query("SELECT * FROM diagnostic_findings WHERE vehicleId = :vehicleId ORDER BY createdAtMs DESC")
    suspend fun getForVehicle(vehicleId: String): List<DiagnosticFindingEntity>

    @Query(
        """SELECT * FROM diagnostic_findings
           WHERE vehicleId = :vehicleId
             AND ecuEndpointId = :ecuEndpointId
             AND diagnosticNamespace = :namespace
             AND rawDtcIdentity = :rawDtcIdentity
           LIMIT 1""",
    )
    suspend fun getByStableIdentity(
        vehicleId: String,
        ecuEndpointId: String,
        namespace: String,
        rawDtcIdentity: String,
    ): DiagnosticFindingEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFinding(finding: DiagnosticFindingEntity): Long

    @Query("UPDATE diagnostic_findings SET resolutionState = 'OPEN', resolvedAtMs = NULL WHERE id = :findingId")
    suspend fun reopen(findingId: String)

    @Query("UPDATE diagnostic_findings SET resolutionState = 'VERIFIED_RESOLVED', resolvedAtMs = :resolvedAtMs WHERE id IN (:findingIds)")
    suspend fun resolveVerified(findingIds: List<String>, resolvedAtMs: Long)

    @Query("UPDATE diagnostic_findings SET resolutionState = :state, resolvedAtMs = :resolvedAtMs WHERE id = :findingId")
    suspend fun updateProjection(findingId: String, state: String, resolvedAtMs: Long?)
}
