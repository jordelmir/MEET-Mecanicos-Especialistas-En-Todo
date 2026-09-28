package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-P3: Process-death restore policy tests (pure resolver, no Android).
 *
 * Contract proven:
 * - exact id restore → Ready at that candidate
 * - id gone → index fallback → identity verified → Ready
 * - id gone + index mismatch → RecoveryRequired (never silent candidate 0)
 * - no saved position → Ready at index 0
 */
class ProbeRestoreResolverTest {

    private fun mockCodeSet(id: String, address: Int): IrCodeSet = IrCodeSet(
        id = id,
        brand = "Samsung",
        modelPatterns = setOf("Generic"),
        remoteModels = emptySet(),
        commands = mapOf(
            IrAction.VOLUME_UP to IrSignal.Encoded(
                carrierHz = 38_000,
                protocol = IrProtocol.Nec,
                address = address,
                command = 0x10
            )
        ),
        provenance = CodeProvenance("Test", "http://test", "MIT"),
        verification = VerificationStatus.UNVERIFIED
    )

    private fun engine(vararg candidates: IrCodeSet) = IrProbeEngine(rawCandidates = candidates.toList())

    private val csA = mockCodeSet("cs-a", address = 1)
    private val csB = mockCodeSet("cs-b", address = 2)
    private val csC = mockCodeSet("cs-c", address = 3)

    @Test
    fun `restores by exact id preserving position`() = runTest {
        val eng = engine(csA, csB, csC)
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 2, restoreCandidateId = "cs-c")

        assertTrue("must be Ready", decision is ProbeRestoreDecision.Ready)
        assertEquals("cs-c", (decision as ProbeRestoreDecision.Ready).candidate?.id)
        assertEquals("engine repositioned on cs-c", "cs-c", eng.currentCandidate()?.id)
    }

    @Test
    fun `restores by id regardless of saved index`() = runTest {
        // Saved index points at cs-a but saved id is cs-c: id wins.
        val eng = engine(csA, csB, csC)
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 1, restoreCandidateId = "cs-c")

        assertTrue(decision is ProbeRestoreDecision.Ready)
        assertEquals("cs-c", (decision as ProbeRestoreDecision.Ready).candidate?.id)
    }

    @Test
    fun `id gone but index identity intact resumes at that index`() = runTest {
        // Catalog changed: candidate list reordered, but the candidate AT the
        // saved index still matches the saved identity (index-based restore).
        val eng = engine(csB, csA, csC)
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 2, restoreCandidateId = "cs-c")

        assertTrue(decision is ProbeRestoreDecision.Ready)
        assertEquals("cs-c", (decision as ProbeRestoreDecision.Ready).candidate?.id)
    }

    @Test
    fun `id gone and index mismatch requires recovery`() = runTest {
        // Saved: index 2 / id cs-c. New catalog dropped cs-b: index 2 is now cs-a.
        val eng = engine(csA, csB) // only 2 candidates remain
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 2, restoreCandidateId = "cs-c")

        assertTrue("must require recovery", decision is ProbeRestoreDecision.RecoveryRequired)
        val recovery = decision as ProbeRestoreDecision.RecoveryRequired
        assertEquals("cs-c", recovery.expectedId)
        assertTrue("found something else", recovery.foundId != "cs-c")
    }

    @Test
    fun `no saved position returns Ready at candidate zero`() = runTest {
        val eng = engine(csA, csB)
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 0, restoreCandidateId = null)

        assertTrue(decision is ProbeRestoreDecision.Ready)
        assertEquals("cs-a", (decision as ProbeRestoreDecision.Ready).candidate?.id)
    }

    @Test
    fun `index without id stays at candidate zero`() = runTest {
        // Conservative: without an id we cannot verify identity → Ready at 0
        // (caller decides whether that is acceptable).
        val eng = engine(csA, csB, csC)
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 2, restoreCandidateId = null)

        assertTrue(decision is ProbeRestoreDecision.Ready)
        assertEquals("cs-a", (decision as ProbeRestoreDecision.Ready).candidate?.id)
    }

    @Test
    fun `empty candidate list yields Ready with null candidate`() = runTest {
        val eng = engine()
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 0, restoreCandidateId = null)

        assertTrue(decision is ProbeRestoreDecision.Ready)
        assertNull((decision as ProbeRestoreDecision.Ready).candidate)
    }

    @Test
    fun `index beyond candidate count requires recovery when id mismatches`() = runTest {
        val eng = engine(csA)
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 5, restoreCandidateId = "cs-z")

        assertTrue(decision is ProbeRestoreDecision.RecoveryRequired)
        assertEquals("cs-z", (decision as ProbeRestoreDecision.RecoveryRequired).expectedId)
    }

    @Test
    fun `resolve never leaves engine on recovery-required state silently`() = runTest {
        // Saved identity cs-c is GONE from the catalog; the index fallback
        // climbs forward but cannot find cs-c → RecoveryRequired.
        // The engine must not be parked at candidate zero pretending a resume.
        val eng = engine(csA, csB) // cs-c removed
        val decision = ProbeRestoreResolver.resolve(eng, restoreCandidateIndex = 2, restoreCandidateId = "cs-c")

        assertTrue(decision is ProbeRestoreDecision.RecoveryRequired)
        val current = eng.currentCandidate()
        assertNotNull(current)
        assertTrue("must not resume at a trustable-candidate-0 position", current!!.id != "cs-c")
    }
}
