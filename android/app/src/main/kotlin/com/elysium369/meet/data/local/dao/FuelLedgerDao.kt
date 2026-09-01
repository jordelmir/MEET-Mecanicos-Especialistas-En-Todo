package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.FuelRewardLedgerEntryEntity
import com.elysium369.meet.data.local.entities.FuelStationPriceObservationEntity
import com.elysium369.meet.data.local.entities.FuelTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelLedgerDao {
    @Query("SELECT * FROM fuel_transactions_local WHERE ownerPrincipalId = :ownerId ORDER BY occurredAtEpochMs DESC")
    fun observeTransactions(ownerId: String): Flow<List<FuelTransactionEntity>>

    @Query("SELECT * FROM fuel_reward_ledger_local WHERE ownerPrincipalId = :ownerId AND authorityState = 'SERVER_CONFIRMED' ORDER BY serverVersion DESC")
    fun observeConfirmedRewards(ownerId: String): Flow<List<FuelRewardLedgerEntryEntity>>

    @Query("SELECT * FROM fuel_station_price_observations WHERE ownerPrincipalId = :ownerId AND expiresAtEpochMs > :nowEpochMs ORDER BY observedAtEpochMs DESC")
    fun observeFreshPrices(ownerId: String, nowEpochMs: Long): Flow<List<FuelStationPriceObservationEntity>>

    @Query("SELECT * FROM fuel_transactions_local WHERE ownerPrincipalId = :ownerId AND vehicleId = :vehicleId AND odometerMeters IS NOT NULL AND occurredAtEpochMs < :beforeEpochMs ORDER BY occurredAtEpochMs DESC LIMIT 1")
    suspend fun latestTransactionWithOdometer(
        ownerId: String,
        vehicleId: String,
        beforeEpochMs: Long,
    ): FuelTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: FuelTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRewardEntry(entry: FuelRewardLedgerEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPriceObservation(observation: FuelStationPriceObservationEntity)
}
