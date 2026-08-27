package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.elysium369.meet.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketOsDao {
    @Query("SELECT * FROM market_organization_projections WHERE ownerPrincipalId = :ownerId ORDER BY updatedAtEpochMs DESC")
    fun observeOrganizations(ownerId: String): Flow<List<MarketOrganizationProjectionEntity>>

    @Query("SELECT * FROM legal_matter_projections WHERE ownerPrincipalId = :ownerId ORDER BY updatedAtEpochMs DESC")
    fun observeLegalMatters(ownerId: String): Flow<List<LegalMatterProjectionEntity>>

    @Query("SELECT * FROM property_listing_projections WHERE ownerPrincipalId = :ownerId ORDER BY updatedAtEpochMs DESC")
    fun observePropertyListings(ownerId: String): Flow<List<PropertyListingProjectionEntity>>

    @Query("SELECT * FROM fuel_coupon_projections WHERE ownerPrincipalId = :ownerId ORDER BY expiresAtEpochMs ASC")
    fun observeFuelCoupons(ownerId: String): Flow<List<FuelCouponProjectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrganizations(rows: List<MarketOrganizationProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLegalMatters(rows: List<LegalMatterProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPropertyListings(rows: List<PropertyListingProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFuelCoupons(rows: List<FuelCouponProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(command: MarketCommandOutboxEntity): Long

    @Query("SELECT * FROM market_command_outbox WHERE ownerPrincipalId = :ownerId AND status IN ('PENDING','RETRYABLE') AND nextAttemptAtEpochMs <= :now ORDER BY createdAtEpochMs LIMIT :limit")
    suspend fun readyCommands(ownerId: String, now: Long, limit: Int): List<MarketCommandOutboxEntity>

    @Query("UPDATE market_command_outbox SET status = 'IN_FLIGHT', attemptCount = attemptCount + 1, updatedAtEpochMs = :now WHERE ownerPrincipalId = :ownerId AND idempotencyKey = :key AND status IN ('PENDING','RETRYABLE') AND nextAttemptAtEpochMs <= :now")
    suspend fun acquire(ownerId: String, key: String, now: Long): Int

    @Query("SELECT * FROM market_command_outbox WHERE ownerPrincipalId = :ownerId AND idempotencyKey = :key LIMIT 1")
    suspend fun command(ownerId: String, key: String): MarketCommandOutboxEntity?

    @Query("UPDATE market_command_outbox SET status = 'DELIVERED', payloadJson = '{\"redacted_after_ack\":true}', lastErrorCode = NULL, updatedAtEpochMs = :now WHERE ownerPrincipalId = :ownerId AND idempotencyKey = :key AND status = 'IN_FLIGHT'")
    suspend fun markDelivered(ownerId: String, key: String, now: Long)

    @Query("UPDATE market_command_outbox SET status = :status, nextAttemptAtEpochMs = :nextAttempt, lastErrorCode = :errorCode, updatedAtEpochMs = :now WHERE ownerPrincipalId = :ownerId AND idempotencyKey = :key AND status = 'IN_FLIGHT'")
    suspend fun markFailure(ownerId: String, key: String, status: String, nextAttempt: Long, errorCode: String, now: Long)

    @Query("UPDATE market_command_outbox SET status = 'RETRYABLE', nextAttemptAtEpochMs = :now, lastErrorCode = 'STALE_LEASE_RECOVERED', updatedAtEpochMs = :now WHERE ownerPrincipalId = :ownerId AND status = 'IN_FLIGHT' AND updatedAtEpochMs <= :staleBefore")
    suspend fun recoverStaleCommands(ownerId: String, staleBefore: Long, now: Long): Int

    @Query("SELECT COUNT(*) FROM market_command_outbox WHERE ownerPrincipalId = :ownerId AND status IN ('PENDING','IN_FLIGHT','RETRYABLE')")
    fun observePendingCommandCount(ownerId: String): Flow<Int>

    @Transaction
    suspend fun replaceLegalProjection(ownerId: String, rows: List<LegalMatterProjectionEntity>) {
        deleteLegalProjection(ownerId)
        upsertLegalMatters(rows)
    }

    @Transaction
    suspend fun replaceOrganizationProjection(ownerId: String, rows: List<MarketOrganizationProjectionEntity>) {
        clearOrganizations(ownerId)
        upsertOrganizations(rows)
    }

    @Transaction
    suspend fun replacePropertyProjection(ownerId: String, rows: List<PropertyListingProjectionEntity>) {
        clearPropertyListings(ownerId)
        upsertPropertyListings(rows)
    }

    @Transaction
    suspend fun replaceFuelProjection(ownerId: String, rows: List<FuelCouponProjectionEntity>) {
        clearFuelCoupons(ownerId)
        upsertFuelCoupons(rows)
    }

    @Query("DELETE FROM legal_matter_projections WHERE ownerPrincipalId = :ownerId")
    suspend fun deleteLegalProjection(ownerId: String)

    @Query("DELETE FROM market_organization_projections WHERE ownerPrincipalId = :ownerId")
    suspend fun clearOrganizations(ownerId: String)

    @Query("DELETE FROM property_listing_projections WHERE ownerPrincipalId = :ownerId")
    suspend fun clearPropertyListings(ownerId: String)

    @Query("DELETE FROM fuel_coupon_projections WHERE ownerPrincipalId = :ownerId")
    suspend fun clearFuelCoupons(ownerId: String)
}
