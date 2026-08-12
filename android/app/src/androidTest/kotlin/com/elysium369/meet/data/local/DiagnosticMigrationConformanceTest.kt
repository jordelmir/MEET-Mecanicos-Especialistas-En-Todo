package com.elysium369.meet.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elysium369.meet.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.migration.Migration

@RunWith(AndroidJUnit4::class)
class DiagnosticMigrationConformanceTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeetDatabase::class.java,
    )

    @Test
    fun duplicateLegacyEventsBecomeOneFindingAndTwoObservations() {
        helper.createDatabase(DB_NAME, 50).apply {
            insertLegacyEvent("event-a", "session-a", 100L, 200L)
            insertLegacyEvent("event-b", "session-b", 300L, 400L)
            close()
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            52,
            true,
            AppModule.MIGRATION_50_51,
            AppModule.MIGRATION_51_52,
        ).use { db ->
            db.query("SELECT COUNT(*) FROM diagnostic_findings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM diagnostic_observations WHERE observationState='LEGACY_IMPORTED'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    @Test
    fun sameDisplayCodeWithDifferentUdsRawIdentityAndEcuNeverMerges() {
        helper.createDatabase(RAW_ID_DB_NAME, 50).apply {
            insertLegacyEvent("seed", "session-seed", 100L, 100L)
            close()
        }
        helper.runMigrationsAndValidate(
            RAW_ID_DB_NAME,
            51,
            true,
            AppModule.MIGRATION_50_51,
        ).apply {
            execSQL("DELETE FROM diagnostic_observations")
            execSQL("DELETE FROM diagnostic_findings")
            execSQL(
                "INSERT INTO diagnostic_findings(id,vehicleId,ecuEndpointId,diagnosticNamespace,rawDtcIdentity,displayCode,createdAtMs,resolutionState,resolvedAtMs) VALUES(?,?,?,?,?,?,?,?,NULL)",
                arrayOf("uds-a", "vehicle-1", "ECM", "UDS", "123456:11", "P1234", 100L, "OPEN"),
            )
            execSQL(
                "INSERT INTO diagnostic_findings(id,vehicleId,ecuEndpointId,diagnosticNamespace,rawDtcIdentity,displayCode,createdAtMs,resolutionState,resolvedAtMs) VALUES(?,?,?,?,?,?,?,?,NULL)",
                arrayOf("uds-b", "vehicle-1", "TCM", "UDS", "123456:22", "P1234", 200L, "OPEN"),
            )
            close()
        }
        helper.runMigrationsAndValidate(
            RAW_ID_DB_NAME,
            52,
            true,
            AppModule.MIGRATION_51_52,
        ).use { db ->
            db.query("SELECT COUNT(*) FROM diagnostic_findings WHERE displayCode='P1234'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.query("SELECT COUNT(DISTINCT ecuEndpointId || ':' || rawDtcIdentity) FROM diagnostic_findings WHERE displayCode='P1234'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    @Test
    fun everySupportedDiagnosticSchemaMigratesToCurrentWithoutForeignKeyDamage() {
        listOf(49, 50, 52, 53, 54, 55).forEach { startVersion ->
            val databaseName = "diagnostic-migration-$startVersion-to-56"
            helper.createDatabase(databaseName, startVersion).close()
            helper.runMigrationsAndValidate(
                databaseName,
                56,
                true,
                *migrationsFrom(startVersion),
            ).use { db ->
                db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
            }
        }
    }

    @Test
    fun staged50To56PreservesDistinctRawIdentityAndFindingCausalityColumns() {
        val databaseName = "diagnostic-migration-staged-50-to-56"
        helper.createDatabase(databaseName, 50).apply {
            insertLegacyEvent("event-a", "session-a", 100L, 200L)
            insertLegacyEvent("event-b", "session-b", 300L, 400L)
            close()
        }
        helper.runMigrationsAndValidate(
            databaseName,
            56,
            true,
            *migrationsFrom(50),
        ).use { db ->
            db.query("SELECT COUNT(*) FROM diagnostic_findings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM diagnostic_observations WHERE findingSequence = 0").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    @Test
    fun sameUdsRawIdentityWithDifferentFailureTypeRemainsTwoFindings() {
        val databaseName = "diagnostic-migration-uds-failure-types-to-56"
        helper.createDatabase(databaseName, 51).apply {
            insertRawUdsEvent("uds-ft-11", 0x11, 100L)
            insertRawUdsEvent("uds-ft-22", 0x22, 200L)
            close()
        }
        helper.runMigrationsAndValidate(
            databaseName,
            56,
            true,
            *migrationsFrom(51),
        ).use { db ->
            db.query(
                "SELECT COUNT(*) FROM diagnostic_findings WHERE vehicleId='vehicle-1' AND rawDtcIdentity='123456'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.query(
                "SELECT COUNT(DISTINCT failureType) FROM diagnostic_findings WHERE vehicleId='vehicle-1' AND rawDtcIdentity='123456'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    private fun migrationsFrom(startVersion: Int): Array<Migration> = buildList {
        if (startVersion <= 49) add(AppModule.MIGRATION_49_50)
        if (startVersion <= 50) add(AppModule.MIGRATION_50_51)
        if (startVersion <= 51) add(AppModule.MIGRATION_51_52)
        if (startVersion <= 52) add(AppModule.MIGRATION_52_53)
        if (startVersion <= 53) add(AppModule.MIGRATION_53_54)
        if (startVersion <= 54) add(AppModule.MIGRATION_54_55)
        if (startVersion <= 55) add(AppModule.MIGRATION_55_56)
    }.toTypedArray()

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertLegacyEvent(
        id: String,
        sessionId: String,
        firstSeenAt: Long,
        lastSeenAt: Long,
    ) {
        execSQL(
            """INSERT INTO dtc_events(
                id,sessionId,vehicleId,code,description,severity,status,firstSeenAt,lastSeenAt,
                resolvedAt,occurrenceCount,freezeFrameJson,observationState,diagnosticNamespace,
                moduleIdentity,moduleName,targetAddress,responseAddress,sourceService,statusByte,
                observationSemantic,synced
            ) VALUES(?,?,?,?,?,?,?,?,?,NULL,1,NULL,'OBSERVED','SAE_OBD','ECM','ECM','7E0','7E8','03',NULL,'ACTIVE',0)""",
            arrayOf(id, sessionId, "vehicle-1", "P0230", "", "UNKNOWN", "ACTIVE", firstSeenAt, lastSeenAt),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertRawUdsEvent(
        id: String,
        failureType: Int,
        observedAt: Long,
    ) {
        execSQL(
            """INSERT INTO dtc_events(
                id,sessionId,vehicleId,code,description,severity,status,firstSeenAt,lastSeenAt,
                resolvedAt,occurrenceCount,freezeFrameJson,observationState,diagnosticNamespace,
                moduleIdentity,moduleName,targetAddress,responseAddress,sourceService,statusByte,
                observationSemantic,rawDtcIdentity,rawDtc24,failureType,dtcFormat,synced
            ) VALUES(?,?,?,?,?,?,?,?,?,NULL,1,NULL,'OBSERVED','UDS','ECM','Engine ECU','7E0','7E8','19',NULL,'ACTIVE','123456',1193046,?,'UDS_3_BYTE',0)""",
            arrayOf(id, "session-$id", "vehicle-1", "P1234", "", "UNKNOWN", "ACTIVE", observedAt, observedAt, failureType),
        )
    }

    private companion object {
        const val DB_NAME = "diagnostic-migration-conformance"
        const val RAW_ID_DB_NAME = "diagnostic-migration-raw-identity"
    }
}
