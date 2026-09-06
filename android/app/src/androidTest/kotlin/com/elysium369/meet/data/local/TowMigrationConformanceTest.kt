package com.elysium369.meet.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elysium369.meet.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TowMigrationConformanceTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeetDatabase::class.java,
    )

    private val dbName = "tow-migration-test.db"

    @Test
    fun migration70To71SchemaMatchesExportedRoomSchemaTest() {
        helper.createDatabase(dbName, 70).close()

        // Validates all columns, types, nullability, indices and index names against 71.json
        helper.runMigrationsAndValidate(
            dbName,
            71,
            true,
            AppModule.MIGRATION_70_71
        ).use { db ->
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        }
    }

    @Test
    fun migration70To71PreservesActiveTowJobsTest() {
        helper.createDatabase(dbName, 70).apply {
            execSQL("""
                INSERT INTO tow_truck_requests (
                    requestId, userId, vehicleInfo, latitude, longitude, locationName,
                    destinationLatitude, destinationLongitude, destinationName, phone,
                    status, assignedDriverId, assignedDriverName, assignedDriverPhone,
                    priceOffer, createdAt, completedAt
                ) VALUES (
                    'tow-req-active-1', 'user-cust-42', 'Toyota Hilux 2020 4x4', 9.9281, -84.0907, 'San Jose Centro',
                    9.9350, -84.1000, 'Taller La Uruca', '+50688887777',
                    'TAKEN', 'op-99', 'Carlos Gruero', '+50689990000',
                    35000.0, 1725500000000, NULL
                )
            """)
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            71,
            true,
            AppModule.MIGRATION_70_71
        ).use { db ->
            db.query("SELECT jobId, customerId, state, customerPhone, pickupAddress, destinationAddress, assignedOperatorName, estimatedPriceMinor, serverVersion FROM tow_jobs WHERE jobId = 'tow-req-active-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("tow-req-active-1", cursor.getString(0))
                assertEquals("user-cust-42", cursor.getString(1))
                assertEquals("ASSIGNED", cursor.getString(2))
                assertEquals("+50688887777", cursor.getString(3))
                assertEquals("San Jose Centro", cursor.getString(4))
                assertEquals("Taller La Uruca", cursor.getString(5))
                assertEquals("Carlos Gruero", cursor.getString(6))
                assertEquals(3500000L, cursor.getLong(7))
                assertEquals(1L, cursor.getLong(8))
            }
        }
    }

    @Test
    fun migration70To71PreservesCompletedTowJobsTest() {
        helper.createDatabase(dbName, 70).apply {
            execSQL("""
                INSERT INTO tow_truck_requests (
                    requestId, userId, vehicleInfo, latitude, longitude, locationName,
                    destinationLatitude, destinationLongitude, destinationName, phone,
                    status, assignedDriverId, assignedDriverName, assignedDriverPhone,
                    priceOffer, createdAt, completedAt
                ) VALUES (
                    'tow-req-completed-1', 'user-cust-99', 'Hyundai Accent 2012', 10.0000, -84.2000, 'Alajuela',
                    10.0100, -84.2100, 'Desamparados Alajuela', '+50670001111',
                    'COMPLETED', 'op-55', 'Maria Gruas', '+50679998888',
                    25000.0, 1725400000000, 1725403600000
                )
            """)
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            71,
            true,
            AppModule.MIGRATION_70_71
        ).use { db ->
            db.query("SELECT jobId, state, finalSettlementMinor, updatedAtEpochMs FROM tow_jobs WHERE jobId = 'tow-req-completed-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("tow-req-completed-1", cursor.getString(0))
                assertEquals("COMPLETED", cursor.getString(1))
                assertEquals(2500000L, cursor.getLong(2))
                assertEquals(1725403600000L, cursor.getLong(3))
            }
        }
    }

    @Test
    fun migration70To71DoesNotInventTowHistoryTest() {
        helper.createDatabase(dbName, 70).apply {
            execSQL("""
                INSERT INTO tow_truck_requests (
                    requestId, userId, vehicleInfo, latitude, longitude, locationName,
                    destinationLatitude, destinationLongitude, destinationName, phone,
                    status, assignedDriverId, assignedDriverName, assignedDriverPhone,
                    priceOffer, createdAt, completedAt
                ) VALUES (
                    'tow-req-open-1', 'user-unknown-cust', 'Nissan Versa 2018', 9.9000, -84.0500, 'Curridabat',
                    NULL, NULL, NULL, '+50660002222',
                    'OPEN', NULL, NULL, NULL,
                    20000.0, 1725400000000, NULL
                )
            """)
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            71,
            true,
            AppModule.MIGRATION_70_71
        ).use { db ->
            db.query("SELECT customerName, assignedOperatorRating, assignedOperatorCompletedJobs, custodyRecordsJson FROM tow_jobs WHERE jobId = 'tow-req-open-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Dato no capturado", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertEquals("[]", cursor.getString(3))
            }
        }
    }
}
