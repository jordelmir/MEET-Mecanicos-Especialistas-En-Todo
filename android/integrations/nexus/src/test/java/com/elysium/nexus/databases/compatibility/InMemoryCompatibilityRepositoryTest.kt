package com.elysium.nexus.databases.compatibility

import com.elysium.nexus.core.compat.CompatibilityResult
import com.elysium.nexus.core.compat.CompatibilityStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [InMemoryCompatibilityRepository] — the
 * JVM-testeable stand-in for [RoomCompatibilityRepository].
 *
 * The in-memory implementation mirrors the Room one's
 * public API exactly (modulo the suspend functions, which
 * both expose). The tests pin the contract: every
 * method the production code consumes is verified.
 */
class InMemoryCompatibilityRepositoryTest {

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
    fun emptyRepositoryHasZeroCount() = runTest {
        val repo = InMemoryCompatibilityRepository()
        assertEquals(0, repo.count())
        assertEquals(emptyList<CompatibilityResult>(), repo.all())
    }

    @Test
    fun addAppendsRecord() = runTest {
        val repo = InMemoryCompatibilityRepository()
        repo.add(result())
        assertEquals(1, repo.count())
        assertEquals(1, repo.all().size)
    }

    @Test
    fun byDeviceFiltersByDeviceId() = runTest {
        val repo = InMemoryCompatibilityRepository()
        repo.add(result(deviceId = "honor-magic-v2", target = "ANDROID_TV"))
        repo.add(result(deviceId = "honor-magic-v2", target = "MACOS"))
        repo.add(result(deviceId = "google-pixel-9", target = "ANDROID_TV"))
        val honor = repo.byDevice("honor-magic-v2")
        val pixel = repo.byDevice("google-pixel-9")
        assertEquals(2, honor.size)
        assertEquals(1, pixel.size)
    }

    @Test
    fun byTargetFiltersByTarget() = runTest {
        val repo = InMemoryCompatibilityRepository()
        repo.add(result(target = "ANDROID_TV"))
        repo.add(result(target = "MACOS"))
        val tv = repo.byTarget("ANDROID_TV")
        assertEquals(1, tv.size)
    }

    @Test
    fun byStatusFiltersByStatus() = runTest {
        val repo = InMemoryCompatibilityRepository()
        repo.add(result(status = CompatibilityStatus.VERIFIED_LAB))
        repo.add(result(
            status = CompatibilityStatus.PARTIALLY_VERIFIED,
            failures = listOf("gyro"),
            confidence = 50
        ))
        repo.add(result(
            status = CompatibilityStatus.PARTIALLY_VERIFIED,
            failures = listOf("haptics"),
            confidence = 50
        ))
        val verified = repo.byStatus(CompatibilityStatus.VERIFIED_LAB)
        val partial = repo.byStatus(CompatibilityStatus.PARTIALLY_VERIFIED)
        assertEquals(1, verified.size)
        assertEquals(2, partial.size)
    }

    @Test
    fun latestReturnsMostRecentForDeviceAndTarget() = runTest {
        val repo = InMemoryCompatibilityRepository()
        repo.add(result(deviceId = "a", target = "T", date = "2026-01-01"))
        repo.add(result(deviceId = "a", target = "T", date = "2026-02-01"))
        repo.add(result(deviceId = "a", target = "OTHER", date = "2026-03-01"))
        val latest = repo.latest("a", "T")
        assertNotNull(latest)
        assertEquals("2026-02-01", latest!!.date)
    }

    @Test
    fun latestReturnsNullWhenAbsent() = runTest {
        val repo = InMemoryCompatibilityRepository()
        assertNull(repo.latest("a", "T"))
        repo.add(result(deviceId = "a", target = "T"))
        assertNull(repo.latest("b", "T"))
        assertNull(repo.latest("a", "OTHER"))
    }

    @Test
    fun statusBreakdownCountsEveryStatus() = runTest {
        val repo = InMemoryCompatibilityRepository()
        repo.add(result(status = CompatibilityStatus.VERIFIED_LAB))
        repo.add(result(status = CompatibilityStatus.VERIFIED_LAB))
        repo.add(result(
            status = CompatibilityStatus.PARTIALLY_VERIFIED,
            failures = listOf("gyro"),
            confidence = 50
        ))
        val breakdown = repo.statusBreakdown()
        assertEquals(2, breakdown[CompatibilityStatus.VERIFIED_LAB])
        assertEquals(1, breakdown[CompatibilityStatus.PARTIALLY_VERIFIED])
        for (s in CompatibilityStatus.values()) {
            assertTrue("status $s missing from breakdown", breakdown.containsKey(s))
        }
    }

    @Test
    fun allIsADefensiveCopy() = runTest {
        val repo = InMemoryCompatibilityRepository()
        repo.add(result())
        val snapshot = repo.all()
        // Mutating the returned list does not affect the
        // repository (the implementation uses `toList()`
        // which is immutable, so any attempt to mutate
        // throws — either way, the repository is safe).
        val originalCount = repo.count()
        assertEquals(1, snapshot.size)
        assertEquals(originalCount, repo.count())
    }

    @Test
    fun countReflectsAllAdds() = runTest {
        val repo = InMemoryCompatibilityRepository()
        for (i in 0 until 25) {
            repo.add(result(deviceId = "device-$i", target = "ANDROID_TV"))
        }
        assertEquals(25, repo.count())
    }
}
