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

    @Query("SELECT * FROM service_requests WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun getOpenRequests(): Flow<List<ServiceRequestEntity>>

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

    @Query("UPDATE service_requests SET status = :status, assignedMechanicId = :mechanicId, assignedMechanicName = :mechanicName, assignedMechanicPhone = :mechanicPhone, priceOffer = :finalPrice, escrowStatus = 'HELD' WHERE requestId = :requestId")
    suspend fun updateMechanicStatusAndPrice(requestId: String, status: String, mechanicId: String, mechanicName: String, mechanicPhone: String, finalPrice: Double)

    @Query("UPDATE service_requests SET status = :status, completedAt = :completedAt, escrowStatus = 'RELEASED' WHERE requestId = :requestId")
    suspend fun completeServiceWithEscrow(requestId: String, status: String, completedAt: Long)

    @Query("UPDATE service_requests SET status = 'OPEN', assignedMechanicId = null, assignedMechanicName = null, assignedMechanicPhone = null, escrowStatus = 'REFUNDED' WHERE requestId = :requestId")
    suspend fun cancelServiceWithEscrow(requestId: String)

    @Query("UPDATE service_bids SET status = :status WHERE bidId = :bidId")
    suspend fun updateBidStatus(bidId: String, status: String)

    @Query("UPDATE service_bids SET status = 'REJECTED' WHERE requestId = :requestId AND bidId != :acceptedBidId")
    suspend fun rejectOtherBids(requestId: String, acceptedBidId: String)

    @Query("SELECT * FROM service_bids WHERE bidId = :bidId")
    suspend fun getBidById(bidId: String): ServiceBidEntity?

    @Query("SELECT * FROM service_bids WHERE shopId = :shopId ORDER BY createdAt DESC")
    fun getBidsByShop(shopId: String): Flow<List<ServiceBidEntity>>

    @Query("SELECT * FROM parts_stores ORDER BY verified DESC, rating DESC, averageEtaMinutes ASC")
    fun getPartsStores(): Flow<List<PartsStoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartsStore(store: PartsStoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartsStores(stores: List<PartsStoreEntity>)

    @Query("SELECT * FROM part_requests ORDER BY createdAt DESC")
    fun getPartRequests(): Flow<List<PartRequestEntity>>

    @Query("SELECT * FROM part_requests WHERE status = 'OPEN' ORDER BY urgencyMinutes ASC, createdAt DESC")
    fun getOpenPartRequests(): Flow<List<PartRequestEntity>>

    @Query("SELECT * FROM part_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getPartRequestById(requestId: String): PartRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartRequest(request: PartRequestEntity)

    @Query("SELECT * FROM part_offers WHERE partRequestId = :requestId ORDER BY etaMinutes ASC, price ASC")
    fun getPartOffersForRequest(requestId: String): Flow<List<PartOfferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartOffer(offer: PartOfferEntity)

    @Query("UPDATE part_requests SET status = :status, acceptedOfferId = :acceptedOfferId WHERE requestId = :requestId")
    suspend fun updatePartRequestStatus(requestId: String, status: String, acceptedOfferId: String?)

    @Query("UPDATE part_offers SET status = :status WHERE offerId = :offerId")
    suspend fun updatePartOfferStatus(offerId: String, status: String)

    @Query("UPDATE part_offers SET status = 'REJECTED' WHERE partRequestId = :partRequestId AND offerId != :acceptedOfferId")
    suspend fun rejectOtherPartOffers(partRequestId: String, acceptedOfferId: String)

    @Query("SELECT * FROM part_offers WHERE offerId = :offerId")
    suspend fun getPartOfferById(offerId: String): PartOfferEntity?
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

@Dao
interface TowTruckDao {
    @Query("SELECT * FROM tow_truck_requests ORDER BY createdAt DESC")
    fun getRequestsFlow(): Flow<List<TowTruckRequestEntity>>

    @Query("SELECT * FROM tow_truck_requests WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun getOpenRequestsFlow(): Flow<List<TowTruckRequestEntity>>

    @Query("SELECT * FROM tow_truck_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getRequestById(requestId: String): TowTruckRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: TowTruckRequestEntity)

    @Query("UPDATE tow_truck_requests SET status = :status, assignedDriverId = :driverId, assignedDriverName = :driverName, assignedDriverPhone = :driverPhone WHERE requestId = :requestId")
    suspend fun updateDriverAndStatus(requestId: String, status: String, driverId: String?, driverName: String?, driverPhone: String?)

    @Query("UPDATE tow_truck_requests SET status = :status, completedAt = :completedAt WHERE requestId = :requestId")
    suspend fun updateRequestStatusAndCompletedTime(requestId: String, status: String, completedAt: Long?)

    @Query("DELETE FROM tow_truck_requests WHERE requestId = :requestId")
    suspend fun deleteRequest(requestId: String)

    @Query("DELETE FROM tow_truck_requests WHERE (status = 'COMPLETED' OR status = 'CANCELLED') AND createdAt < :thresholdTime")
    suspend fun purgeOldRequests(thresholdTime: Long)
}

@Dao
interface RatingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: RatingEntity)

    @Query("SELECT * FROM ratings WHERE targetType = :targetType AND targetId = :targetId ORDER BY createdAt DESC")
    fun getRatingsForTargetFlow(targetType: String, targetId: String): Flow<List<RatingEntity>>

    @Query("SELECT AVG(stars) FROM ratings WHERE targetType = :targetType AND targetId = :targetId")
    suspend fun getAverageRatingForTarget(targetType: String, targetId: String): Double?

    @Query("SELECT * FROM ratings ORDER BY createdAt DESC")
    fun getAllRatingsFlow(): Flow<List<RatingEntity>>
}

@Dao
interface ProviderProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProviderProfileEntity)

    @Query("SELECT * FROM provider_profiles WHERE userId = :userId ORDER BY isActive DESC, updatedAt DESC")
    fun getProfilesForUser(userId: String): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles WHERE userId = :userId AND isActive = 1")
    fun getActiveProfilesForUser(userId: String): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles WHERE userId = :userId AND providerType = :type LIMIT 1")
    suspend fun getProfileByUserAndType(userId: String, type: String): ProviderProfileEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM provider_profiles WHERE userId = :userId AND providerType = :type AND isActive = 1)")
    suspend fun isUserRegisteredAs(userId: String, type: String): Boolean

    @Query("SELECT * FROM provider_profiles WHERE providerType = :type AND isActive = 1 ORDER BY rating DESC")
    fun getActiveProvidersByType(type: String): Flow<List<ProviderProfileEntity>>

    @Query("UPDATE provider_profiles SET isActive = :isActive, updatedAt = :updatedAt WHERE profileId = :profileId")
    suspend fun setProfileActive(profileId: String, isActive: Boolean, updatedAt: Long)

    @Query("UPDATE provider_profiles SET rating = :rating, totalJobs = totalJobs + 1, updatedAt = :updatedAt WHERE profileId = :profileId")
    suspend fun updateRatingAndJobs(profileId: String, rating: Double, updatedAt: Long)

    @Query("DELETE FROM provider_profiles WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)
}

@Dao
interface RideDao {
    @Query("SELECT * FROM ride_requests ORDER BY createdAt DESC")
    fun getAllRequestsFlow(): Flow<List<RideRequestEntity>>

    @Query("SELECT * FROM ride_requests WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun getOpenRequestsFlow(): Flow<List<RideRequestEntity>>

    @Query("SELECT * FROM ride_requests WHERE passengerId = :passengerId ORDER BY createdAt DESC")
    fun getRequestsByPassenger(passengerId: String): Flow<List<RideRequestEntity>>

    @Query("SELECT * FROM ride_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getRequestById(requestId: String): RideRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: RideRequestEntity)

    @Query("UPDATE ride_requests SET status = :status WHERE requestId = :requestId")
    suspend fun updateRequestStatus(requestId: String, status: String)

    @Query("UPDATE ride_requests SET status = 'ACCEPTED', acceptedOfferId = :offerId, assignedDriverId = :driverId, assignedDriverName = :driverName, assignedDriverPhone = :driverPhone, assignedDriverVehicle = :vehicle, finalPrice = :price WHERE requestId = :requestId")
    suspend fun acceptOffer(requestId: String, offerId: String, driverId: String, driverName: String, driverPhone: String, vehicle: String, price: Double)

    @Query("UPDATE ride_requests SET passengerRating = :rating WHERE requestId = :requestId")
    suspend fun updatePassengerRating(requestId: String, rating: Double)

    @Query("UPDATE ride_requests SET driverRating = :rating WHERE requestId = :requestId")
    suspend fun updateDriverRating(requestId: String, rating: Double)

    @Query("DELETE FROM ride_requests WHERE requestId = :requestId")
    suspend fun deleteRequest(requestId: String)

    // Offers
    @Query("SELECT * FROM ride_offers WHERE requestId = :requestId ORDER BY counterPrice ASC, createdAt DESC")
    fun getOffersForRequest(requestId: String): Flow<List<RideOfferEntity>>

    @Query("SELECT * FROM ride_offers WHERE offerId = :offerId LIMIT 1")
    suspend fun getOfferById(offerId: String): RideOfferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffer(offer: RideOfferEntity)

    @Query("UPDATE ride_offers SET status = :status WHERE offerId = :offerId")
    suspend fun updateOfferStatus(offerId: String, status: String)

    @Query("UPDATE ride_offers SET status = 'REJECTED' WHERE requestId = :requestId AND offerId != :acceptedOfferId")
    suspend fun rejectOtherOffers(requestId: String, acceptedOfferId: String)

    // Chat
    @Query("SELECT * FROM ride_chat_messages WHERE rideRequestId = :rideRequestId ORDER BY createdAt ASC")
    fun getChatMessagesFlow(rideRequestId: String): Flow<List<RideChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: RideChatMessageEntity)

    @Query("UPDATE ride_chat_messages SET isRead = 1 WHERE rideRequestId = :rideRequestId AND senderId != :userId")
    suspend fun markMessagesAsRead(rideRequestId: String, userId: String)

    @Query("SELECT COUNT(*) FROM ride_chat_messages WHERE rideRequestId = :rideRequestId AND senderId != :userId AND isRead = 0")
    fun getUnreadCountFlow(rideRequestId: String, userId: String): Flow<Int>

    // ── Driver Verifications ─────────────────────────────────────────────────

    @Query("SELECT * FROM driver_verifications WHERE driverId = :driverId LIMIT 1")
    fun getDriverVerificationFlow(driverId: String): Flow<DriverVerificationEntity?>

    @Query("SELECT * FROM driver_verifications WHERE driverId = :driverId LIMIT 1")
    suspend fun getDriverVerification(driverId: String): DriverVerificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriverVerification(entity: DriverVerificationEntity)

    @Query("UPDATE driver_verifications SET status = :status, approvedAt = :approvedAt, updatedAt = :updatedAt WHERE driverId = :driverId")
    suspend fun updateDriverVerificationStatus(driverId: String, status: String, approvedAt: Long?, updatedAt: Long)

    @Query("UPDATE driver_verifications SET status = 'REJECTED', rejectionReason = :reason, updatedAt = :updatedAt WHERE driverId = :driverId")
    suspend fun rejectDriverVerification(driverId: String, reason: String, updatedAt: Long)

    @Query("DELETE FROM driver_verifications WHERE driverId = :driverId")
    suspend fun deleteDriverVerification(driverId: String)

    // ── Passenger Verifications ──────────────────────────────────────────────

    @Query("SELECT * FROM passenger_verifications WHERE passengerId = :passengerId LIMIT 1")
    fun getPassengerVerificationFlow(passengerId: String): Flow<PassengerVerificationEntity?>

    @Query("SELECT * FROM passenger_verifications WHERE passengerId = :passengerId LIMIT 1")
    suspend fun getPassengerVerification(passengerId: String): PassengerVerificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassengerVerification(entity: PassengerVerificationEntity)

    @Query("UPDATE passenger_verifications SET status = :status, approvedAt = :approvedAt WHERE passengerId = :passengerId")
    suspend fun updatePassengerVerificationStatus(passengerId: String, status: String, approvedAt: Long?)

    @Query("DELETE FROM passenger_verifications WHERE passengerId = :passengerId")
    suspend fun deletePassengerVerification(passengerId: String)
}
