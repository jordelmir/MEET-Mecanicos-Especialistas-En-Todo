package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.ranking.CandidatePager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-P27: the paged variant of the probe engine walks the universal
 * sweep with bounded memory (§75–§78) while preserving probe semantics.
 */
class PagedIrProbeEngineTest {

    /** Sweep-shaped source: `count` candidates ordered by probability. */
    private fun sweep(count: Int, pageSize: Int, maxCachedPages: Int): CandidatePager<IrCodeSet> {
        val all = (0 until count).map { mockCodeSet("cs$it", address = it, command = 0x07) }
        return CandidatePager(
            pageSize = pageSize,
            maxCachedPages = maxCachedPages,
            totalCount = all.size,
            pageLoader = { from, n -> all.subList(from, from + n) }
        )
    }

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

    /** Phase A: candidate exposing arbitrary probe keys (address,command per key). */
    private fun mockCodeSetMulti(
        id: String,
        signals: Map<IrAction, Pair<Int, Int>>
    ): IrCodeSet = IrCodeSet(
        id = id,
        brand = "Sankey",
        modelPatterns = setOf("Generic"),
        remoteModels = emptySet(),
        commands = signals.mapValues { (_, ac) ->
            IrSignal.Encoded(
                carrierHz = 38_000,
                protocol = IrProtocol.Nec,
                address = ac.first,
                command = ac.second
            )
        },
        provenance = CodeProvenance("Test", "http://test", "MIT"),
        verification = VerificationStatus.UNVERIFIED
    )

    @Test
    fun `drains the whole sweep with bounded memory`() = runTest {
        val pager = sweep(count = 1_000, pageSize = 10, maxCachedPages = 3)
        val engine = PagedIrProbeEngine(pager)

        var drained = 0
        while (engine.nextCandidate() != null) drained++

        assertEquals(1_000, drained)
        assertEquals(1_000, engine.currentProbeNumber)
        assertFalse(engine.hasMore)
        // the memory bound is the point of this phase
        assertTrue("engine materialized too much: ${pager.loadedItems}",
            pager.loadedItems <= 10 * 3)
    }

    @Test
    fun `pages with no VOLUME_UP are skipped until candidates exist`() = runTest {
        val all = (0 until 20).map { mockCodeSet("cs$it", address = it, command = 0x07) }
        val noVol = IrCodeSet(
            id = "noVol",
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
        // first page: 5 noVOLUME candidates, then the real ones
        val pager = CandidatePager(
            pageSize = 10,
            maxCachedPages = 3,
            totalCount = 21,
            pageLoader = { from, n ->
                if (from == 0) listOf(noVol, noVol, noVol, noVol, noVol) + all.take(5)
                else all.subList(from - 1 + 5, from - 1 + 5 + n)
            }
        )
        val engine = PagedIrProbeEngine(pager)
        val first = engine.nextCandidate()
        assertEquals("cs0", first!!.id)
    }

    @Test
    fun `deduplicates identical fingerprint signals across page boundaries`() = runTest {
        // same address+command → same fingerprint, spread across two pages
        val pager = CandidatePager(
            pageSize = 2,
            maxCachedPages = 2,
            totalCount = 4,
            pageLoader = { from, n -> (0 until n).map { mockCodeSet("dup$from-$it", address = 7, command = 7) } }
        )
        val engine = PagedIrProbeEngine(pager)
        val unique = mutableListOf<String>()
        while (engine.nextCandidate() != null) {
            unique += (engine.currentProbeNumber - 1).toString()
        }
        assertEquals("across-page fingerprints must dedupe", 1, unique.size)
    }

    @Test
    fun `dedup off transmits EVERY candidate across ALL pages to the end`() = runTest {
        // 366 candidates sharing the SAME VOLUME_UP fingerprint, spread over
        // 8 pages (pageSize=50). With dedup OFF the sweep must hand out all
        // 366, crossing every page boundary, ending only when exhausted.
        val all = (0 until 366).map { mockCodeSet("cs$it", address = 7, command = 7) }
        val pager = CandidatePager(
            pageSize = 50,
            maxCachedPages = 4,
            totalCount = all.size,
            pageLoader = { from, n -> all.subList(from, from + n) }
        )
        val engine = PagedIrProbeEngine(pager, deduplicateFingerprints = false)

        var handedOut = 0
        var last: IrCodeSet? = null
        while (true) {
            val next = engine.nextCandidate() ?: break
            last = next
            handedOut++
        }

        assertEquals("every candidate must be handed out", 366, handedOut)
        assertEquals(366, engine.currentProbeNumber)
        assertEquals("cs365", last!!.id)
        assertEquals("cursor must be exhausted at the true end", CursorState.EXHAUSTED, engine.state)
        assertFalse(engine.hasMore)
        assertNull("no candidate left", engine.currentCandidate())
        // memory bound still holds with dedup off
        assertTrue("engine materialized too much: ${pager.loadedItems}",
            pager.loadedItems <= 50 * 4)
    }

    @Test
    fun `totalCandidates reports the REAL dedup-adjusted sweep size after initialize`() = runTest {
        // 366 raw candidates with only 101 unique VOLUME_UP fingerprints:
        // the UI must show 101 (the real, usable count), never the raw 366.
        val raw = (0 until 366).map { mockCodeSet("cs$it", address = it % 101, command = 0x07) }
        val pager = CandidatePager(
            pageSize = 50,
            maxCachedPages = 4,
            totalCount = raw.size,
            pageLoader = { from, n -> raw.subList(from, from + n) }
        )
        val engine = PagedIrProbeEngine(pager)
        engine.initialize()

        assertEquals("UI must show only real candidates", 101, engine.totalCandidates)

        var handedOut = 0
        while (engine.nextCandidate() != null) handedOut++
        assertEquals("sweep must hand out exactly the dedup-adjusted total", 101, handedOut)
        assertEquals(101, engine.currentProbeNumber)
        assertEquals("cursor must be exhausted at the true end", CursorState.EXHAUSTED, engine.state)
        assertFalse(engine.hasMore)
    }

    @Test
    fun `totalCandidates stays raw when dedup is disabled`() = runTest {
        val raw = (0 until 366).map { mockCodeSet("cs$it", address = it % 101, command = 0x07) }
        val pager = CandidatePager(
            pageSize = 50,
            maxCachedPages = 4,
            totalCount = raw.size,
            pageLoader = { from, n -> raw.subList(from, from + n) }
        )
        val engine = PagedIrProbeEngine(pager, deduplicateFingerprints = false)
        engine.initialize()

        assertEquals("dedup off must keep the raw count", 366, engine.totalCandidates)

        var handedOut = 0
        while (engine.nextCandidate() != null) handedOut++
        assertEquals(366, handedOut)
    }

    @Test
    fun `totalCandidates is honest even when the first page has no unique candidates`() = runTest {
        // Every candidate duplicates the fingerprint of a previous one across
        // pages: the sweep must still report and hand out the REAL total.
        val all = (0 until 50).map { mockCodeSet("cs$it", address = 7, command = 7) }
        val pager = CandidatePager(
            pageSize = 10,
            maxCachedPages = 4,
            totalCount = all.size,
            pageLoader = { from, n -> all.subList(from, from + n) }
        )
        val engine = PagedIrProbeEngine(pager)
        engine.initialize()

        assertEquals("one unique fingerprint out of 50 raw", 1, engine.totalCandidates)
        assertNotNull(engine.currentCandidate())
        engine.nextCandidate()
        assertNull("sweep ends after the single real candidate", engine.nextCandidate())
        assertEquals(CursorState.EXHAUSTED, engine.state)
    }

    @Test
    fun `dedup off crosses multiple page boundaries with identical signals per page`() = runTest {
        // Each page holds candidates with identical fingerprints; dedup OFF
        // must still cross pages 1,2,3 (not stall at any boundary).
        val all = (0 until 200).map { mockCodeSet("cs$it", address = 3, command = 9) }
        val pager = CandidatePager(
            pageSize = 25,
            maxCachedPages = 4,
            totalCount = all.size,
            pageLoader = { from, n -> all.subList(from, from + n) }
        )
        val engine = PagedIrProbeEngine(pager, deduplicateFingerprints = false)

        var handedOut = 0
        while (engine.nextCandidate() != null) handedOut++
        assertEquals(200, handedOut)
        assertEquals(200, engine.currentProbeNumber)
    }

    @Test
    fun `dedup default ON still drops identical fingerprints`() = runTest {
        val all = (0 until 120).map { mockCodeSet("cs$it", address = 5, command = 5) }
        val pager = CandidatePager(
            pageSize = 30,
            maxCachedPages = 4,
            totalCount = all.size,
            pageLoader = { from, n -> all.subList(from, from + n) }
        )
        val engine = PagedIrProbeEngine(pager)
        var handedOut = 0
        while (engine.nextCandidate() != null) handedOut++
        assertEquals("default dedup must collapse identical fingerprints to 1", 1, handedOut)
    }

    @Test
    fun `selectById finds candidates inside the loaded window`() = runTest {
        val engine = PagedIrProbeEngine(sweep(count = 50, pageSize = 10, maxCachedPages = 4))
        repeat(12) { engine.nextCandidate() }  // consumed through page 1

        assertTrue(engine.selectById("cs5"))
        assertEquals("cs5", engine.currentCandidate()!!.id)
    }

    @Test
    fun `selectById is bounded beyond the window and honest when missing`() = runTest {
        val pager = sweep(count = 1_000, pageSize = 10, maxCachedPages = 2)
        val engine = PagedIrProbeEngine(pager)

        // 30 consumed: selected from page 2 (within loaded window + 2 fresh pages)
        repeat(30) { engine.nextCandidate() }
        assertTrue(engine.selectById("cs21"))
        assertEquals("cs21", engine.currentCandidate()!!.id)

        // far beyond the bounded search window (page 47): honestly not found
        assertFalse(engine.selectById("cs470"))
        assertEquals("position unchanged", "cs21", engine.currentCandidate()!!.id)
    }

    @Test
    fun `reset restores the sweep from the beginning`() = runTest {
        val engine = PagedIrProbeEngine(sweep(count = 30, pageSize = 10, maxCachedPages = 2))
        repeat(15) { engine.nextCandidate() }
        engine.reset()
        assertEquals("cs0", engine.nextCandidate()!!.id)
        assertEquals(1, engine.currentProbeNumber)
    }

    @Test
    fun `totalCandidates reports the source sweep size before initialize`() {
        val engine = PagedIrProbeEngine(sweep(count = 400, pageSize = 40, maxCachedPages = 2))
        assertEquals(400, engine.totalCandidates)
        assertNull(engine.currentCandidate())
    }

    // ── Phase A — multi-key sweep ──────────────────────────────────────────

    private fun multiKeyPager(
        candidates: List<IrCodeSet>,
        pageSize: Int = 10,
        maxCachedPages: Int = 4
    ): CandidatePager<IrCodeSet> = CandidatePager(
        pageSize = pageSize,
        maxCachedPages = maxCachedPages,
        totalCount = candidates.size,
        pageLoader = { from, n -> candidates.subList(from, from + n) }
    )

    private val THREE_KEYS = listOf(
        IrAction.VOLUME_UP, IrAction.MUTE, IrAction.POWER_TOGGLE
    )

    @Test
    fun `multi-key keeps candidates sharing VOLUME_UP but differing in MUTE`() = runTest {
        // Same VOLUME_UP emission, different MUTE: the sweep must test BOTH —
        // the TV may respond to the MUTE of the second one.
        val a = mockCodeSetMulti("csA", mapOf(
            IrAction.VOLUME_UP to (0x01 to 0x10),
            IrAction.MUTE to (0x01 to 0x30)
        ))
        val b = mockCodeSetMulti("csB", mapOf(
            IrAction.VOLUME_UP to (0x01 to 0x10),
            IrAction.MUTE to (0x01 to 0x31)
        ))
        val pager = multiKeyPager(listOf(a, b))
        val engine = PagedIrProbeEngine(pager, probeKeys = THREE_KEYS)
        engine.initialize()

        var handedOut = 0
        while (engine.nextCandidate() != null) handedOut++
        assertEquals("MUTE differs → both are real candidates", 2, handedOut)
        assertEquals(2, engine.totalCandidates)
    }

    @Test
    fun `multi-key collapses only fully identical key tuples`() = runTest {
        val same = mapOf(
            IrAction.VOLUME_UP to (0x01 to 0x10),
            IrAction.MUTE to (0x01 to 0x30),
            IrAction.POWER_TOGGLE to (0x01 to 0x02)
        )
        val pager = multiKeyPager(listOf(
            mockCodeSetMulti("csA", same),
            mockCodeSetMulti("csB", same)
        ))
        val engine = PagedIrProbeEngine(pager, probeKeys = THREE_KEYS)
        engine.initialize()

        var handedOut = 0
        while (engine.nextCandidate() != null) handedOut++
        assertEquals("all three keys identical → one real candidate", 1, handedOut)
        assertEquals(1, engine.totalCandidates)
    }

    @Test
    fun `candidate reachable only via MUTE stays in the multi-key sweep`() = runTest {
        // No VOLUME_UP at all — the old sweep lost this TV; the multi-key
        // sweep must keep it because MUTE is one of the probe keys.
        val muteOnly = mockCodeSetMulti("csMute", mapOf(
            IrAction.MUTE to (0x07 to 0x31),
            IrAction.POWER_TOGGLE to (0x07 to 0x02)
        ))
        val pager = multiKeyPager(listOf(muteOnly))
        val engine = PagedIrProbeEngine(pager, probeKeys = THREE_KEYS)
        engine.initialize()

        assertEquals(1, engine.totalCandidates)
        assertEquals("csMute", engine.nextCandidate()!!.id)
    }

    @Test
    fun `multi-key sweep without probe keys does not drop power-only candidates`() = runTest {
        val powerOnly = mockCodeSetMulti("csPower", mapOf(
            IrAction.POWER_TOGGLE to (0x07 to 0x02)
        ))
        val pager = multiKeyPager(listOf(powerOnly))
        val engine = PagedIrProbeEngine(pager, probeKeys = THREE_KEYS)
        engine.initialize()

        assertEquals("POWER-only TV is a real candidate", 1, engine.totalCandidates)
        assertEquals("csPower", engine.nextCandidate()!!.id)
    }
}
