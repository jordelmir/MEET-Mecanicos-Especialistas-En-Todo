package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.ranking.CandidatePager

/**
 * V06-P27 — Paged variant of [IrProbeEngine] (MASTER_ORDER §75–§78:
 * candidate paging, bounded memory).
 *
 * The eager [IrProbeEngine] materializes the ENTIRE universal sweep in
 * memory (`filter → distinctBy fingerprint → sortedByDescending` over all
 * candidates). This variant walks the same sweep through a
 * [CandidatePager]: pages are ranked by the source (the catalog's §P0-8
 * probability ordering), filtered to `VOLUME_UP`, fingerprint-deduplicated
 * ACROSS pages (bounded set) and locally re-scored inside the page.
 *
 * Memory bound: only `pageSize * maxCachedPages` candidates are ever
 * materialized, whatever the sweep size (test-enforced).
 *
 * Honesty:
 * - `totalCandidates` is the SOURCE count (catalog-level), not the
 *   dedup-adjusted count — as in the eager engine, pre-filter rows count.
 * - Strict global re-ranking across all pages is not performed: each page
 *   is re-scored locally after the source's global ranking; candidates
 *   inside a page are ordered by [CandidateScorer].
 * - [selectById] searches the loaded window + at most `maxCachedPages`
 *   fresh pages (bounded). A candidate beyond that window is honestly
 *   reported as not found (returns false, position unchanged).
 */
class PagedIrProbeEngine(
    private val pager: CandidatePager<IrCodeSet>,
    targetModel: String? = null,
    private val penaltyMap: Map<String, Int> = emptyMap(),
    private val successMap: Map<String, Int> = emptyMap(),
    private val failMap: Map<String, Int> = emptyMap(),
    /**
     * Phase A — multi-key sweep: the candidate pool is the UNION of these
     * probe keys, and dedup collapses two candidates ONLY when every probe
     * key they expose is physically identical. A TV reachable via MUTE or
     * POWER_TOGGLE but not VOLUME_UP stays in the sweep.
     */
    private val probeKeys: List<IrAction> = listOf(IrAction.VOLUME_UP),
    /**
     * RC-12: dedup is a speed optimization, NOT a correctness requirement.
     * The universal sweep keeps it ON (default): duplicate signals are the
     * same physical IR emission, so probing them again adds no information —
     * the UI shows the dedup-adjusted total via [totalCandidates]. Callers
     * that want literal coverage of every code_set pass false.
     */
    private val deduplicateFingerprints: Boolean = true
) : ProbeCursor {
    private val targetModel: String? = targetModel

    private val seenFingerprints: MutableSet<String> = HashSet()

    private var pageIndex = -1
    private var pageItems: List<IrCodeSet> = emptyList()
    private var itemIndex = 0
    private var consumed = 0

    /**
     * RC-12: honest sweep size. Computed once in [initialize] — the count
     * of candidates that will ACTUALLY be handed out: unique fingerprints
     * when dedup is ON (the universal sweep), source count when OFF.
     * The UI shows this number, never the raw catalog count.
     */
    private var computedTotal: Int? = null

    /** V0.6.3 Phase 2: Engine state machine. */
    private var _state: CursorState = CursorState.UNINITIALIZED
    override val state: CursorState get() = _state

    /** RC-12: sweep size the UI shows (dedup-adjusted, never raw source count). */
    override val totalCandidates: Int get() = computedTotal ?: pager.totalCount

    /** How many candidates the cursor has handed out. */
    override val currentProbeNumber: Int get() = consumed

    override val hasMore: Boolean
        get() = itemIndex < pageItems.size || (pageIndex + 1) < pager.pageCount

    /** Pages materialized so far (memory accounting). */
    val loadedPagesCount: Int get() = pager.loadedPages

    /** Candidates materialized so far (memory accounting). */
    val loadedItemsCount: Int get() = pager.loadedItems

    /**
     * V0.6.3 Phase 3: Initialize engine — load page 0, filter, dedup, rank.
     * After this returns Ready, [currentCandidate] MUST NOT be null.
     */
    override suspend fun initialize(): CursorInitResult {
        if (_state == CursorState.READY) return CursorInitResult.Ready(this)
        if (_state == CursorState.EXHAUSTED) return CursorInitResult.NoCandidates

        // Reset to clean state
        pageIndex = -1
        pageItems = emptyList()
        itemIndex = 0
        consumed = 0
        seenFingerprints.clear()

        // Load first page
        if (!loadNextPage()) {
            _state = CursorState.EXHAUSTED
            return CursorInitResult.NoCandidates
        }

        // RC-12: honest count — walk every page with the SAME filter (and
        // dedup when enabled) the sweep will use, so totalCandidates is the
        // number of candidates the user will really see. Never the raw
        // catalog count (e.g. 101 real out of 366 raw).
        computedTotal = dryRunUniqueCount()

        // Verify invariant: READY implies currentCandidate != null
        if (pageItems.isEmpty()) {
            _state = CursorState.EXHAUSTED
            return CursorInitResult.NoCandidates
        }

        _state = CursorState.READY
        return CursorInitResult.Ready(this)
    }

    /** Get the currently selected candidate, or null if exhausted. */
    override fun currentCandidate(): IrCodeSet? = pageItems.getOrNull(itemIndex)

    /** Advance to the next ranked candidate. Returns null when exhausted. */
    override suspend fun nextCandidate(): IrCodeSet? {
        while (true) {
            if (itemIndex < pageItems.size) {
                val current = pageItems[itemIndex]
                itemIndex++
                consumed++
                return current
            }
            if (!loadNextPage()) {
                _state = CursorState.EXHAUSTED
                return null
            }
        }
    }

    /**
     * Re-position the probe on a candidate by ID. Searches every page the
     * cursor has already visited (served from the bounded page cache) plus
     * at most [CandidatePager.maxCachedPages] fresh pages — the window is
     * bounded, so a candidate beyond it is honestly reported as not found
     * (returns false, position unchanged).
     *
     * Honesty: the id-lookup is fingerprint-agnostic, so re-selecting an
     * item whose fingerprint was already consumed earlier could re-hand it.
     * The auto-sweep re-positions to the LAST transmitted candidate, which
     * is never previously consumed.
     */
    override fun selectById(candidateId: String): Boolean {
        // Search only already-loaded pages (non-suspend, called from UI).
        val end = minOf(maxOf(pageIndex, 0) + pager.maxCachedPages + 1, pager.pageCount)
        for (p in 0 until end) {
            val view = pageView(p) ?: continue
            val idx = view.indexOfFirst { it.id == candidateId }
            if (idx >= 0) {
                pageIndex = p
                pageItems = view
                itemIndex = idx
                consumed = p * pager.pageSize + idx + 1
                return true
            }
        }
        return false
    }

    /** Reset back to the beginning of the sweep. */
    override fun reset() {
        _state = CursorState.UNINITIALIZED
        pageIndex = -1
        pageItems = emptyList()
        itemIndex = 0
        consumed = 0
        seenFingerprints.clear()
    }

    // ── internals ─────────────────────────────────────────────

    private suspend fun loadNextPage(): Boolean {
        pageIndex++
        if (pageIndex >= pager.pageCount) return false
        pageItems = indexedPage(pageIndex)
        itemIndex = 0
        return true
    }

    /** Filter + cross-page dedup (optional) + local page re-rank (cursor view). */
    private suspend fun indexedPage(index: Int): List<IrCodeSet> {
        val page = pager.page(index)
        return page.asSequence()
            .filter { candidate -> candidate.commands.keys.any { it in probeKeys } }
            .let { seq ->
                if (deduplicateFingerprints) {
                    seq.filter { seenFingerprints.add(multiKeyFingerprint(it)) }
                } else seq
            }
            .sortedByDescending { score(it) }
            .toList()
    }

    /**
     * Phase A — multi-key fingerprint: the tuple of physical fingerprints of
     * every probe key the candidate exposes, in stable probe order. Two
     * candidates collapse ONLY when all their exposed probe keys are the same
     * physical emission. A candidate sharing VOLUME_UP but differing in MUTE
     * or POWER_TOGGLE is a real, distinct candidate.
     */
    private fun multiKeyFingerprint(candidate: IrCodeSet): String =
        probeKeys.joinToString("|") { key ->
            val signal = candidate.commands[key]
            if (signal != null) IrProbeEngine.fingerprintSignal(signal) else "∅"
        }

    /**
     * RC-12: count the candidates the sweep will REALLY hand out, using the
     * same multi-key filter and the same cross-page fingerprint dedup (when
     * enabled). Uses a throw-away fingerprint set so the real sweep state is
     * untouched. The page cache stays bounded (LRU), only fingerprints grow —
     * bounded by unique signals, never the raw candidate count.
     */
    private suspend fun dryRunUniqueCount(): Int {
        if (!deduplicateFingerprints) return pager.totalCount
        val seen = HashSet<String>()
        var unique = 0
        for (p in 0 until pager.pageCount) {
            for (candidate in pager.page(p)) {
                if (candidate.commands.keys.none { it in probeKeys }) continue
                if (seen.add(multiKeyFingerprint(candidate))) unique++
            }
        }
        return unique
    }

    /** Filter + local re-rank WITHOUT dedup (id-lookup view). Returns null if page not cached. */
    private fun pageView(index: Int): List<IrCodeSet>? =
        pager.getCachedPage(index)
            ?.asSequence()
            ?.filter { candidate -> candidate.commands.keys.any { it in probeKeys } }
            ?.sortedByDescending { score(it) }
            ?.toList()

    private fun score(candidate: IrCodeSet): Int =
        CandidateScorer.scoreCandidate(
            candidate,
            targetModel,
            penaltyScore = penaltyMap[candidate.id] ?: 0,
            successCount = successMap[candidate.id] ?: 0,
            failCount = failMap[candidate.id] ?: 0
        ).score

    /** Fingerprint accessor for tests. */
    fun fingerprintOf(signal: IrSignal): String = IrProbeEngine.fingerprintSignal(signal)
}