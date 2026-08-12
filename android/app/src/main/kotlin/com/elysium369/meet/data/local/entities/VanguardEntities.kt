package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Elysium Vanguard Telemetry Intelligence + Commerce Trust Core
 *
 * 19 entity classes referenced by MeetDatabase.kt (v36) and FeatureDaos.kt.
 * Generated to satisfy build (kaptDebugKotlin was failing due to missing symbols).
 *
 * Conventions follow FeatureEntities.kt:
 *   - @Entity(tableName = "snake_case")
 *   - @PrimaryKey on id field
 *   - @ColumnInfo only when name differs from property
 *   - @Serializable for sync-friendly types
 *   - Indices on FK-like string fields
 *
 * Schema decisions:
 *   - IDs are String UUIDs (matching existing convention, e.g. LiveSessionEntity.sessionId)
 *   - Timestamps are Long (epoch millis)
 *   - Enums stored as String (Room-safe, no TypeConverter needed)
 *   - JSON payloads stored as String (caller responsible for serialization)
 *   - Money stored as Long cents (no float drift)
 */

// ═══════════════════════════════════════════════════════════════
// ELYSIUM VANGUARD TELEMETRY INTELLIGENCE (16 entities)
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "vanguard_obd_sessions")
@Serializable
data class VanguardObdSessionEntity(
    @PrimaryKey val sessionId: String,
    val vehicleId: String,
    val adapterId: String?,
    val protocol: String,             // ISO9141, KWP2000, CAN_11BIT, CAN_29BIT, J1850
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String,               // ACTIVE, COMPLETED, FAILED, ABORTED
    val totalPidsRead: Int = 0,
    val errorCount: Int = 0,
    val lastError: String? = null
)

@Entity(tableName = "obd_pid_samples", indices = [Index("sessionId"), Index("capturedAt")])
@Serializable
data class ObdPidSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,
    val pid: String,                  // e.g. "0x0C" (RPM), "0x0D" (SPEED)
    val value: Double,
    val unit: String,
    val capturedAt: Long
)

@Entity(tableName = "obd_command_log", indices = [Index("sessionId"), Index("sentAt")])
@Serializable
data class ObdCommandLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,
    val command: String,              // e.g. "010C"
    val response: String,
    val latencyMs: Long,
    val success: Boolean,
    val sentAt: Long
)

@Entity(tableName = "ecu_failure_events", indices = [Index("vehicleId"), Index("detectedAt")])
@Serializable
data class EcuFailureEventEntity(
    @PrimaryKey val eventId: String,
    val vehicleId: String,
    val dtcCode: String,
    val source: String,               // SCAN | TELEMETRY | AI | MANUAL
    val severity: String,             // LOW | MEDIUM | HIGH | CRITICAL
    val description: String,
    val detectedAt: Long,
    val resolvedAt: Long? = null
)

@Entity(tableName = "compatibility_rules", indices = [Index("vehicleMake"), Index("vehicleModel")])
@Serializable
data class CompatibilityRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleMake: String,
    val vehicleModel: String,
    val vehicleYear: Int? = null,
    val featureId: String,            // gauge id, repair tool id, etc.
    val compatible: Boolean,
    val notes: String? = null
)

@Entity(tableName = "vehicle_profile_snapshots", indices = [Index("vehicleId"), Index("capturedAt")])
@Serializable
data class VehicleProfileSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleId: String,
    val capturedAt: Long,
    val odometerKm: Double?,
    val batteryVoltage: Double?,
    val coolantTempC: Double?,
    val oilLifePercent: Double?,
    val payloadJson: String           // additional flexible telemetry
)

@Entity(tableName = "mode06_results", indices = [Index("sessionId")])
@Serializable
data class Mode06ResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,
    val testId: String,               // e.g. "01" (Misfire), "02" (Fuel system)
    val componentId: String,
    val value: Double,
    val minValue: Double,
    val maxValue: Double,
    val status: String,               // PASS | FAIL | INCOMPLETE
    val capturedAt: Long
)

@Entity(tableName = "freeze_frames", indices = [Index("dtcCode"), Index("vehicleId")])
@Serializable
data class FreezeFrameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleId: String,
    val dtcCode: String,
    val capturedAt: Long,
    val payloadJson: String           // serialized snapshot of PIDs at moment of fault
)

@Entity(tableName = "derived_metrics", indices = [Index("vehicleId"), Index("computedAt")])
@Serializable
data class DerivedMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleId: String,
    val metricName: String,           // e.g. "instant_fuel_consumption"
    val value: Double,
    val unit: String,
    val computedAt: Long,
    val origin: String = "DERIVED",
    val confidence: Double = 0.0,
    val inputPidsJson: String = "[]",
    val formulaVersion: String = "UNVERSIONED",
    val inputQuality: Double = 0.0,
    val formulaAuthority: String = "UNREVIEWED_FORMULA",
    val derivationCompleteness: Double = 0.0,
    val measurementUncertainty: Double? = null,
)

@Entity(tableName = "health_scores", indices = [Index("vehicleId"), Index("computedAt")])
@Serializable
data class HealthScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleId: String,
    val overallScore: Int,            // 0..100
    val engineScore: Int? = null,
    val batteryScore: Int? = null,
    val brakesScore: Int? = null,
    val tiresScore: Int? = null,
    val payloadJson: String? = null,
    val computedAt: Long
)

@Entity(tableName = "repair_recommendations", indices = [Index("vehicleId"), Index("dtcCode")])
@Serializable
data class RepairRecommendationEntity(
    @PrimaryKey val recommendationId: String,
    val vehicleId: String,
    val dtcCode: String?,
    val priority: String,             // LOW | MEDIUM | HIGH | URGENT
    val title: String,
    val description: String,
    val estimatedCostCents: Long? = null,
    val estimatedTimeMinutes: Int? = null,
    val createdAt: Long
)

@Entity(tableName = "ai_diagnostic_results", indices = [Index("vehicleId"), Index("generatedAt")])
@Serializable
data class AiDiagnosticResultEntity(
    @PrimaryKey val resultId: String,
    val vehicleId: String,
    val inputContextJson: String,     // symptoms, dtcs, telemetry snapshot
    val outputDiagnosisJson: String,   // ai-generated diagnosis
    val modelVersion: String,
    val confidence: Double,           // 0..1
    val generatedAt: Long
)

@Entity(tableName = "vehicle_history", indices = [Index("vehicleId"), Index("eventAt")])
@Serializable
data class VehicleHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleId: String,
    val eventType: String,            // DTC | REPAIR | MAINTENANCE | TELEMETRY_ALERT
    val eventAt: Long,
    val summary: String,
    val payloadJson: String? = null
)

@Entity(tableName = "pdf_reports", indices = [Index("vehicleId"), Index("generatedAt")])
@Serializable
data class PdfReportEntity(
    @PrimaryKey val reportId: String,
    val vehicleId: String,
    val reportType: String,           // DIAGNOSTIC | INSPECTION | PRE_PURCHASE | INSURANCE
    val title: String,
    val filePath: String,
    val generatedAt: Long,
    val signatureHash: String? = null
)

@Entity(tableName = "audit_logs", indices = [Index("actorId"), Index("occurredAt")])
@Serializable
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val actorId: String?,
    val actorRole: String?,           // USER | MECHANIC | ADMIN | SYSTEM
    val action: String,               // CREATE | UPDATE | DELETE | VIEW | EXPORT
    val resourceType: String,
    val resourceId: String,
    val payloadJson: String? = null,
    val occurredAt: Long
)

@Entity(tableName = "fix_rollouts", indices = [Index("fixId"), Index("rolledOutAt")])
@Serializable
data class FixRolloutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fixId: String,                // unique fix identifier (e.g. CVE id, bug id)
    val rolloutType: String,          // SECURITY | BUGFIX | FEATURE_FLAG
    val affectedVersions: String,     // semver range
    val targetVersion: String,
    val status: String,               // PLANNED | IN_PROGRESS | COMPLETED | ROLLED_BACK
    val rolledOutAt: Long? = null
)

// ═══════════════════════════════════════════════════════════════
// ELYSIUM VANGUARD COMMERCE TRUST CORE (3 entities)
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "vanguard_events", indices = [Index("aggregateId"), Index("occurredAt"), Index(value = ["idempotencyKey"], unique = true)])
@Serializable
data class VanguardEventEntity(
    @PrimaryKey val eventId: String,
    val aggregateType: String,        // REPAIR | BID | ESCROW | MARKETPLACE
    val aggregateId: String,
    val eventType: String,
    val actorId: String?,
    val actorRole: String?,
    val source: String,               // LOCAL_ROOM | SUPABASE | WEBHOOK
    val correlationId: String? = null,
    val causationId: String? = null,
    val idempotencyKey: String,
    val payloadJson: String,
    val schemaVersion: Int = 1,
    val occurredAt: Long,
    val synced: Boolean = false
)

@Entity(tableName = "marketplace_ledger_entries", indices = [Index("transactionId"), Index("orderId"), Index(value = ["idempotencyKey"], unique = true)])
@Serializable
data class MarketplaceLedgerEntryEntity(
    @PrimaryKey val ledgerEntryId: String,
    val transactionId: String,
    val relatedEventId: String?,
    val orderType: String,            // SERVICE_REPAIR | GAUGE_PURCHASE | SUBSCRIPTION
    val orderId: String,
    val participantId: String?,
    val participantRole: String,      // ESCROW | PLATFORM | MECHANIC | CUSTOMER
    val entryType: String,            // GROSS_CAPTURE | PLATFORM_COMMISSION | PAYOUT | REFUND
    val direction: String,            // CREDIT | DEBIT
    val amountCents: Long,
    val currency: String = "USD",
    val status: String,               // PENDING | POSTED | SETTLED | REVERSED
    val metadataJson: String? = null,
    val createdAt: Long,
    val settledAt: Long? = null,
    val idempotencyKey: String,
    val synced: Boolean = false
)

@Entity(tableName = "vanguard_outbox", indices = [Index("eventId"), Index("status"), Index(value = ["idempotencyKey"], unique = true)])
@Serializable
data class VanguardOutboxEntity(
    @PrimaryKey val outboxId: String,
    val eventId: String,
    val destination: String,          // SUPABASE_EVENTS | STRIPE_WEBHOOK | METRICS_PIPELINE
    val operation: String,            // UPSERT_VANGUARD_EVENT | POST_LEDGER | POST_METRIC
    val payloadJson: String,
    val status: String,               // PENDING | IN_FLIGHT | DELIVERED | FAILED | DEAD_LETTER
    val attemptCount: Int = 0,
    val nextAttemptAt: Long,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val idempotencyKey: String
)
