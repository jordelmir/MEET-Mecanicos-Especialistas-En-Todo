package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.DiagnosticFindingEntity

@Dao
interface DiagnosticFindingDao {
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
}
