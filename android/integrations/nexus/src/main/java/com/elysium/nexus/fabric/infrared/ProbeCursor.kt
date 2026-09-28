package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrCodeSet

/**
 * V0.6.3 Phase 2: Engine initialization state machine.
 *
 * Every ProbeCursor MUST be initialized before use. Calling
 * [currentCandidate] or [nextCandidate] on an UNINITIALIZED cursor
 * is undefined. After [ProbeCursor.initialize] returns [CursorState.READY],
 * [currentCandidate] MUST NOT return null (enforced by contract).
 */
enum class CursorState {
    /** Engine not yet initialized. Must call initialize() first. */
    UNINITIALIZED,
    /** Engine ready — currentCandidate is non-null (guaranteed). */
    READY,
    /** All candidates exhausted. currentCandidate is null. */
    EXHAUSTED
}

/**
 * Result of [ProbeCursor.initialize].
 */
sealed interface CursorInitResult {
    /** Engine ready with at least one candidate loaded. */
    data class Ready(val engine: ProbeCursor) : CursorInitResult
    /** No candidates match the sweep criteria. */
    data object NoCandidates : CursorInitResult
    /** Database or pager error during initialization. */
    data class Error(val reason: String) : CursorInitResult
}

/**
 * V0.6.2 PR3 Phase 14, V0.6.3 Phase 2 — Cursor over ranked IR probe candidates.
 *
 * Both engines ([IrProbeEngine] eager, [PagedIrProbeEngine] bounded-memory)
 * expose the same cursor contract, so the product probe flow can switch
 * between them without UI changes.
 *
 * V0.6.3 Phase 2: Added [initialize] method. Engines MUST NOT expose
 * a null [currentCandidate] when in READY state. The UI must call
 * [initialize] first and only proceed on READY.
 *
 * Honesty contract:
 * - [nextCandidate] advances by ONE ranked candidate, never skipping.
 * - [selectById] re-positions by exact identity; a miss must NOT silently
 *   select candidate 0 (callers guard via [ProbeRestoreResolver]).
 */
interface ProbeCursor {
    /** Current engine state. */
    val state: CursorState

    /** Total candidate count (source-level). */
    val totalCandidates: Int

    /** 1-based probe number of the current candidate (for UI "X de Y"). */
    val currentProbeNumber: Int

    /** True while more candidates remain after the current one. */
    val hasMore: Boolean

    /**
     * Currently selected candidate.
     * Returns null ONLY when state is [CursorState.EXHAUSTED].
     * Pre-condition: state MUST be READY (call [initialize] first).
     */
    fun currentCandidate(): IrCodeSet?

    /**
     * Initialize the engine: load the first page, filter, dedup, rank.
     * MUST return [CursorState.READY] with currentCandidate != null
     * when candidates exist. MUST NOT block the UI thread.
     */
    suspend fun initialize(): CursorInitResult

    /** Advance to the next ranked candidate; null when exhausted. */
    suspend fun nextCandidate(): IrCodeSet?

    /** Re-position by exact candidate ID. Returns false when not found. */
    fun selectById(candidateId: String): Boolean

    /** Reset to the beginning of the sweep. */
    fun reset()
}