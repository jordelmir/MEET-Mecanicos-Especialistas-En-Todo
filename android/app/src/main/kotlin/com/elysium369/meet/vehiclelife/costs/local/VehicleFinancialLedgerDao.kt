package com.elysium369.meet.vehiclelife.costs.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleFinancialLedgerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VehicleFinancialLedgerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<VehicleFinancialLedgerEntity>)

    @Query("SELECT * FROM vehicle_financial_ledger WHERE vehicleId = :vehicleId ORDER BY dateUtc DESC")
    fun observeEntriesForVehicle(vehicleId: String): Flow<List<VehicleFinancialLedgerEntity>>

    @Query("SELECT * FROM vehicle_financial_ledger WHERE vehicleId = :vehicleId ORDER BY dateUtc DESC")
    suspend fun getEntriesForVehicle(vehicleId: String): List<VehicleFinancialLedgerEntity>

    @Query("SELECT * FROM vehicle_financial_ledger ORDER BY dateUtc DESC")
    fun observeAllEntries(): Flow<List<VehicleFinancialLedgerEntity>>

    @Query("SELECT * FROM vehicle_financial_ledger ORDER BY dateUtc DESC")
    suspend fun getAllEntries(): List<VehicleFinancialLedgerEntity>

    @Query("SELECT * FROM vehicle_financial_ledger WHERE vehicleId = :vehicleId AND category = :category AND state = 'PAID' ORDER BY dateUtc DESC")
    suspend fun getPaidEntriesByCategory(vehicleId: String, category: String): List<VehicleFinancialLedgerEntity>

    @Query("SELECT * FROM vehicle_financial_ledger WHERE vehicleId = :vehicleId AND dateUtc >= :sinceUtc ORDER BY dateUtc DESC")
    suspend fun getEntriesSince(vehicleId: String, sinceUtc: Long): List<VehicleFinancialLedgerEntity>

    @Query("DELETE FROM vehicle_financial_ledger WHERE entryId = :entryId")
    suspend fun deleteById(entryId: String)

    @Query("DELETE FROM vehicle_financial_ledger WHERE vehicleId = :vehicleId")
    suspend fun clearForVehicle(vehicleId: String)
}
