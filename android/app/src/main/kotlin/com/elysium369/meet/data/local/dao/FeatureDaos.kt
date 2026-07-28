package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.core.access.CommissionEngine
import com.elysium369.meet.core.access.TransactionKind
import com.elysium369.meet.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import kotlin.math.roundToLong

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVanguardEvent(event: VanguardEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMarketplaceLedgerEntries(entries: List<MarketplaceLedgerEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVanguardOutbox(message: VanguardOutboxEntity): Long

    @Transaction
    suspend fun upsertServiceRequestFromSync(request: ServiceRequestEntity): Boolean {
        val local = getRequestById(request.requestId)
        if (local == null) {
            insertRequest(request)
            return true
        }

        val localHasLocalClaim = local.status != "OPEN" && request.status == "OPEN"
        val localPaymentFailed = local.escrowStatus == "REFUNDED" && request.escrowStatus != "REFUNDED"
        val localIsTerminal = local.status == "COMPLETED" || local.status == "CANCELLED"
        if (localHasLocalClaim || localPaymentFailed || (localIsTerminal && request.status != local.status)) {
            return false
        }

        insertRequest(request)
        return true
    }

    @Query("SELECT * FROM service_bids WHERE requestId = :requestId ORDER BY createdAt DESC")
    fun getBidsForRequest(requestId: String): Flow<List<ServiceBidEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBid(bid: ServiceBidEntity)

    @Transaction
    suspend fun upsertBidRespectingRequestClaim(bid: ServiceBidEntity): Boolean {
        val localBid = getBidById(bid.bidId)
        val request = getRequestById(bid.requestId)
        if (localBid?.status == "ACCEPTED" && request?.escrowStatus != "REFUNDED") return true

        val normalizedBid = when {
            request?.escrowStatus == "REFUNDED" -> bid.copy(status = "PENDING")
            request == null || request.status == "OPEN" -> bid
            request.status == "ACCEPTED" && bid.status == "ACCEPTED" && request.assignedMechanicId == bid.shopId -> bid
            else -> bid.copy(status = "REJECTED")
        }
        insertBid(normalizedBid)
        return normalizedBid.status != "REJECTED"
    }

    @Query("UPDATE service_requests SET status = :status WHERE requestId = :requestId")
    suspend fun updateRequestStatus(requestId: String, status: String)

    @Query("UPDATE service_requests SET status = :status, assignedMechanicId = :mechanicId, assignedMechanicName = :mechanicName, assignedMechanicPhone = :mechanicPhone, priceOffer = :finalPrice, escrowStatus = 'HELD' WHERE requestId = :requestId")
    suspend fun updateMechanicStatusAndPrice(requestId: String, status: String, mechanicId: String, mechanicName: String, mechanicPhone: String, finalPrice: Double)

    @Query("UPDATE service_requests SET status = 'ACCEPTED', assignedMechanicId = :mechanicId, assignedMechanicName = :mechanicName, assignedMechanicPhone = :mechanicPhone, priceOffer = :finalPrice, escrowStatus = 'HELD' WHERE requestId = :requestId AND status = 'OPEN'")
    suspend fun claimOpenRequestForMechanic(requestId: String, mechanicId: String, mechanicName: String, mechanicPhone: String, finalPrice: Double): Int

    @Query("UPDATE service_requests SET status = :status, completedAt = :completedAt, escrowStatus = 'RELEASED' WHERE requestId = :requestId")
    suspend fun completeServiceWithEscrow(requestId: String, status: String, completedAt: Long)

    @Query("UPDATE service_requests SET status = 'COMPLETED', completedAt = :completedAt, escrowStatus = 'RELEASED' WHERE requestId = :requestId AND status = 'ACCEPTED'")
    suspend fun completeAcceptedServiceWithEscrow(requestId: String, completedAt: Long): Int

    @Query("UPDATE service_requests SET status = 'OPEN', assignedMechanicId = null, assignedMechanicName = null, assignedMechanicPhone = null, escrowStatus = 'REFUNDED' WHERE requestId = :requestId")
    suspend fun cancelServiceWithEscrow(requestId: String)

    @Query("UPDATE service_bids SET status = 'PENDING' WHERE requestId = :requestId AND status = 'ACCEPTED'")
    suspend fun reopenAcceptedBidsForPaymentFailure(requestId: String)

    @Query("UPDATE service_bids SET status = :status WHERE bidId = :bidId")
    suspend fun updateBidStatus(bidId: String, status: String)

    @Query("UPDATE service_bids SET status = 'REJECTED' WHERE requestId = :requestId AND bidId != :acceptedBidId")
    suspend fun rejectOtherBids(requestId: String, acceptedBidId: String)

    @Query("SELECT * FROM service_bids WHERE bidId = :bidId")
    suspend fun getBidById(bidId: String): ServiceBidEntity?

    @Query("SELECT * FROM service_bids WHERE shopId = :shopId ORDER BY createdAt DESC")
    fun getBidsByShop(shopId: String): Flow<List<ServiceBidEntity>>

    @Transaction
    suspend fun acceptBidAtomically(
        requestId: String,
        bidId: String,
        mechanicPhone: String
    ): Boolean {
        val request = getRequestById(requestId) ?: return false
        val bid = getBidById(bidId) ?: return false
        if (bid.requestId != requestId) return false

        if (request.status == "ACCEPTED") {
            return request.assignedMechanicId == bid.shopId && bid.status == "ACCEPTED"
        }
        if (request.status != "OPEN") return false

        val claimed = claimOpenRequestForMechanic(
            requestId = requestId,
            mechanicId = bid.shopId,
            mechanicName = bid.shopName,
            mechanicPhone = mechanicPhone,
            finalPrice = bid.price
        )
        if (claimed == 0) return false

        updateBidStatus(bidId, "ACCEPTED")
        rejectOtherBids(requestId, bidId)
        recordMarketplaceEvent(
            aggregateType = "SERVICE_REQUEST",
            aggregateId = requestId,
            eventType = "SERVICE_BID_ACCEPTED",
            actorId = bid.shopId,
            actorRole = "MECHANIC",
            idempotencyKey = "service:$requestId:bid:$bidId:accepted",
            payloadJson = jsonOf(
                "requestId" to requestId,
                "bidId" to bidId,
                "mechanicId" to bid.shopId,
                "mechanicName" to bid.shopName,
                "finalPrice" to bid.price,
                "escrowStatus" to "HELD"
            )
        )
        return true
    }

    @Transaction
    suspend fun takeMechanicRequestAtomically(
        requestId: String,
        mechanicId: String,
        mechanicName: String,
        mechanicPhone: String,
        finalPrice: Double
    ): Boolean {
        val request = getRequestById(requestId) ?: return false
        if (request.status == "ACCEPTED") {
            return request.assignedMechanicId == mechanicId
        }
        if (request.status != "OPEN") return false
        val claimed = claimOpenRequestForMechanic(
            requestId = requestId,
            mechanicId = mechanicId,
            mechanicName = mechanicName,
            mechanicPhone = mechanicPhone,
            finalPrice = finalPrice
        )
        if (claimed == 0) return false

        recordMarketplaceEvent(
            aggregateType = "SERVICE_REQUEST",
            aggregateId = requestId,
            eventType = "SERVICE_REQUEST_TAKEN",
            actorId = mechanicId,
            actorRole = "MECHANIC",
            idempotencyKey = "service:$requestId:mechanic:$mechanicId:taken",
            payloadJson = jsonOf(
                "requestId" to requestId,
                "mechanicId" to mechanicId,
                "mechanicName" to mechanicName,
                "finalPrice" to finalPrice,
                "escrowStatus" to "HELD"
            )
        )
        return true
    }

    @Transaction
    suspend fun completeAcceptedServiceOnce(requestId: String, completedAt: Long): Boolean {
        val request = getRequestById(requestId) ?: return false
        if (request.status == "COMPLETED") return true
        if (request.status != "ACCEPTED") return false
        val completed = completeAcceptedServiceWithEscrow(requestId, completedAt)
        if (completed == 0) return false

        val eventId = recordMarketplaceEvent(
            aggregateType = "SERVICE_REQUEST",
            aggregateId = requestId,
            eventType = "SERVICE_COMPLETED",
            actorId = request.assignedMechanicId,
            actorRole = "MECHANIC",
            idempotencyKey = "service:$requestId:completed",
            payloadJson = jsonOf(
                "requestId" to requestId,
                "mechanicId" to request.assignedMechanicId,
                "finalPrice" to request.priceOffer,
                "escrowStatus" to "RELEASED",
                "completedAt" to completedAt
            ),
            occurredAt = completedAt
        )
        recordServiceCompletionLedger(request, relatedEventId = eventId, createdAt = completedAt)
        return true
    }

    @Transaction
    suspend fun refundServiceAfterPaymentFailure(requestId: String): Boolean {
        val request = getRequestById(requestId) ?: return false
        if (request.escrowStatus == "REFUNDED") return true
        cancelServiceWithEscrow(requestId)
        reopenAcceptedBidsForPaymentFailure(requestId)
        val now = System.currentTimeMillis()
        val eventId = recordMarketplaceEvent(
            aggregateType = "SERVICE_REQUEST",
            aggregateId = requestId,
            eventType = "SERVICE_PAYMENT_REFUNDED",
            actorId = request.assignedMechanicId,
            actorRole = "SYSTEM",
            idempotencyKey = "service:$requestId:payment-refunded",
            payloadJson = jsonOf(
                "requestId" to requestId,
                "previousStatus" to request.status,
                "previousEscrowStatus" to request.escrowStatus,
                "finalPrice" to request.priceOffer
            ),
            occurredAt = now
        )
        recordServiceRefundLedger(request, relatedEventId = eventId, createdAt = now)
        return true
    }

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

    @Transaction
    suspend fun upsertPartRequestFromSync(request: PartRequestEntity): Boolean {
        val local = getPartRequestById(request.requestId)
        if (local == null) {
            insertPartRequest(request)
            return true
        }

        val localHasLocalClaim = local.status != "OPEN" && request.status == "OPEN"
        val localIsTerminal = local.status == "DELIVERED" || local.status == "CANCELLED"
        if (localHasLocalClaim || (localIsTerminal && request.status != local.status)) {
            return false
        }

        insertPartRequest(request)
        return true
    }

    @Query("SELECT * FROM part_offers WHERE partRequestId = :requestId ORDER BY etaMinutes ASC, price ASC")
    fun getPartOffersForRequest(requestId: String): Flow<List<PartOfferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartOffer(offer: PartOfferEntity)

    @Transaction
    suspend fun upsertPartOfferRespectingRequestClaim(offer: PartOfferEntity): Boolean {
        val localOffer = getPartOfferById(offer.offerId)
        if (localOffer?.status == "ACCEPTED") return true

        val request = getPartRequestById(offer.partRequestId)
        val normalizedOffer = when {
            request == null || request.status == "OPEN" -> offer
            request.status == "ACCEPTED" && request.acceptedOfferId == offer.offerId -> offer.copy(status = "ACCEPTED")
            else -> offer.copy(status = "REJECTED")
        }
        insertPartOffer(normalizedOffer)
        return normalizedOffer.status != "REJECTED"
    }

    @Query("UPDATE part_requests SET status = :status, acceptedOfferId = :acceptedOfferId WHERE requestId = :requestId")
    suspend fun updatePartRequestStatus(requestId: String, status: String, acceptedOfferId: String?)

    @Query("UPDATE part_requests SET status = 'ACCEPTED', acceptedOfferId = :acceptedOfferId WHERE requestId = :requestId AND status = 'OPEN'")
    suspend fun claimOpenPartRequestForOffer(requestId: String, acceptedOfferId: String): Int

    @Query("UPDATE part_offers SET status = :status WHERE offerId = :offerId")
    suspend fun updatePartOfferStatus(offerId: String, status: String)

    @Query("UPDATE part_offers SET status = 'REJECTED' WHERE partRequestId = :partRequestId AND offerId != :acceptedOfferId")
    suspend fun rejectOtherPartOffers(partRequestId: String, acceptedOfferId: String)

    @Query("SELECT * FROM part_offers WHERE offerId = :offerId")
    suspend fun getPartOfferById(offerId: String): PartOfferEntity?

    @Transaction
    suspend fun acceptPartOfferAtomically(partRequestId: String, offerId: String): Boolean {
        val request = getPartRequestById(partRequestId) ?: return false
        val offer = getPartOfferById(offerId) ?: return false
        if (offer.partRequestId != partRequestId) return false

        if (request.status == "ACCEPTED") {
            return request.acceptedOfferId == offerId && offer.status == "ACCEPTED"
        }
        if (request.status != "OPEN") return false

        val claimed = claimOpenPartRequestForOffer(partRequestId, offerId)
        if (claimed == 0) return false

        updatePartOfferStatus(offerId, "ACCEPTED")
        rejectOtherPartOffers(partRequestId, offerId)
        recordMarketplaceEvent(
            aggregateType = "PART_REQUEST",
            aggregateId = partRequestId,
            eventType = "PART_OFFER_ACCEPTED",
            actorId = offer.storeId,
            actorRole = "PARTS_STORE",
            idempotencyKey = "part:$partRequestId:offer:$offerId:accepted",
            payloadJson = jsonOf(
                "partRequestId" to partRequestId,
                "offerId" to offerId,
                "storeId" to offer.storeId,
                "storeName" to offer.storeName,
                "partNumber" to offer.partNumber,
                "price" to offer.price,
                "deliveryFee" to offer.deliveryFee,
                "etaMinutes" to offer.etaMinutes
            )
        )
        return true
    }

    private suspend fun recordMarketplaceEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        actorId: String?,
        actorRole: String?,
        idempotencyKey: String,
        payloadJson: String,
        occurredAt: Long = System.currentTimeMillis(),
        correlationId: String? = aggregateId,
        causationId: String? = null
    ): String {
        val eventId = stableId("event:$idempotencyKey")
        insertVanguardEvent(
            VanguardEventEntity(
                eventId = eventId,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                actorId = actorId,
                actorRole = actorRole,
                source = "LOCAL_ROOM",
                correlationId = correlationId,
                causationId = causationId,
                idempotencyKey = idempotencyKey,
                payloadJson = payloadJson,
                schemaVersion = 1,
                occurredAt = occurredAt,
                synced = false
            )
        )
        insertVanguardOutbox(
            VanguardOutboxEntity(
                outboxId = stableId("outbox:$idempotencyKey"),
                eventId = eventId,
                destination = "SUPABASE_EVENTS",
                operation = "UPSERT_VANGUARD_EVENT",
                payloadJson = payloadJson,
                status = "PENDING",
                attemptCount = 0,
                nextAttemptAt = occurredAt,
                lastError = null,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                idempotencyKey = "outbox:$idempotencyKey"
            )
        )
        return eventId
    }

    private suspend fun recordServiceCompletionLedger(
        request: ServiceRequestEntity,
        relatedEventId: String,
        createdAt: Long
    ) {
        val grossCents = moneyCents(request.priceOffer)
        if (grossCents <= 0L) return

        val commission = CommissionEngine.decide(
            transactionKind = TransactionKind.REPAIR_SERVICE,
            grossCents = grossCents
        )
        val transactionId = stableId("txn:service:${request.requestId}:completion")
        val metadata = jsonOf(
            "requestId" to request.requestId,
            "paymentId" to request.paymentId,
            "commissionRateBps" to commission.rateBps,
            "commissionPolicy" to commission.policyCode,
            "escrowStatus" to "RELEASED"
        )

        insertMarketplaceLedgerEntries(
            listOf(
                ledgerEntry(
                    transactionId = transactionId,
                    relatedEventId = relatedEventId,
                    orderId = request.requestId,
                    participantId = "escrow:${request.requestId}",
                    participantRole = "ESCROW",
                    entryType = "GROSS_CAPTURE",
                    amountCents = grossCents,
                    status = "POSTED",
                    metadataJson = metadata,
                    createdAt = createdAt
                ),
                ledgerEntry(
                    transactionId = transactionId,
                    relatedEventId = relatedEventId,
                    orderId = request.requestId,
                    participantId = "elysium_platform",
                    participantRole = "PLATFORM",
                    entryType = "PLATFORM_COMMISSION",
                    amountCents = commission.platformCommissionCents,
                    status = "POSTED",
                    metadataJson = metadata,
                    createdAt = createdAt
                ),
                ledgerEntry(
                    transactionId = transactionId,
                    relatedEventId = relatedEventId,
                    orderId = request.requestId,
                    participantId = request.assignedMechanicId,
                    participantRole = "PROVIDER",
                    entryType = "PROVIDER_PAYOUT",
                    amountCents = commission.providerPayoutCents,
                    status = "PENDING",
                    metadataJson = metadata,
                    createdAt = createdAt
                )
            )
        )
    }

    private suspend fun recordServiceRefundLedger(
        request: ServiceRequestEntity,
        relatedEventId: String,
        createdAt: Long
    ) {
        val grossCents = moneyCents(request.priceOffer)
        if (grossCents <= 0L) return

        val transactionId = stableId("txn:service:${request.requestId}:refund")
        insertMarketplaceLedgerEntries(
            listOf(
                ledgerEntry(
                    transactionId = transactionId,
                    relatedEventId = relatedEventId,
                    orderId = request.requestId,
                    participantId = "customer:${request.requestId}",
                    participantRole = "CUSTOMER",
                    entryType = "PAYMENT_REFUND",
                    direction = "CREDIT",
                    amountCents = grossCents,
                    status = "POSTED",
                    metadataJson = jsonOf(
                        "requestId" to request.requestId,
                        "paymentId" to request.paymentId,
                        "previousEscrowStatus" to request.escrowStatus
                    ),
                    createdAt = createdAt
                )
            )
        )
    }

    private fun ledgerEntry(
        transactionId: String,
        relatedEventId: String,
        orderId: String,
        participantId: String?,
        participantRole: String,
        entryType: String,
        amountCents: Long,
        status: String,
        metadataJson: String,
        createdAt: Long,
        direction: String = "CREDIT"
    ): MarketplaceLedgerEntryEntity {
        val idempotencyKey = "ledger:SERVICE_REPAIR:$orderId:$entryType"
        return MarketplaceLedgerEntryEntity(
            ledgerEntryId = stableId(idempotencyKey),
            transactionId = transactionId,
            relatedEventId = relatedEventId,
            orderType = "SERVICE_REPAIR",
            orderId = orderId,
            participantId = participantId,
            participantRole = participantRole,
            entryType = entryType,
            direction = direction,
            amountCents = amountCents,
            currency = "USD",
            status = status,
            metadataJson = metadataJson,
            createdAt = createdAt,
            settledAt = null,
            idempotencyKey = idempotencyKey,
            synced = false
        )
    }

    private fun stableId(seed: String): String {
        return java.util.UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
    }

    private fun moneyCents(amount: Double): Long {
        return (amount.coerceAtLeast(0.0) * 100.0).roundToLong()
    }

    private fun jsonOf(vararg fields: Pair<String, Any?>): String {
        val json = JSONObject()
        fields.forEach { (key, value) ->
            json.put(key, value ?: JSONObject.NULL)
        }
        return json.toString()
    }
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

    @Query(
        """
        UPDATE ride_requests
        SET status = 'ACCEPTED',
            assignedDriverId = :driverId,
            assignedDriverName = :driverName,
            assignedDriverPhone = :driverPhone,
            assignedDriverVehicle = :vehicle,
            finalPrice = priceOffer
        WHERE requestId = :requestId
          AND status = 'OPEN'
          AND assignedDriverId IS NULL
        """
    )
    suspend fun claimOpenRequest(
        requestId: String,
        driverId: String,
        driverName: String,
        driverPhone: String,
        vehicle: String,
    ): Int

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
