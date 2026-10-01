package com.elysium369.meet.core.operations.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E A O S   R O O M   P E R S I S T E N C E   L A Y E R
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ASCENSION MAXIMA §23, §24, §25 — EAOS Durability.
 *
 *  Operation cases and correlated incidents survive app restarts.
 *  The owner can review historical cases, not just in-session ones.
 * ══════════════════════════════════════════════════════════════════════
 */

@Entity(
    tableName = "eaos_operation_cases",
    indices = [Index(value = ["state"], name = "index_eaos_operation_cases_state")],
)
data class OperationCaseEntity(
    @PrimaryKey
    @ColumnInfo(name = "case_id")
    val caseId: String,

    @ColumnInfo(name = "correlation_id")
    val correlationId: String,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "severity")
    val severity: String,

    @ColumnInfo(name = "state")
    val state: String,

    @ColumnInfo(name = "reconciliation_state")
    val reconciliationState: String,

    @ColumnInfo(name = "remediation_outcome")
    val remediationOutcome: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "what_happened")
    val whatHappened: String,

    @ColumnInfo(name = "what_automation_did")
    val whatAutomationDid: String,

    @ColumnInfo(name = "evidence_summary")
    val evidenceSummary: String,

    @ColumnInfo(name = "what_remains_uncertain")
    val whatRemainsUncertain: String,

    @ColumnInfo(name = "requested_owner_action")
    val requestedOwnerAction: String,

    @ColumnInfo(name = "consequence_of_inaction")
    val consequenceOfInaction: String,

    @ColumnInfo(name = "observed_metric_json")
    val observedMetricJson: String? = null,

    @ColumnInfo(name = "evidence_snapshot_json", defaultValue = "'{}'")
    val evidenceSnapshotJson: String = "{}",

    @ColumnInfo(name = "money_exposure_minor")
    val moneyExposureMinor: Long? = null,

    @ColumnInfo(name = "money_exposure_currency")
    val moneyExposureCurrency: String? = null,

    @ColumnInfo(name = "event_count", defaultValue = "1")
    val eventCount: Int = 1,

    @ColumnInfo(name = "occurred_at_epoch_ms")
    val occurredAtEpochMs: Long,

    @ColumnInfo(name = "resolved_at_epoch_ms")
    val resolvedAtEpochMs: Long? = null,

    @ColumnInfo(name = "resolution_reason")
    val resolutionReason: String? = null,
)

@Entity(
    tableName = "eaos_correlated_incidents",
    indices = [Index(value = ["correlation_key"], name = "index_eaos_correlated_incidents_key")],
)
data class CorrelatedIncidentEntity(
    @PrimaryKey
    @ColumnInfo(name = "incident_id")
    val incidentId: String,

    @ColumnInfo(name = "correlation_key")
    val correlationKey: String,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "severity")
    val severity: String,

    @ColumnInfo(name = "event_count")
    val eventCount: Int,

    @ColumnInfo(name = "first_seen_epoch_ms")
    val firstSeenEpochMs: Long,

    @ColumnInfo(name = "last_seen_epoch_ms")
    val lastSeenEpochMs: Long,

    @ColumnInfo(name = "is_auto_remediated", defaultValue = "0")
    val isAutoRemediated: Boolean,

    @ColumnInfo(name = "active_case_id")
    val activeCaseId: String? = null,
)

@Dao
interface OperationCaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operationCase: OperationCaseEntity)

    @Query("SELECT * FROM eaos_operation_cases WHERE case_id = :caseId")
    suspend fun getById(caseId: String): OperationCaseEntity?

    @Query("SELECT * FROM eaos_operation_cases WHERE state IN ('REQUIRES_OWNER', 'OPEN') OR (severity = 'P0' AND state NOT IN ('AUTO_RESOLVED', 'CLOSED', 'OWNER_APPROVED', 'OWNER_REJECTED'))")
    suspend fun listRequiringOwner(): List<OperationCaseEntity>

    @Query("SELECT * FROM eaos_operation_cases ORDER BY occurred_at_epoch_ms DESC")
    fun observeAll(): Flow<List<OperationCaseEntity>>

    @Query("SELECT COUNT(*) FROM eaos_operation_cases")
    suspend fun count(): Int

    @Update
    suspend fun update(operationCase: OperationCaseEntity)
}

@Dao
interface CorrelatedIncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(incident: CorrelatedIncidentEntity)

    @Query("SELECT * FROM eaos_correlated_incidents WHERE correlation_key = :key")
    suspend fun getByCorrelationKey(key: String): CorrelatedIncidentEntity?

    @Query("SELECT COUNT(*) FROM eaos_correlated_incidents")
    suspend fun count(): Int
}
