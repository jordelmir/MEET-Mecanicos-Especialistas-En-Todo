package com.elysium.nexus.fabric.profile.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import android.content.Context

// ═══════════════════════════════════════════════════════════════════════════
// Installed Profile + Commands — §2 Authoritative persistent IR profile
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "installed_ir_profiles")
data class InstalledIrProfileEntity(
    @PrimaryKey val profileId: String,
    val displayName: String,
    val brandId: String,
    val deviceTypeId: String,
    val deviceModelId: String?,
    val remoteId: String?,
    val codeSetId: String,
    val catalogVersion: String,
    /** P0.1: Renamed from catalogCanonicalHash to catalogCanonicalHashAtInstall. */
    val catalogCanonicalHashAtInstall: String,
    /** P0.1: Schema version of catalog at profile creation. */
    val catalogSchemaVersionAtInstall: Int,
    /** P0.1: Build ID of catalog at profile creation. */
    val catalogBuildIdAtInstall: String,
    val verificationStatus: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastSuccessfulUseEpochMs: Long,
    val needsRevalidation: Boolean,
    val isEnabled: Boolean
)

@Entity(
    tableName = "installed_ir_commands",
    primaryKeys = ["profileId", "actionKey"],
    foreignKeys = [
        ForeignKey(
            entity = InstalledIrProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("profileId"),
        Index("signalId"),
        Index("codeSetId")
    ]
)
data class InstalledIrCommandEntity(
    val profileId: String,
    val actionKey: String,
    val signalId: String,
    val codeSetId: String,
    val physicalSha256: String,
    val sourceRevisionId: String,
    val verificationStatus: String,
    val successCount: Int,
    val failureCount: Int,
    val lastSuccessEpochMs: Long,
    val lastFailureEpochMs: Long
)

// ═══════════════════════════════════════════════════════════════════════════
// Device Identity — §31.2 / V06-P9 peer identity graph (durable history)
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "device_identities")
data class DeviceIdentityEntity(
    @PrimaryKey val stableId: String,
    val label: String,
    val manufacturer: String?,
    val model: String?,
    val matchType: String,
    val evidenceJson: String,
    val compositeFallback: Boolean,
    val lastSeenEpochMs: Long
)

@Entity(
    tableName = "device_identity_history",
    primaryKeys = ["stableId", "observedAtEpochMs"],
    foreignKeys = [
        ForeignKey(
            entity = DeviceIdentityEntity::class,
            parentColumns = ["stableId"],
            childColumns = ["stableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("stableId")]
)
data class DeviceIdentityHistoryEntity(
    val stableId: String,
    val observedAtEpochMs: Long,
    val source: String,
    val manufacturer: String?,
    val model: String?,
    val ipAddress: String?
)

// ═══════════════════════════════════════════════════════════════════════════
// Probe Session — §23 Tracks a probing session for a brand/device
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "probe_sessions")
data class ProbeSessionEntity(
    @PrimaryKey val sessionId: String,
    val brand: String,
    val deviceType: String,
    val targetModel: String?,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val status: String,
    val winnerCodeSetId: String?,
    // P0.3: Full probe state for process death recovery
    val currentCandidateIndex: Int,
    val currentCandidateId: String?,
    val currentActionKey: String?,
    val lastSignalId: String?,
    val lastPhysicalSha256: String?,
    val lastAttemptId: String?,
    val catalogHashAtStart: String?,
    val verifiedActionKeys: String
)

// ═══════════════════════════════════════════════════════════════════════════
// Probe Attempt — §23 Individual probe attempt with attemptId
// ═══════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "probe_attempts",
    foreignKeys = [
        ForeignKey(
            entity = ProbeSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("codeSetId")
    ]
)
data class ProbeAttemptEntity(
    @PrimaryKey val attemptId: String,
    val sessionId: String,
    val candidateId: String,
    val codeSetId: String,
    val signalId: String,
    val actionKey: String,
    val transmittedAtEpochMs: Long,
    val result: String,
    val transmitDurationMs: Long,
    // V0.6.2 PR3 Phase 13 — full evidence on every transmission:
    val physicalSha256: String?,
    val carrierHz: Int?,
    val catalogBuildId: String?,
    val confirmedAtEpochMs: Long?,
    /** CHALLENGE_CONFIRMED | SECONDARY_CONFIRMED | USER_CONFIRMED — which evidence step confirmed this attempt. */
    val confirmedBy: String?
)

// ═══════════════════════════════════════════════════════════════════════════
// Compatibility Evidence — §23 Community/verified compatibility records
// ═══════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "compatibility_evidence",
    indices = [
        Index("codeSetId"),
        Index("deviceModelId")
    ]
)
data class CompatibilityEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codeSetId: String,
    val deviceModelId: String? = null,
    val brand: String,
    val deviceType: String,
    val actionKey: String,
    val success: Boolean,
    val reportSource: String,
    val reportedAtEpochMs: Long,
    val notes: String?
)

/** P1-11: Projection for batch evidence GROUP BY queries. Not an Entity — room infers mapping. */
data class EvidenceCountRow(
    val codeSetId: String,
    val successCount: Int,
    val failCount: Int
)

// ═══════════════════════════════════════════════════════════════════════════
// Candidate Penalty — §23 Penalizes candidates that repeatedly fail
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "candidate_penalties")
data class CandidatePenaltyEntity(
    @PrimaryKey val codeSetId: String,
    val penaltyScore: Int,
    val failCount: Int,
    val lastFailEpochMs: Long,
    val reason: String
)

// ═══════════════════════════════════════════════════════════════════════════
// Catalog Migration Record — §14 Tracks catalog version transitions
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "catalog_migrations")
data class CatalogMigrationEntity(
    @PrimaryKey val migrationId: String,
    val fromVersion: String,
    val toVersion: String,
    val migratedAtEpochMs: Long,
    val profileCount: Int,
    val status: String
)

// ═══════════════════════════════════════════════════════════════════════════
// Signal Source — §14 Provenance tracking for catalog signals
// Maps each signalId to its originating source(s) with evidence level.
// ═══════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "signal_sources",
    primaryKeys = ["signalId", "sourceId"]
)
data class SignalSourceEntity(
    val signalId: String,
    val sourceId: String,
    val sourceRevisionId: String,
    val evidenceLevel: String,
    /** INTERNAL_UNVERIFIED | INTERNAL_DEVICE_OBSERVED | HIL_CAPTURED | REAL_DEVICE_VERIFIED | PRODUCTION_APPROVED */
    val verificationSource: String?,
    val verifiedAtEpochMs: Long?,
    val deviceModel: String?,
    val notes: String?
)

// ═══════════════════════════════════════════════════════════════════════════
// DAO — Complete CRUD for all entities
// ═══════════════════════════════════════════════════════════════════════════

@Dao
interface InstalledProfileDao {

    // ── Profiles ──────────────────────────────────────────────────────

    @Query("SELECT * FROM installed_ir_profiles WHERE isEnabled = 1 ORDER BY updatedAtEpochMs DESC")
    suspend fun getAllProfiles(): List<InstalledIrProfileEntity>

    @Query("SELECT * FROM installed_ir_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): InstalledIrProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: InstalledIrProfileEntity)

    @Query("DELETE FROM installed_ir_profiles WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    // ── Commands ──────────────────────────────────────────────────────

    @Query("SELECT * FROM installed_ir_commands WHERE profileId = :profileId")
    suspend fun getCommandsForProfile(profileId: String): List<InstalledIrCommandEntity>

    @Query("SELECT * FROM installed_ir_commands WHERE profileId = :profileId AND actionKey = :actionKey LIMIT 1")
    suspend fun getCommandForAction(profileId: String, actionKey: String): InstalledIrCommandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommands(commands: List<InstalledIrCommandEntity>)

    @Query("DELETE FROM installed_ir_commands WHERE profileId = :profileId")
    suspend fun deleteCommands(profileId: String)

    @Transaction
    suspend fun saveProfileWithCommands(profile: InstalledIrProfileEntity, commands: List<InstalledIrCommandEntity>) {
        insertProfile(profile)
        // V06.2 PR3 Phase 10 (P0-19): the authoritative install replaces the
        // exact command set — stale commands from a previous install must never
        // survive. DELETE previous → INSERT exact current, inside the SAME transaction.
        deleteCommands(profile.profileId)
        insertCommands(commands)
    }

    @Transaction
    suspend fun deleteProfileWithCommands(profileId: String) {
        deleteCommands(profileId)
        deleteProfile(profileId)
    }

    // ── Probe Sessions ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProbeSession(session: ProbeSessionEntity)

    @Query("SELECT * FROM probe_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getProbeSession(sessionId: String): ProbeSessionEntity?

    // V0.6.2 PR3 Phase 12 — real process-death recovery: find the latest active
    // (non-completed) session matching the current brand/device. Never restore
    // against a different catalog silently — callers compare catalogHashAtStart.
    @Query("SELECT * FROM probe_sessions WHERE brand = :brand AND deviceType = :deviceType AND status != 'COMPLETED' ORDER BY startedAtEpochMs DESC LIMIT 1")
    suspend fun getLatestActiveProbeSession(brand: String, deviceType: String): ProbeSessionEntity?

    @Query("UPDATE probe_sessions SET status = :status, completedAtEpochMs = :completedAtMs, winnerCodeSetId = :winnerCodeSetId WHERE sessionId = :sessionId")
    suspend fun completeProbeSession(sessionId: String, status: String, completedAtMs: Long, winnerCodeSetId: String?)

    /** P0.3: Update transient probe state for process death recovery. */
    @Query("""
        UPDATE probe_sessions
        SET currentCandidateIndex = :candidateIndex,
            currentCandidateId = :candidateId,
            currentActionKey = :actionKey,
            lastSignalId = :signalId,
            lastPhysicalSha256 = :physicalSha256,
            lastAttemptId = :attemptId,
            verifiedActionKeys = :verifiedActionKeys
        WHERE sessionId = :sessionId
    """)
    suspend fun updateProbeSessionState(
        sessionId: String,
        candidateIndex: Int,
        candidateId: String?,
        actionKey: String?,
        signalId: String?,
        physicalSha256: String?,
        attemptId: String?,
        verifiedActionKeys: String
    )

    // ── Probe Attempts ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProbeAttempt(attempt: ProbeAttemptEntity)

    @Query("SELECT * FROM probe_attempts WHERE sessionId = :sessionId ORDER BY transmittedAtEpochMs DESC")
    suspend fun getAttemptsForSession(sessionId: String): List<ProbeAttemptEntity>

    @Query("SELECT * FROM probe_attempts WHERE sessionId = :sessionId AND actionKey = :actionKey AND result = 'SUCCESS' ORDER BY transmittedAtEpochMs DESC LIMIT 1")
    suspend fun getLastSuccessfulAttempt(sessionId: String, actionKey: String): ProbeAttemptEntity?

    // V0.6.2 PR3 Phase 13 — Attempt evidence lifecycle

    @Query("UPDATE probe_attempts SET result = :result, transmitDurationMs = :durationMs WHERE attemptId = :attemptId")
    suspend fun updateAttemptResult(attemptId: String, result: String, durationMs: Long)

    @Query("UPDATE probe_attempts SET result = :result, confirmedAtEpochMs = :confirmedAtMs, confirmedBy = :confirmedBy WHERE attemptId = :attemptId")
    suspend fun updateAttemptConfirmation(attemptId: String, result: String, confirmedAtMs: Long, confirmedBy: String)

    // ── Compatibility Evidence ─────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: CompatibilityEvidenceEntity)

    @Query("SELECT * FROM compatibility_evidence WHERE codeSetId = :codeSetId AND actionKey = :actionKey AND success = 1")
    suspend fun getSuccessfulEvidence(codeSetId: String, actionKey: String): List<CompatibilityEvidenceEntity>

    /** P1-11: Batch evidence counts — single GROUP BY instead of N+1 per-candidate queries. */
    @Query("""
        SELECT codeSetId,
               SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount,
               SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failCount
        FROM compatibility_evidence
        WHERE actionKey = :actionKey
        GROUP BY codeSetId
    """)
    suspend fun getEvidenceCountsByCodeSet(actionKey: String): List<EvidenceCountRow>

    // ── Candidate Penalties ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPenalty(penalty: CandidatePenaltyEntity)

    @Query("SELECT * FROM candidate_penalties WHERE codeSetId = :codeSetId LIMIT 1")
    suspend fun getPenalty(codeSetId: String): CandidatePenaltyEntity?

    @Query("SELECT * FROM candidate_penalties ORDER BY penaltyScore DESC LIMIT :limit")
    suspend fun getTopPenalties(limit: Int): List<CandidatePenaltyEntity>

    // ── Catalog Migrations ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMigration(migration: CatalogMigrationEntity)

    @Query("SELECT * FROM catalog_migrations ORDER BY migratedAtEpochMs DESC LIMIT 1")
    suspend fun getLastMigration(): CatalogMigrationEntity?

    // ── Signal Sources (Provenance) ────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignalSource(source: SignalSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignalSources(sources: List<SignalSourceEntity>)

    @Query("SELECT * FROM signal_sources WHERE signalId = :signalId")
    suspend fun getSourcesForSignal(signalId: String): List<SignalSourceEntity>

    @Query("SELECT * FROM signal_sources WHERE signalId = :signalId AND evidenceLevel = :level")
    suspend fun getSourcesByLevel(signalId: String, level: String): List<SignalSourceEntity>

    @Query("SELECT DISTINCT signalId FROM signal_sources WHERE evidenceLevel IN (:levels)")
    suspend fun getSignalsWithEvidenceLevels(levels: List<String>): List<String>

    // ── Device Identity Graph (V06-P9) ───────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeviceIdentity(entity: DeviceIdentityEntity)

    @Query("SELECT * FROM device_identities WHERE stableId = :stableId")
    suspend fun getDeviceIdentity(stableId: String): DeviceIdentityEntity?

    @Query("SELECT * FROM device_identities ORDER BY lastSeenEpochMs DESC")
    suspend fun getAllDeviceIdentities(): List<DeviceIdentityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentityHistory(entity: DeviceIdentityHistoryEntity)

    @Query("SELECT * FROM device_identity_history WHERE stableId = :stableId ORDER BY observedAtEpochMs DESC")
    suspend fun getIdentityHistory(stableId: String): List<DeviceIdentityHistoryEntity>
}

// ═══════════════════════════════════════════════════════════════════════════
// Room Database — §2 Complete with all audit entities
// ═══════════════════════════════════════════════════════════════════════════

@Database(
    entities = [
        InstalledIrProfileEntity::class,
        InstalledIrCommandEntity::class,
        ProbeSessionEntity::class,
        ProbeAttemptEntity::class,
        CompatibilityEvidenceEntity::class,
        CandidatePenaltyEntity::class,
        CatalogMigrationEntity::class,
        SignalSourceEntity::class,
        SceneEntity::class,
        DeviceIdentityEntity::class,
        DeviceIdentityHistoryEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class ElysiumUserDatabase : RoomDatabase() {
    abstract fun profileDao(): InstalledProfileDao
    abstract fun sceneDao(): SceneDao

    companion object {
        @Volatile
        private var INSTANCE: ElysiumUserDatabase? = null

        // P0-12: Explicit migrations. No fallbackToDestructiveMigration.
        // v2→v3: empty migration (no schema change, but forces version bump for exportSchema)
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No schema changes — version bump to enable exportSchema tracking.
            }
        }

        /** V06 §4: relational integrity — FKs + indices on v7 schemas.
         *  A compound stable index is expected for all child tables. */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // --- installed_ir_commands: FK → installed_ir_profiles (CASCADE) + indices ---
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS installed_ir_commands_new (
                        profileId TEXT NOT NULL,
                        actionKey TEXT NOT NULL,
                        signalId TEXT NOT NULL,
                        codeSetId TEXT NOT NULL,
                        physicalSha256 TEXT NOT NULL,
                        sourceRevisionId TEXT NOT NULL,
                        verificationStatus TEXT NOT NULL,
                        successCount INTEGER NOT NULL DEFAULT 0,
                        failureCount INTEGER NOT NULL DEFAULT 0,
                        lastSuccessEpochMs INTEGER NOT NULL DEFAULT 0,
                        lastFailureEpochMs INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(profileId, actionKey),
                        FOREIGN KEY(profileId) REFERENCES installed_ir_profiles(profileId) ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    """INSERT INTO installed_ir_commands_new
                       (profileId, actionKey, signalId, codeSetId, physicalSha256, sourceRevisionId,
                        verificationStatus, successCount, failureCount, lastSuccessEpochMs, lastFailureEpochMs)
                       SELECT profileId, actionKey, signalId, codeSetId, physicalSha256, sourceRevisionId,
                              verificationStatus, successCount, failureCount, lastSuccessEpochMs, lastFailureEpochMs
                       FROM installed_ir_commands"""
                )
                db.execSQL("DROP TABLE installed_ir_commands")
                db.execSQL("ALTER TABLE installed_ir_commands_new RENAME TO installed_ir_commands")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_installed_ir_commands_profileId ON installed_ir_commands(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_installed_ir_commands_signalId ON installed_ir_commands(signalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_installed_ir_commands_codeSetId ON installed_ir_commands(codeSetId)")

                // --- probe_attempts: FK → probe_sessions (CASCADE) + indices ---
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS probe_attempts_new (
                        attemptId TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        candidateId TEXT NOT NULL,
                        codeSetId TEXT NOT NULL,
                        signalId TEXT NOT NULL,
                        actionKey TEXT NOT NULL,
                        transmittedAtEpochMs INTEGER NOT NULL,
                        result TEXT NOT NULL,
                        transmitDurationMs INTEGER NOT NULL,
                        PRIMARY KEY(attemptId),
                        FOREIGN KEY(sessionId) REFERENCES probe_sessions(sessionId) ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    """INSERT INTO probe_attempts_new
                       (attemptId, sessionId, candidateId, codeSetId, signalId, actionKey,
                        transmittedAtEpochMs, result, transmitDurationMs)
                       SELECT attemptId, sessionId, candidateId, codeSetId, signalId, actionKey,
                              transmittedAtEpochMs, result, transmitDurationMs
                       FROM probe_attempts"""
                )
                db.execSQL("DROP TABLE probe_attempts")
                db.execSQL("ALTER TABLE probe_attempts_new RENAME TO probe_attempts")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_probe_attempts_sessionId ON probe_attempts(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_probe_attempts_codeSetId ON probe_attempts(codeSetId)")

                // --- compatibility_evidence: add deviceModelId + indices ---
                db.execSQL("ALTER TABLE compatibility_evidence ADD COLUMN deviceModelId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compatibility_evidence_codeSetId ON compatibility_evidence(codeSetId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compatibility_evidence_deviceModelId ON compatibility_evidence(deviceModelId)")
            }
        }

        // P1-SIGNAL-SOURCES: Add signal_sources table for provenance tracking
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS signal_sources (
                        signalId TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        sourceRevisionId TEXT NOT NULL,
                        evidenceLevel TEXT NOT NULL,
                        verificationSource TEXT,
                        verifiedAtEpochMs INTEGER,
                        deviceModel TEXT,
                        notes TEXT,
                        PRIMARY KEY(signalId, sourceId)
                    )"""
                )
            }
        }

        // P0.1: Add catalogSchemaVersionAtInstall, catalogBuildIdAtInstall,
        // rename catalogCanonicalHash → catalogCanonicalHashAtInstall.
        // SQLite doesn't support RENAME COLUMN before API 30, so we recreate the table.
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS installed_ir_profiles_new (
                        profileId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        brandId TEXT NOT NULL,
                        deviceTypeId TEXT NOT NULL,
                        deviceModelId TEXT,
                        remoteId TEXT,
                        codeSetId TEXT NOT NULL,
                        catalogVersion TEXT NOT NULL,
                        catalogCanonicalHashAtInstall TEXT NOT NULL DEFAULT 'unknown',
                        catalogSchemaVersionAtInstall INTEGER NOT NULL DEFAULT 4,
                        catalogBuildIdAtInstall TEXT NOT NULL DEFAULT 'unknown',
                        verificationStatus TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        lastSuccessfulUseEpochMs INTEGER NOT NULL DEFAULT 0,
                        needsRevalidation INTEGER NOT NULL DEFAULT 0,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(profileId)
                    )"""
                )
                db.execSQL(
                    """INSERT INTO installed_ir_profiles_new
                       (profileId, displayName, brandId, deviceTypeId, deviceModelId, remoteId,
                        codeSetId, catalogVersion, catalogCanonicalHashAtInstall,
                        catalogSchemaVersionAtInstall, catalogBuildIdAtInstall,
                        verificationStatus, createdAtEpochMs, updatedAtEpochMs,
                        lastSuccessfulUseEpochMs, needsRevalidation, isEnabled)
                       SELECT profileId, displayName, brandId, deviceTypeId, deviceModelId, remoteId,
                              codeSetId, catalogVersion, catalogCanonicalHash,
                              4, 'unknown',
                              verificationStatus, createdAtEpochMs, updatedAtEpochMs,
                              lastSuccessfulUseEpochMs, needsRevalidation, isEnabled
                       FROM installed_ir_profiles"""
                )
                db.execSQL("DROP TABLE installed_ir_profiles")
                db.execSQL("ALTER TABLE installed_ir_profiles_new RENAME TO installed_ir_profiles")
            }
        }

        // P0.3: Expand probe_sessions with full state fields for process death recovery.
        // Probe sessions are transient — safe to drop and recreate.
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS probe_sessions")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS probe_sessions (
                        sessionId TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        deviceType TEXT NOT NULL,
                        targetModel TEXT,
                        startedAtEpochMs INTEGER NOT NULL,
                        completedAtEpochMs INTEGER,
                        status TEXT NOT NULL,
                        winnerCodeSetId TEXT,
                        currentCandidateIndex INTEGER NOT NULL DEFAULT 0,
                        currentCandidateId TEXT,
                        currentActionKey TEXT,
                        lastSignalId TEXT,
                        lastPhysicalSha256 TEXT,
                        lastAttemptId TEXT,
                        catalogHashAtStart TEXT,
                        verifiedActionKeys TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(sessionId)
                    )"""
                )
            }
        }

        /** V06 §8: durable scenes — scenes table (v8). */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS scenes (
                        sceneId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        tagsCsv TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scenes_updatedAtEpochMs ON scenes(updatedAtEpochMs)")
            }
        }

        /** V06-P9: device identity graph — durable identity + history (v9). */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS device_identities (
                        stableId TEXT NOT NULL PRIMARY KEY,
                        label TEXT NOT NULL,
                        manufacturer TEXT,
                        model TEXT,
                        matchType TEXT NOT NULL,
                        evidenceJson TEXT NOT NULL,
                        compositeFallback INTEGER NOT NULL DEFAULT 0,
                        lastSeenEpochMs INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS device_identity_history (
                        stableId TEXT NOT NULL,
                        observedAtEpochMs INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        manufacturer TEXT,
                        model TEXT,
                        ipAddress TEXT,
                        PRIMARY KEY(stableId, observedAtEpochMs),
                        FOREIGN KEY(stableId) REFERENCES device_identities(stableId) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_device_identity_history_stableId ON device_identity_history(stableId)")
            }
        }

        // V0.6.2 PR3 Phase 13 — Attempt evidence lifecycle (v10):
        // add physicalSha256, carrierHz, catalogBuildId, confirmedAtEpochMs,
        // confirmedBy to probe_attempts.
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE probe_attempts ADD COLUMN physicalSha256 TEXT")
                db.execSQL("ALTER TABLE probe_attempts ADD COLUMN carrierHz INTEGER")
                db.execSQL("ALTER TABLE probe_attempts ADD COLUMN catalogBuildId TEXT")
                db.execSQL("ALTER TABLE probe_attempts ADD COLUMN confirmedAtEpochMs INTEGER")
                db.execSQL("ALTER TABLE probe_attempts ADD COLUMN confirmedBy TEXT")
            }
        }

        fun getInstance(context: Context): ElysiumUserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ElysiumUserDatabase::class.java,
                    "elysium_user_database.db"
                )
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9, MIGRATION_9_10
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
