package com.elysium369.meet.safety.science.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyInvestigativeDao {

    @Query("SELECT * FROM safety_investigative_cases ORDER BY updatedAt DESC")
    fun getAllCases(): Flow<List<InvestigativeCaseEntity>>

    @Query("SELECT * FROM safety_investigative_cases WHERE caseId = :caseId")
    suspend fun getCaseById(caseId: String): InvestigativeCaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(case: InvestigativeCaseEntity)

    @Query("SELECT * FROM safety_source_records ORDER BY retrievalTimestamp DESC")
    fun getAllSourceRecords(): Flow<List<SourceRecordEntity>>

    @Query("SELECT * FROM safety_source_records WHERE sourceRecordId = :recordId")
    suspend fun getSourceRecordById(recordId: String): SourceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSourceRecord(record: SourceRecordEntity)

    @Query("SELECT * FROM safety_financial_observations WHERE sourceRecordId = :sourceRecordId")
    fun getObservationsForSource(sourceRecordId: String): Flow<List<FinancialObservationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinancialObservation(observation: FinancialObservationEntity)

    @Query("SELECT * FROM safety_investigative_signals WHERE caseId = :caseId ORDER BY detectedAt DESC")
    fun getSignalsForCase(caseId: String): Flow<List<InvestigativeSignalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: InvestigativeSignalEntity)
}
