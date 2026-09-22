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
}
