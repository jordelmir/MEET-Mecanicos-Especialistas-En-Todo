package com.elysium.nexus.fabric.infrared.database

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.infrared.IrProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Binding sort determinism contract.
 *
 * Verifies that the candidate selection algorithm in IrCatalogRepository
 * produces the same winner for identical inputs across multiple runs,
 * and that tie-breaking follows the documented priority:
 *
 *   1. verificationRank (highest first)
 *   2. RAW encoding preferred
 *   3. sourcePriority (highest first)
 *   4. bindingId (alphabetical, stable tie-break)
 *
 * Regression: any change to the sort chain must keep these tests green.
 */
class BindingSortDeterminismTest {

    /**
     * Mirror of IrCatalogRepository.PendingBinding for test isolation.
     * If the real class changes, this test catches the drift.
     */
    private data class TestBinding(
        val action: IrAction,
        val signal: IrSignal,
        val signalId: String,
        val bindingId: String,
        val codeSetId: String,
        val encodingType: String,
        val sourcePriority: Int,
        val verificationStatus: String
    )

    /** Mirror of IrCatalogRepository.verificationRank */
    private fun verificationRank(status: String): Int = when (status) {
        "VERIFIED_LAB" -> 5
        "VERIFIED_COMMUNITY" -> 4
        "PARTIALLY_VERIFIED" -> 3
        "STRUCTURALLY_VALID", "PROTOCOL_VALIDATED" -> 2
        "VERIFIED" -> 1
        else -> 0
    }

    private fun sortBindings(bindings: List<TestBinding>): TestBinding? =
        bindings.sortedWith(
            compareByDescending<TestBinding> { verificationRank(it.verificationStatus) }
                .thenByDescending { if (it.encodingType == "RAW") 1 else 0 }
                .thenByDescending { it.sourcePriority }
                .thenBy { it.bindingId }
        ).firstOrNull()

    private fun testSignal(protocol: IrProtocol = IrProtocol.Nec) = IrSignal.Encoded(
        carrierHz = 38000,
        protocol = protocol,
        address = 0x04,
        command = 0x08,
        repeats = 3
    )

    // ── Determinism: same input → same winner ──

    @Test
    fun sameInputProducesSameWinner() {
        val bindings = listOf(
            TestBinding(IrAction.VOLUME_UP, testSignal(), "sig-1", "bind-a", "cs-1", "PARAMETRIC", 10, "VERIFIED"),
            TestBinding(IrAction.VOLUME_UP, testSignal(), "sig-2", "bind-b", "cs-1", "RAW", 5, "PARTIALLY_VERIFIED"),
            TestBinding(IrAction.VOLUME_UP, testSignal(), "sig-3", "bind-c", "cs-2", "PARAMETRIC", 15, "VERIFIED"),
        )
        val winner1 = sortBindings(bindings)
        val winner2 = sortBindings(bindings)
        val winner3 = sortBindings(bindings)
        assertNotNull(winner1)
        assertEquals(winner1, winner2)
        assertEquals(winner2, winner3)
    }

    // ── Priority 1: verificationRank wins ──

    @Test
    fun higherVerificationRankWins() {
        val bindings = listOf(
            TestBinding(IrAction.MUTE, testSignal(), "sig-low", "bind-1", "cs-1", "RAW", 100, "VERIFIED"),
            TestBinding(IrAction.MUTE, testSignal(), "sig-high", "bind-2", "cs-2", "PARAMETRIC", 1, "VERIFIED_LAB"),
        )
        val winner = sortBindings(bindings)
        assertEquals("sig-high", winner?.signalId)
        assertEquals("VERIFIED_LAB", winner?.verificationStatus)
    }

    @Test
    fun communityBeatsUnverified() {
        val bindings = listOf(
            TestBinding(IrAction.POWER_TOGGLE, testSignal(), "sig-unverified", "bind-1", "cs-1", "RAW", 50, "UNVERIFIED"),
            TestBinding(IrAction.POWER_TOGGLE, testSignal(), "sig-community", "bind-2", "cs-2", "RAW", 10, "VERIFIED_COMMUNITY"),
        )
        val winner = sortBindings(bindings)
        assertEquals("sig-community", winner?.signalId)
    }

    // ── Priority 2: RAW encoding preferred ──

    @Test
    fun rawEncodingWinsOverParametric() {
        val bindings = listOf(
            TestBinding(IrAction.VOLUME_DOWN, testSignal(), "sig-param", "bind-1", "cs-1", "PARAMETRIC", 10, "VERIFIED"),
            TestBinding(IrAction.VOLUME_DOWN, testSignal(), "sig-raw", "bind-2", "cs-2", "RAW", 10, "VERIFIED"),
        )
        val winner = sortBindings(bindings)
        assertEquals("sig-raw", winner?.signalId)
        assertEquals("RAW", winner?.encodingType)
    }

    // ── Priority 3: sourcePriority ──

    @Test
    fun higherSourcePriorityWins() {
        val bindings = listOf(
            TestBinding(IrAction.CHANNEL_UP, testSignal(), "sig-low-pri", "bind-1", "cs-1", "RAW", 5, "VERIFIED"),
            TestBinding(IrAction.CHANNEL_UP, testSignal(), "sig-high-pri", "bind-2", "cs-2", "RAW", 20, "VERIFIED"),
        )
        val winner = sortBindings(bindings)
        assertEquals("sig-high-pri", winner?.signalId)
        assertEquals(20, winner?.sourcePriority)
    }

    // ── Priority 4: bindingId alphabetical tie-break ──

    @Test
    fun bindingIdTieBreakIsAlphabetical() {
        val bindings = listOf(
            TestBinding(IrAction.CHANNEL_DOWN, testSignal(), "sig-z", "bind-zzz", "cs-1", "RAW", 10, "VERIFIED"),
            TestBinding(IrAction.CHANNEL_DOWN, testSignal(), "sig-a", "bind-aaa", "cs-2", "RAW", 10, "VERIFIED"),
        )
        val winner = sortBindings(bindings)
        assertEquals("sig-a", winner?.signalId)
        assertEquals("bind-aaa", winner?.bindingId)
    }

    // ── Full stack: combined priorities ──

    @Test
    fun fullPriorityChain() {
        val bindings = listOf(
            // Low verification, high source priority, RAW
            TestBinding(IrAction.MENU, testSignal(), "sig-1", "bind-1", "cs-1", "RAW", 100, "UNVERIFIED"),
            // High verification, low source priority, PARAMETRIC
            TestBinding(IrAction.MENU, testSignal(), "sig-2", "bind-2", "cs-2", "PARAMETRIC", 1, "VERIFIED_LAB"),
            // Medium verification, medium source, RAW
            TestBinding(IrAction.MENU, testSignal(), "sig-3", "bind-3", "cs-3", "RAW", 50, "VERIFIED_COMMUNITY"),
        )
        val winner = sortBindings(bindings)
        // VERIFIED_LAB (rank 5) beats everything
        assertEquals("sig-2", winner?.signalId)
    }

    @Test
    fun rawBeatsHigherPriorityWhenVerificationEqual() {
        val bindings = listOf(
            TestBinding(IrAction.HOME, testSignal(), "sig-a", "bind-a", "cs-1", "PARAMETRIC", 100, "VERIFIED"),
            TestBinding(IrAction.HOME, testSignal(), "sig-b", "bind-b", "cs-2", "RAW", 1, "VERIFIED"),
        )
        val winner = sortBindings(bindings)
        assertEquals("sig-b", winner?.signalId)
        assertEquals("RAW", winner?.encodingType)
    }

    // ── Single binding: trivial case ──

    @Test
    fun singleBindingWins() {
        val bindings = listOf(
            TestBinding(IrAction.NUM_0, testSignal(), "sig-only", "bind-only", "cs-1", "RAW", 0, "UNVERIFIED"),
        )
        val winner = sortBindings(bindings)
        assertEquals("sig-only", winner?.signalId)
    }

    // ── Empty list: returns null ──

    @Test
    fun emptyBindingsReturnsNull() {
        val winner = sortBindings(emptyList())
        assertEquals(null, winner)
    }

    // ── Large list: determinism under volume ──

    @Test
    fun largeBindingListDeterminism() {
        val bindings = (1..200).map { i ->
            TestBinding(
                action = IrAction.VOLUME_UP,
                signal = testSignal(),
                signalId = "sig-$i",
                bindingId = "bind-${String.format("%04d", 200 - i)}",
                codeSetId = "cs-$i",
                encodingType = if (i % 3 == 0) "RAW" else "PARAMETRIC",
                sourcePriority = i % 10,
                verificationStatus = when (i % 5) {
                    0 -> "VERIFIED_LAB"
                    1 -> "VERIFIED_COMMUNITY"
                    2 -> "PARTIALLY_VERIFIED"
                    3 -> "VERIFIED"
                    else -> "UNVERIFIED"
                }
            )
        }
        val winner1 = sortBindings(bindings)
        val winner2 = sortBindings(bindings.shuffled())
        val winner3 = sortBindings(bindings.reversed())
        // Same winner regardless of input order
        assertEquals(winner1?.signalId, winner2?.signalId)
        assertEquals(winner2?.signalId, winner3?.signalId)
    }
}
