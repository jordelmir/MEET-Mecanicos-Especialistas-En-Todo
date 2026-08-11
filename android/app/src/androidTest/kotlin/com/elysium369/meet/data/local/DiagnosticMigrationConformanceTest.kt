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

    private companion object {
        const val DB_NAME = "diagnostic-migration-conformance"
        const val RAW_ID_DB_NAME = "diagnostic-migration-raw-identity"
    }
}
