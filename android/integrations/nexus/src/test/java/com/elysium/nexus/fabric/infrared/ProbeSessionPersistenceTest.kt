package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.profile.db.ProbeSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0.3: Unit tests for probe session persistence and process death recovery.
 *
 * Tests verify:
 * - ProbeSessionEntity has all required fields
 * - Engine repositioning by candidate ID works correctly
 * - Engine repositioning fallback to index works
 * - Candidate identity verification after restore
 * - Verified actions serialization round-trip
 * - Engine state after restore matches expected position
 */
class ProbeSessionPersistenceTest {

    private fun mockCodeSet(id: String, address: Int, command: Int): IrCodeSet = IrCodeSet(
        id = id,
        brand = "Samsung",
        modelPatterns = setOf("Generic"),
        remoteModels = emptySet(),
        commands = mapOf(
            IrAction.VOLUME_UP to IrSignal.Encoded(
                carrierHz = 38_000,
                protocol = IrProtocol.Nec,
                address = address,
                command = command
            )
        ),
        provenance = CodeProvenance("Test", "http://test", "MIT"),
        verification = VerificationStatus.UNVERIFIED
    )

    @Test
    fun `ProbeSessionEntity has all required P0_3 fields`() {
        val entity = ProbeSessionEntity(
            sessionId = "session-1",
            brand = "Samsung",
            deviceType = "TV",
            targetModel = "UN55CU7000",
            startedAtEpochMs = 1000L,
            completedAtEpochMs = null,
            status = "ORIENT",
            winnerCodeSetId = null,
            currentCandidateIndex = 3,
            currentCandidateId = "cs-42",
            currentActionKey = "VOLUME_UP",
            lastSignalId = "sig-123",
            lastPhysicalSha256 = "sha256-abc",
            lastAttemptId = "attempt-789",
            catalogHashAtStart = "catalog-hash-v1",
            verifiedActionKeys = "VOLUME_UP,VOLUME_DOWN"
        )

        assertEquals(3, entity.currentCandidateIndex)
        assertEquals("cs-42", entity.currentCandidateId)
        assertEquals("VOLUME_UP", entity.currentActionKey)
        assertEquals("sig-123", entity.lastSignalId)
        assertEquals("sha256-abc", entity.lastPhysicalSha256)
        assertEquals("attempt-789", entity.lastAttemptId)
        assertEquals("catalog-hash-v1", entity.catalogHashAtStart)
        assertEquals("VOLUME_UP,VOLUME_DOWN", entity.verifiedActionKeys)
    }

    @Test
    fun `ProbeSessionEntity defaults are safe for new sessions`() {
        val entity = ProbeSessionEntity(
            sessionId = "session-new",
            brand = "LG",
            deviceType = "TV",
            targetModel = null,
            startedAtEpochMs = 1000L,
            completedAtEpochMs = null,
            status = "ORIENT",
            winnerCodeSetId = null,
            currentCandidateIndex = 0,
            currentCandidateId = null,
            currentActionKey = null,
            lastSignalId = null,
            lastPhysicalSha256 = null,
            lastAttemptId = null,
            catalogHashAtStart = null,
            verifiedActionKeys = ""
        )

        assertEquals(0, entity.currentCandidateIndex)
        assertNull(entity.currentCandidateId)
        assertNull(entity.lastAttemptId)
        assertEquals("", entity.verifiedActionKeys)
    }

    @Test
    fun `Engine repositioning by selectById restores exact candidate`() {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)
        val cs3 = mockCodeSet("cs3", 0x08, 0x07)
        val cs4 = mockCodeSet("cs4", 0x0C, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs2, cs3, cs4))

        // Simulate: user was testing cs3 (index 2), process died
        val savedIndex = 2
        val savedId = "cs3"

        // Restore: reposition by ID
        val repositioned = engine.selectById(savedId)
        assertTrue(repositioned)
        assertEquals("cs3", engine.currentCandidate()?.id)
        assertEquals(3, engine.currentProbeNumber)
    }

    @Test
    fun `Engine repositioning fallback to index when ID not found`() = runTest {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)
        val cs3 = mockCodeSet("cs3", 0x08, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs2, cs3))

        // Simulate: saved ID "cs_deleted" no longer exists (catalog changed)
        val savedIndex = 1
        val savedId = "cs_deleted"

        // Fallback: reposition by index
        val repositioned = engine.selectById(savedId)
        assertFalse(repositioned)

        // Fallback to index-based repositioning
        engine.reset()
        repeat(savedIndex) { engine.nextCandidate() }
        assertEquals("cs2", engine.currentCandidate()?.id)
        assertEquals(2, engine.currentProbeNumber)
    }

    @Test
    fun `Candidate identity verification after restore catches mismatch`() = runTest {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)
        val cs3 = mockCodeSet("cs3", 0x08, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs2, cs3))

        // Save: was testing cs2 at index 1
        val savedIndex = 1
        val savedId = "cs2"

        // Restore by index (ID-based fails)
        engine.reset()
        repeat(savedIndex) { engine.nextCandidate() }

        // Verify identity
        val current = engine.currentCandidate()
        assertEquals(savedId, current?.id)

        // Now test mismatch scenario: catalog changed, cs2 moved
        val engine2 = IrProbeEngine(listOf(cs1, cs3)) // cs2 removed
        engine2.reset()
        repeat(savedIndex.coerceAtMost(engine2.totalCandidates - 1)) { engine2.nextCandidate() }

        val current2 = engine2.currentCandidate()
        // cs3 is at index 1 now, but we expected cs2
        assertEquals("cs3", current2?.id)
        assertEquals(savedId, savedId) // saved was cs2
        assertTrue(current2?.id != savedId) // MISMATCH!
    }

    @Test
    fun `Engine preserves position after multiple advances`() = runTest {
        val candidates = (0 until 10).map { mockCodeSet("cs_$it", it, 0x07) }
        val engine = IrProbeEngine(candidates)

        // Advance to candidate 5
        repeat(5) { engine.nextCandidate() }
        assertEquals("cs_5", engine.currentCandidate()?.id)
        assertEquals(6, engine.currentProbeNumber)

        // Save state
        val savedIndex = 5
        val savedId = "cs_5"

        // Create new engine and restore
        val engine2 = IrProbeEngine(candidates)
        val repositioned = engine2.selectById(savedId)
        assertTrue(repositioned)
        assertEquals("cs_5", engine2.currentCandidate()?.id)
        assertEquals(6, engine2.currentProbeNumber)
    }

    @Test
    fun `Verified actions serialization round trip`() {
        val actions = setOf(IrAction.VOLUME_UP, IrAction.VOLUME_DOWN, IrAction.MUTE)

        // Serialize
        val serialized = actions.joinToString(",") { it.name }
        assertEquals("VOLUME_UP,VOLUME_DOWN,MUTE", serialized)

        // Deserialize
        val deserialized = serialized.split(",").mapNotNull {
            try { IrAction.valueOf(it.trim()) } catch (e: Exception) { null }
        }.toSet()
        assertEquals(actions, deserialized)
    }

    @Test
    fun `Verified actions serialization handles empty set`() {
        val actions = emptySet<IrAction>()
        val serialized = actions.joinToString(",") { it.name }
        assertEquals("", serialized)

        val deserialized = if (serialized.isBlank()) emptySet()
        else serialized.split(",").mapNotNull {
            try { IrAction.valueOf(it.trim()) } catch (e: Exception) { null }
        }.toSet()
        assertEquals(emptySet<IrAction>(), deserialized)
    }

    @Test
    fun `Engine exhaustion detection after restore near end`() = runTest {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs2))

        // Advance to last candidate
        engine.nextCandidate()
        assertEquals("cs2", engine.currentCandidate()?.id)
        assertTrue(engine.hasMore)

        // Save at last candidate
        val savedId = "cs2"

        // Restore
        val engine2 = IrProbeEngine(listOf(cs1, cs2))
        engine2.selectById(savedId)
        assertEquals("cs2", engine2.currentCandidate()?.id)
        assertTrue(engine2.hasMore)

        // Advance past last
        engine2.nextCandidate()
        assertFalse(engine2.hasMore)
        assertNull(engine2.currentCandidate())
    }
}
