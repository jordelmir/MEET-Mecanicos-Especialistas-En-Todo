package com.elysium369.meet.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elysium369.meet.di.AppModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyMigrationConformanceTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeetDatabase::class.java,
    )

    @Test
    fun safetyProjectionAndEvidenceMigrationsMatchRoom80() {
        val name = "safety-migration-78-to-80.db"
        helper.createDatabase(name, 78).close()

        helper.runMigrationsAndValidate(
            name,
            80,
            true,
            AppModule.MIGRATION_78_79,
            AppModule.MIGRATION_79_80,
        ).use { db ->
            val expected = setOf(
                "safety_public_points_local",
                "safety_public_cases_local",
                "safety_public_timeline_local",
                "safety_public_claims_local",
                "safety_evidence_local",
            )
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                val actual = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertTrue(actual.containsAll(expected))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    @Test
    fun retiredSafetyCachesArePurgedAndNullableMetricsRemainUnknownInRoom88() {
        val name = "safety-case-migration-87-to-88.db"
        helper.createDatabase(name, 87).use { db ->
            db.execSQL("INSERT INTO `safety_local_payloads` (`payloadId`, `ciphertext`, `sha256`, `createdAt`) VALUES ('retained', X'01', 'retained', 1)")
            db.execSQL("INSERT INTO `safety_reports` (`reportId`, `ownerUserId`, `category`, `payloadId`, `localState`, `serverVersion`, `syncState`, `createdAt`, `updatedAt`) VALUES ('retained', 'retained', 'retained', 'retained', 'retained', 1, 'retained', 1, 1)")
            db.execSQL("INSERT INTO `safety_command_outbox` (`idempotencyKey`, `aggregateId`, `actorSessionUserId`, `commandType`, `expectedVersion`, `payloadVersion`, `payloadId`, `clientPayloadSha256`, `status`, `attemptCount`, `nextAttemptAt`, `createdAt`, `updatedAt`) VALUES ('retained', 'retained', 'retained', 'retained', 1, 1, 'retained', 'retained', 'retained', 1, 1, 1, 1)")
            db.execSQL("INSERT INTO `safety_evidence_local` (`evidenceId`, `reportId`, `ownerUserId`, `encryptedPath`, `contentSha256`, `mimeType`, `byteCount`, `stagedAt`, `uploadState`, `attemptCount`) VALUES ('retained', 'retained', 'retained', 'retained', 'retained', 'retained', 1, 1, 'retained', 1)")
            db.execSQL("INSERT INTO `safety_public_points_local` (`publicPointId`, `category`, `displayLatitude`, `displayLongitude`, `geoDisclosure`, `label`, `claimState`, `independentSourceCount`, `civilSourceCount`, `journalisticSourceCount`, `publicRecordSourceCount`, `documentarySourceCount`, `institutionalSourceCount`, `lastReviewedAt`, `publishedAt`, `serverVersion`, `syncedAt`, `victimCountDocumented`, `victimFemaleCount`, `victimMaleCount`, `victimUnknownSexCount`) VALUES ('retained', 'retained', 1, 1, 'retained', 'retained', 'retained', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)")
            db.execSQL("INSERT INTO `safety_public_timeline_local` (`caseId`, `milestoneId`, `eventType`, `publicSummary`, `recordedAt`, `sourceCount`, `evidenceCount`, `serverVersion`) VALUES ('retained', 'retained', 'retained', 'retained', 1, 1, 1, 1)")
            db.execSQL("INSERT INTO `safety_public_claims_local` (`caseId`, `claimId`, `predicate`, `claimState`, `independentSourceCount`, `evidenceCount`, `civilSourceCount`, `journalisticSourceCount`, `publicRecordSourceCount`, `documentarySourceCount`, `institutionalSourceCount`, `serverVersion`) VALUES ('retained', 'retained', 'retained', 'retained', 1, 1, 1, 1, 1, 1, 1, 1)")
            db.execSQL(
                """
                INSERT INTO safety_public_cases_local
                (caseId, caseType, title, publicSummary, lifecycle,
                 confidenceScore, eventCount, claimCount, sourceCount,
                 evidenceCount, publishedAt, lastUpdatedAt, serverVersion)
                VALUES ('old-case', 'SAFETY_CASE', 'Historical title', '',
                        'UNDER_REVIEW', 0.7, 2, 3, 4, 5, 1, 1, 1)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(
            name, 88, true, AppModule.MIGRATION_87_88,
        ).use { db ->
            listOf("safety_public_cases_local", "safety_public_points_local", "safety_public_claims_local", "safety_public_timeline_local").forEach { table ->
                db.query("SELECT 1 FROM `$table`").use { cursor -> assertFalse(cursor.moveToFirst()) }
            }
            listOf("safety_local_payloads", "safety_reports", "safety_command_outbox", "safety_evidence_local").forEach { table ->
                db.query("SELECT 1 FROM `$table`").use { cursor -> assertTrue(cursor.moveToFirst()) }
            }
            db.query(
                "SELECT confidenceScore, eventCount FROM safety_public_cases_local WHERE caseId = 'old-case'",
            ).use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
            db.execSQL(
                """
                INSERT INTO safety_public_cases_local
                (caseId, caseType, title, publicSummary, lifecycle,
                 confidenceScore, eventCount, claimCount, sourceCount,
                 evidenceCount, publishedAt, lastUpdatedAt, serverVersion)
                VALUES ('v3-case', 'SAFETY_CASE', 'Caso con revisión independiente',
                        '', 'UNDER_REVIEW', NULL, NULL, NULL, NULL, NULL, 2, 2, 1)
                """.trimIndent(),
            )
            db.query(
                "SELECT confidenceScore, eventCount FROM safety_public_cases_local WHERE caseId = 'v3-case'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }
}
