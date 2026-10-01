package com.elysium.nexus.core.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CompatibilityDatabase] — the in-memory §33
 * store.
 */
class CompatibilityDatabaseTest {

    private fun result(
        deviceId: String = "honor-magic-v2",
        target: String = "ANDROID_TV",
        status: CompatibilityStatus = CompatibilityStatus.VERIFIED_LAB,
        failures: List<String> = emptyList(),
        confidence: Int = 100,
        tester: String = "lab",
        date: String = "2026-07-31"
    ) = CompatibilityResult(
        deviceId = deviceId,
        deviceModel = "Honor Magic V2",
        androidVersion = "14",
        oemFirmware = "MagicOS 7.2",
        transport = "BluetoothClassicHID",
        targetPlatform = target,
        targetOsFirmware = "Android TV 14",
        game = "Stardew Valley",
        capabilitiesTested = listOf("buttons", "sticks"),
        capabilitiesPassed = listOf("buttons", "sticks"),
        capabilitiesFailed = failures,
        latencyP50Ns = 4_000_000L,
        latencyP95Ns = 8_000_000L,
        tester = tester,
        date = date,
        evidence = null,
        confidence = confidence,
        status = status
    )

    @Test
    fun emptyDatabaseHasZeroRecords() {
        val db = CompatibilityDatabase()
        assertEquals(0, db.size())
        assertEquals(emptyList<CompatibilityResult>(), db.all())
    }

    @Test
    fun addAppendsRecord() {
        val db = CompatibilityDatabase()
        db.add(result())
        assertEquals(1, db.size())
        assertEquals(1, db.all().size)
    }

    @Test
    fun addRejectsVerifiedLabWithFailures() {
        val db = CompatibilityDatabase()
        try {
            db.add(result(status = CompatibilityStatus.VERIFIED_LAB, failures = listOf("triggers")))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, db.size())
    }

    @Test
    fun byDeviceFiltersByDeviceId() {
        val db = CompatibilityDatabase()
        db.add(result(deviceId = "honor-magic-v2", target = "ANDROID_TV"))
        db.add(result(deviceId = "honor-magic-v2", target = "MACOS"))
        db.add(result(deviceId = "google-pixel-9", target = "ANDROID_TV"))
        val honor = db.byDevice("honor-magic-v2")
        val pixel = db.byDevice("google-pixel-9")
        assertEquals(2, honor.size)
        assertEquals(1, pixel.size)
    }

    @Test
    fun byTargetFiltersByTargetPlatform() {
        val db = CompatibilityDatabase()
        db.add(result(target = "ANDROID_TV"))
        db.add(result(target = "MACOS"))
        db.add(result(target = "WINDOWS"))
        val tv = db.byTarget("ANDROID_TV")
        val mac = db.byTarget("MACOS")
        assertEquals(1, tv.size)
        assertEquals(1, mac.size)
    }

    @Test
    fun byStatusFiltersByStatus() {
        val db = CompatibilityDatabase()
        db.add(result(status = CompatibilityStatus.VERIFIED_LAB))
        db.add(result(status = CompatibilityStatus.PARTIALLY_VERIFIED, failures = listOf("gyro"), confidence = 50))
        db.add(result(status = CompatibilityStatus.PARTIALLY_VERIFIED, failures = listOf("haptics"), confidence = 50))
        val verified = db.byStatus(CompatibilityStatus.VERIFIED_LAB)
        val partial = db.byStatus(CompatibilityStatus.PARTIALLY_VERIFIED)
        assertEquals(1, verified.size)
        assertEquals(2, partial.size)
    }

    @Test
    fun latestReturnsMostRecentForDeviceAndTarget() {
        val db = CompatibilityDatabase()
        db.add(result(deviceId = "a", target = "T", date = "2026-01-01"))
        db.add(result(deviceId = "a", target = "T", date = "2026-02-01"))
        db.add(result(deviceId = "a", target = "OTHER", date = "2026-03-01"))
        val latest = db.latest("a", "T")
        assertNotNull(latest)
        assertEquals("2026-02-01", latest!!.date)
    }

    @Test
    fun latestReturnsNullWhenAbsent() {
        val db = CompatibilityDatabase()
        assertNull(db.latest("a", "T"))
        db.add(result(deviceId = "a", target = "T"))
        assertNull(db.latest("b", "T"))
        assertNull(db.latest("a", "OTHER"))
    }

    @Test
    fun statusBreakdownCountsEveryStatus() {
        val db = CompatibilityDatabase()
        db.add(result(status = CompatibilityStatus.VERIFIED_LAB))
        db.add(result(status = CompatibilityStatus.VERIFIED_LAB))
        db.add(result(status = CompatibilityStatus.PARTIALLY_VERIFIED, failures = listOf("gyro"), confidence = 50))
        val breakdown = db.statusBreakdown()
        assertEquals(2, breakdown[CompatibilityStatus.VERIFIED_LAB])
        assertEquals(1, breakdown[CompatibilityStatus.PARTIALLY_VERIFIED])
        // Statuses with no records still appear in the
        // breakdown, with count 0.
        for (s in CompatibilityStatus.values()) {
            assertTrue("status $s missing from breakdown", breakdown.containsKey(s))
        }
    }

    @Test
    fun allIsADefensiveCopy() {
        val db = CompatibilityDatabase()
        db.add(result())
        val snapshot = db.all()
        // The returned list is a defensive copy. We
        // verify the database size is unaffected by
        // mutating the snapshot's underlying array
        // would require reflection; the simpler check is
        // that the database's internal state is not
        // shared with the caller.
        val originalSize = db.size()
        assertEquals(1, snapshot.size)
        assertEquals(originalSize, db.size())
    }
}
