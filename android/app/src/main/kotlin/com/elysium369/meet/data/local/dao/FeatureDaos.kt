package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveSessionDao {
    @Query("SELECT * FROM live_sessions WHERE sessionId = :sessionId")
    suspend fun getLiveSession(sessionId: String): LiveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiveSession(session: LiveSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiveSnapshot(snapshot: LiveSnapshotEntity)

    @Query("SELECT * FROM live_snapshots WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getSnapshotsForSession(sessionId: String): List<LiveSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMechanicNote(note: MechanicNoteEntity)

    @Query("SELECT * FROM mechanic_notes WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getNotesForSession(sessionId: String): Flow<List<MechanicNoteEntity>>

    @Query("UPDATE live_sessions SET status = :status WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String)

    @Query("UPDATE live_sessions SET endedAt = :endedAt WHERE sessionId = :sessionId")
    suspend fun updateSessionEndedAt(sessionId: String, endedAt: Long)
}

@Dao
interface RepairNetworkAddonsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: RepairPhotoEntity)

    @Query("SELECT * FROM repair_photos WHERE caseId = :caseId ORDER BY createdAt DESC")
    suspend fun getPhotosForCase(caseId: String): List<RepairPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: RepairPartEntity)

    @Query("SELECT * FROM repair_parts WHERE caseId = :caseId")
    suspend fun getPartsForCase(caseId: String): List<RepairPartEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVote(vote: RepairVoteEntity)

    @Query("SELECT * FROM repair_votes WHERE caseId = :caseId AND userId = :userId")
    suspend fun getVoteForCaseByUser(caseId: String, userId: String): RepairVoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: RepairCommentEntity)

    @Query("SELECT * FROM repair_comments WHERE caseId = :caseId ORDER BY createdAt DESC")
    fun getCommentsForCase(caseId: String): Flow<List<RepairCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerification(verification: RepairVerificationEntity)

    @Query("SELECT * FROM repair_verifications WHERE caseId = :caseId")
    suspend fun getVerificationForCase(caseId: String): RepairVerificationEntity?
}

@Dao
interface MarketplaceDao {
    @Query("SELECT * FROM service_requests ORDER BY createdAt DESC")
    fun getRequests(): Flow<List<ServiceRequestEntity>>

    @Query("SELECT * FROM service_requests WHERE requestId = :requestId")
    suspend fun getRequestById(requestId: String): ServiceRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: ServiceRequestEntity)

    @Query("SELECT * FROM service_bids WHERE requestId = :requestId ORDER BY createdAt DESC")
    fun getBidsForRequest(requestId: String): Flow<List<ServiceBidEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBid(bid: ServiceBidEntity)

    @Query("UPDATE service_requests SET status = :status WHERE requestId = :requestId")
    suspend fun updateRequestStatus(requestId: String, status: String)

    @Query("UPDATE service_bids SET status = :status WHERE bidId = :bidId")
    suspend fun updateBidStatus(bidId: String, status: String)

    @Query("SELECT * FROM service_bids WHERE shopId = :shopId ORDER BY createdAt DESC")
    fun getBidsByShop(shopId: String): Flow<List<ServiceBidEntity>>
}

@Dao
interface BlackBoxDao {
    @Query("SELECT * FROM evidence_packages ORDER BY timestamp DESC")
    fun getEvidencePackages(): Flow<List<EvidencePackageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidencePackage(pack: EvidencePackageEntity)

    @Query("DELETE FROM evidence_packages WHERE packageId = :packageId")
    suspend fun deleteEvidencePackage(packageId: String)
}

@Dao
interface VehicleTwinDao {
    @Query("SELECT * FROM vehicle_twin_profiles WHERE vehicleId = :vehicleId")
    suspend fun getTwinProfile(vehicleId: String): VehicleTwinProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTwinProfile(profile: VehicleTwinProfileEntity)

    @Query("SELECT * FROM twin_anomalies WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getAnomaliesForVehicle(vehicleId: String): Flow<List<TwinAnomalyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(anomaly: TwinAnomalyEntity)

    @Query("DELETE FROM twin_anomalies WHERE vehicleId = :vehicleId")
    suspend fun clearAnomaliesForVehicle(vehicleId: String)
}
