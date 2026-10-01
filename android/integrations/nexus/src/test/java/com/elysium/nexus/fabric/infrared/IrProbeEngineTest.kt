package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProbeEngineTest {

    private fun mockCodeSet(id: String, address: Int, command: Int): IrCodeSet = IrCodeSet(
        id = id,
        brand = "Sankey",
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
    fun `IrProbeEngine filters candidate code sets with VOLUME_UP action`() {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)
        val csNoVol = IrCodeSet(
            id = "csNoVol",
            brand = "Sankey",
            modelPatterns = setOf("NoVol"),
            remoteModels = emptySet(),
            commands = mapOf(
                IrAction.POWER_TOGGLE to IrSignal.Encoded(
                    carrierHz = 38_000,
                    protocol = IrProtocol.Nec,
                    address = 0,
                    command = 2
                )
            ),
            provenance = CodeProvenance("Test", "http://test", "MIT"),
            verification = VerificationStatus.UNVERIFIED
        )

        val engine = IrProbeEngine(listOf(cs1, csNoVol, cs2))
        assertEquals(2, engine.totalCandidates)
        assertEquals("cs1", engine.currentCandidate()?.id)
    }

    @Test
    fun `IrProbeEngine deduplicates candidate code sets with identical signal fingerprint`() {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs1Dup = mockCodeSet("cs1_dup", 0x00, 0x07) // Same address & command
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs1Dup, cs2))
        assertEquals(2, engine.totalCandidates) // cs1Dup deduplicated out!
    }

    @Test
    fun `IrProbeEngine advances through candidates on nextCandidate()`() = runTest {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs2))
        assertTrue(engine.hasMore)
        assertEquals(1, engine.currentProbeNumber)

        val first = engine.nextCandidate()
        assertEquals("cs1", first?.id)

        val second = engine.nextCandidate()
        assertEquals("cs2", second?.id)

        assertFalse(engine.hasMore)
        assertNull(engine.nextCandidate())
    }

    @Test
    fun `IrProbeEngine selects a previously transmitted candidate by ID`() = runTest {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)
        val cs3 = mockCodeSet("cs3", 0x08, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs2, cs3))

        // §38 Auto-sweep: capture the candidate, transmit, THEN advance.
        assertEquals("cs1", engine.currentCandidate()?.id)
        engine.nextCandidate()
        assertEquals("cs2", engine.currentCandidate()?.id)

        // The user confirms the LAST transmitted candidate (cs1) while the
        // engine already moved ahead; re-position must recover it.
        val repositioned = engine.selectById("cs1")
        assertTrue(repositioned)
        assertEquals("cs1", engine.currentCandidate()?.id)
        assertEquals(1, engine.currentProbeNumber)
    }

    @Test
    fun `IrProbeEngine selectById is a no-op for unknown candidate`() {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val engine = IrProbeEngine(listOf(cs1))

        assertFalse(engine.selectById("nope"))
        assertEquals("cs1", engine.currentCandidate()?.id)
    }

    @Test
    fun `IrProbeEngine universal sweep respects totalCandidates ceiling`() = runTest {
        // 400 universal candidates with DISTINCT fingerprints stay reachable one-by-one.
        val many = (0 until 400).map { mockCodeSet("cs_$it", it * 17 + 1, 0x07) }
        val engine = IrProbeEngine(many)
        assertEquals(400, engine.totalCandidates)

        engine.nextCandidate()
        engine.nextCandidate()
        assertEquals(3, engine.currentProbeNumber)
        assertTrue(engine.hasMore)
    }
}
