package com.elysium.nexus.fabric.hedging

import com.elysium.nexus.fabric.canonical.UniversalAction

/**
 * V06-P18: Mutation semantics — the single safety classification for actions.
 *
 * Every execution policy (hedging, blind retry, broadcast, macro recording,
 * replay confirmation) must ask ONE classifier, never a private per-file list.
 * (Before this: HedgedExecutor, InputRecorder and DisconnectNeutralizer each
 * maintained their own private boolean lists — the audit's PHASE 18 finding.)
 *
 * ## Classes
 *
 * - [IDEMPOTENT_SAFE]: repeating the action converges to the same state.
 *   Hedge + blind retry allowed.
 * - [NON_IDEMPOTENT]: a second execution is a user-visible change
 *   (VOLUME_UP…). Never hedge, never blind-retry.
 * - [DESTRUCTIVE]: high-consequence mutation. Never hedge, never repeat
 *   without confirmation. Today reached through [UniversalAction.Custom]
 *   keys on the destructive keyword list — no adapter invents new verbs
 *   without the dispatcher gating them first.
 */
enum class MutationSafety {
    IDEMPOTENT_SAFE,
    NON_IDEMPOTENT,
    DESTRUCTIVE
}

object MutationSemantics {

    /** Keys that mark a Custom action high-consequence. */
    private val DESTRUCTIVE_CUSTOM_KEYWORDS = listOf(
        "factory_reset", "reset", "wipe", "erase", "delete", "clear_all"
    )

    fun classify(action: UniversalAction): MutationSafety = when (action) {
        // Absorbing states: setting an absolute value converges.
        is UniversalAction.PowerOn,
        is UniversalAction.PowerOff,
        is UniversalAction.PowerToggle,
        is UniversalAction.Mute,
        is UniversalAction.MediaStop,
        is UniversalAction.Home,
        is UniversalAction.Back,
        is UniversalAction.Menu,
        is UniversalAction.SetVolume,
        is UniversalAction.SetTemperature,
        is UniversalAction.SetFanSpeed,
        is UniversalAction.SetMode,
        is UniversalAction.InputSelect -> MutationSafety.IDEMPOTENT_SAFE

        // Incremental / transport / navigation: double execution = visible.
        is UniversalAction.VolumeUp,
        is UniversalAction.VolumeDown,
        is UniversalAction.ChannelUp,
        is UniversalAction.ChannelDown,
        is UniversalAction.MediaPlay,
        is UniversalAction.MediaPause,
        is UniversalAction.MediaNext,
        is UniversalAction.MediaPrevious,
        is UniversalAction.Navigate,
        is UniversalAction.Ok -> MutationSafety.NON_IDEMPOTENT

        is UniversalAction.Custom ->
            if (isDestructiveCustom(action)) MutationSafety.DESTRUCTIVE
            else MutationSafety.NON_IDEMPOTENT
    }

    /** Hedge only absorbing-state actions. */
    fun canHedge(action: UniversalAction): Boolean =
        classify(action) == MutationSafety.IDEMPOTENT_SAFE

    /** Blind repeat (no state confirmation) only for absorbing states. */
    fun canRepeatWithoutConfirmation(action: UniversalAction): Boolean =
        classify(action) == MutationSafety.IDEMPOTENT_SAFE

    /**
     * High-consequence recording policy (macro capture guards).
     * Conservative superset of the execution taxonomy: power-off, HVAC mode
     * changes and unknown custom verbs need explicit confirmation in
     * replays even though some are idempotent — consequence, not
     * idempotency, is the axis here.
     */
    fun requiresConfirmation(action: UniversalAction): Boolean = when (action) {
        is UniversalAction.Custom -> true
        is UniversalAction.PowerOff -> true
        is UniversalAction.SetMode -> true
        else -> classify(action) == MutationSafety.DESTRUCTIVE
    }

    private fun isDestructiveCustom(action: UniversalAction.Custom): Boolean {
        val key = action.key.lowercase()
        return DESTRUCTIVE_CUSTOM_KEYWORDS.any { keyword ->
            key == keyword || key.endsWith("_$keyword")
        }
    }
}