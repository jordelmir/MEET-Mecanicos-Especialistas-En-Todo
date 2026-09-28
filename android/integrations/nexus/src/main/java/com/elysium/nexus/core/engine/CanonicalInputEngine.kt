package com.elysium.nexus.core.engine

import com.elysium.nexus.core.filter.StickConfig
import com.elysium.nexus.core.filter.StickFilters
import com.elysium.nexus.core.latency.LatencyTracker
import com.elysium.nexus.core.model.BatteryState
import com.elysium.nexus.core.model.ButtonSet
import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.model.DpadState
import com.elysium.nexus.core.model.MotionState
import com.elysium.nexus.core.model.StickState
import com.elysium.nexus.core.model.TouchCollection
import com.elysium.nexus.core.model.TouchPoint
import com.elysium.nexus.core.model.TriggerState
import com.elysium.nexus.core.model.UniversalControllerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * The canonical input engine.
 *
 * Owns the single source of truth for the controller's input
 * state. Every surface (touch, motion, button, trigger) feeds
 * raw samples in; the engine validates against `MASTER_ORDER.md`
 * §9, applies the §12 stick filter pipeline, assigns a
 * monotonic sequence and timestamp, and emits the resulting
 * [UniversalControllerState] to a [StateFlow] for downstream
 * consumers (the transport, the UI, the telemetry panel).
 *
 * The engine also drives the §32 state machine. Every transition
 * emits a neutral frame, per §38 (the disconnect test). The
 * neutralization is a property of the engine, not a property of
 * the surface, so a buffer overflow, a process kill, or a
 * transport drop all leave the host in a known-zero state
 * without the surface having to do anything.
 *
 * ## Why a single class
 *
 * The engine is the *only* writer of the canonical state. If
 * there were two writers, the §9 invariants (sequence monotonic,
 * timestamp monotonic, no regressed values) would require a
 * cross-writer lock. A single writer is one less problem to
 * solve and the cost of a `MutableStateFlow` is one atomic
 * reference swap.
 *
 * ## Why inject a `CoroutineScope` and a clock
 *
 * The engine does **not** own its scope. The scope is owned by
 * the service that holds the engine (Phase 1+). The service's
 * lifecycle is the engine's lifecycle: when the service stops,
 * the scope cancels, and the engine's StateFlow is collected
 * one last time and discarded. The injected `clock` is a
 * `(Unit) -> ULong` lambda so tests can pin the timestamp to
 * a deterministic value. Production wires it to `System.nanoTime()`.
 *
 * Per §31: no `GlobalScope`, no `unwrap()`. The engine never
 * starts a coroutine of its own. It mutates the StateFlow
 * directly; downstream collectors (the transport, the UI) are
 * the ones that live in the injected scope.
 */
class CanonicalInputEngine(
    leftStickConfig: StickConfig,
    rightStickConfig: StickConfig,
    @Suppress("unused") // reserved for future engine-internal jobs (0.5+)
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val clock: () -> ULong = { System.nanoTime().toULong() },
    /**
     * Optional latency tracker. When present, every
     * `submit*` call that carries a `t0Ns` parameter
     * records the diff between T2 (the commit time) and
     * T0 (the platform-level timestamp the caller saw)
     * into the tracker. This is the §30 latency budget's
     * first measurement.
     *
     * `null` disables latency tracking — useful for
     * unit tests that do not need it. Production wires a
     * tracker so the activity can log p50/p95 to
     * logcat.
     */
    private val latencyTracker: LatencyTracker? = null
) {
    // The per-side stick configurations are
    // *mutable* (Phase 1.23+): the activity wires the
    // §15 settings to the engine and a settings
    // change updates the config in place. The fields
    // are guarded by a `ReentrantReadWriteLock`: the
    // filter pipeline reads on every `submitStick`,
    // and a settings change writes once. Reads are
    // cheap; a `synchronized` block would also work
    // but a `ReentrantReadWriteLock` is the right
    // shape for read-heavy / write-rare access.
    private val stickConfigLock = java.util.concurrent.locks.ReentrantReadWriteLock()
    private var leftStickConfig: StickConfig = leftStickConfig
    private var rightStickConfig: StickConfig = rightStickConfig

    private val _state: MutableStateFlow<UniversalControllerState> =
        MutableStateFlow(neutral(sequence = 0uL))

    /** The current canonical state. Hot; always emits the latest. */
    val state: StateFlow<UniversalControllerState> = _state.asStateFlow()

    private val _engineState: MutableStateFlow<EngineState> =
        MutableStateFlow(EngineState.Idle)

    /** The current §32 state machine state. Hot; always emits the
     *  latest. */
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    /**
     * The next sequence number the engine will assign. The
     * engine is the only writer; sequence is guaranteed to be
     * strictly monotonic across the lifetime of the engine.
     */
    private var nextSequence: ULong = 0uL

    /**
     * The highest timestamp the engine has emitted. Used to
     * ensure timestamp monotonicity even when the wall clock
     * jumps backwards (a real risk on Android when the user
     * changes the system clock or NTP corrects).
     */
    private var lastTimestampNs: ULong = 0uL

    /**
     * The current §15 left-stick configuration. Hot
     * read; cheap under [stickConfigLock]. The
     * editor's settings dialog is the only caller
     * of [updateStickConfig]; the filter pipeline
     * is the only reader of [leftStickConfig].
     */
    fun currentStickConfig(side: StickSide): StickConfig {
        val readLock = stickConfigLock.readLock()
        readLock.lock()
        try {
            return if (side == StickSide.Left) leftStickConfig else rightStickConfig
        } finally {
            readLock.unlock()
        }
    }

    /**
     * Update the §15 stick configuration for [side].
     * The new configuration is used by the next
     * [submitStick] call. The function is total:
     * the [StickConfig]'s `init` block validates
     * the bounds; an out-of-range config throws
     * [IllegalArgumentException] before the
     * assignment.
     */
    fun updateStickConfig(side: StickSide, config: StickConfig) {
        // The `StickConfig` constructor validates
        // the input (the data class's `init`
        // block). If the config is invalid, the
        // throw happens before the lock; the
        // engine's state is unchanged.
        val writeLock = stickConfigLock.writeLock()
        writeLock.lock()
        try {
            if (side == StickSide.Left) {
                leftStickConfig = config
            } else {
                rightStickConfig = config
            }
        } finally {
            writeLock.unlock()
        }
    }

    // -- Stick and trigger submission --------------------------------

    /**
     * Submit a raw stick sample for [side]. The sample is run
     * through the §12 filter pipeline; the result is validated
     * against §9; on success, the canonical state is updated and
     * the new state is returned.
     *
     * Only allowed in [EngineState.Active]. In every other
     * state, the engine returns [SubmitResult.WrongStateMachine]
     * without mutating the canonical state.
     */
    fun submitStick(side: StickSide, raw: StickState): SubmitResult {
        if (!_engineState.value.isActive()) {
            return SubmitResult.WrongStateMachine(
                state = _engineState.value,
                reason = "stick submission requires Active"
            )
        }
        val filtered = StickFilters.apply(
            raw = raw,
            config = currentStickConfig(side)
        )
        val current = _state.value
        val candidate = when (side) {
            StickSide.Left -> current.copy(leftStick = filtered)
            StickSide.Right -> current.copy(rightStick = filtered)
        }
        return commit(candidate)
    }

    /**
     * Submit a raw trigger sample for [side]. Triggers are
     * not filtered in 0.4 (the §13 hair-trigger / curve
     * pipeline lands in 0.6); the engine validates and
     * commits the raw value.
     */
    fun submitTrigger(side: StickSide, raw: TriggerState): SubmitResult {
        if (!_engineState.value.isActive()) {
            return SubmitResult.WrongStateMachine(
                state = _engineState.value,
                reason = "trigger submission requires Active"
            )
        }
        val current = _state.value
        val candidate = when (side) {
            StickSide.Left -> current.copy(leftTrigger = raw)
            StickSide.Right -> current.copy(rightTrigger = raw)
        }
        return commit(candidate)
    }

    // -- Button, dpad, touch, motion, battery submission -------------

    /**
     * Press or release a button. The engine updates the
     * [ButtonSet] in the canonical state and commits.
     */
    fun submitButton(button: CanonicalButton, pressed: Boolean): SubmitResult {
        if (!_engineState.value.isActive()) {
            return SubmitResult.WrongStateMachine(
                state = _engineState.value,
                reason = "button submission requires Active"
            )
        }
        val current = _state.value
        val newButtons = current.buttons.with(button, pressed)
        return commit(current.copy(buttons = newButtons))
    }

    /**
     * Set the D-pad to [state]. D-pad is digital, so a single
     * integer / enum is the whole sample.
     */
    fun submitDpad(state: DpadState): SubmitResult {
        if (!_engineState.value.isActive()) {
            return SubmitResult.WrongStateMachine(
                state = _engineState.value,
                reason = "dpad submission requires Active"
            )
        }
        return commit(_state.value.copy(dpad = state))
    }

    /**
     * Replace the touch collection. The engine validates the
     * collection's per-point invariants and the cap.
     */
    fun submitTouches(collection: TouchCollection): SubmitResult {
        if (!_engineState.value.isActive()) {
            return SubmitResult.WrongStateMachine(
                state = _engineState.value,
                reason = "touches submission requires Active"
            )
        }
        return commit(_state.value.copy(touches = collection))
    }

    /**
     * Add, replace, or remove a single touch point. The engine
     * looks up the existing point by [TouchPoint.id] and
     * either updates it (if found) or appends it (if not).
     * Removing a touch is signaled by `null`.
     *
     * [t0Ns] is the platform-level timestamp at which the
     * underlying `MotionEvent` was delivered (the §30 T0).
     * The engine records the diff between T2 (the commit
     * time) and T0 into the [latencyTracker] if one is
     * configured. `null` disables the measurement for this
     * call (used by the engine-internal transitions, which
     * do not have a T0).
     *
     * 0.4 ships this as the most direct shape the touch surface
     * will use in 0.5.
     */
    fun submitTouchPoint(
        id: Int,
        point: TouchPoint?,
        t0Ns: Long? = null
    ): SubmitResult {
        if (!_engineState.value.isActive()) {
            return SubmitResult.WrongStateMachine(
                state = _engineState.value,
                reason = "touches submission requires Active"
            )
        }
        val current = _state.value
        val existing = current.touches.points
        val next = if (point == null) {
            existing.filterNot { it.id == id }
        } else {
            val replaced = existing.any { it.id == id }
            if (replaced) {
                existing.map { if (it.id == id) point else it }
            } else {
                if (existing.size >= TouchCollection.MAX_TOUCHES) {
                    // Cap hit; reject the add. The surface should
                    // never produce this; the cap exists to
                    // protect the engine from a runaway producer.
                    // We synthesise a typed rejection without ever
                    // constructing an oversize collection.
                    return SubmitResult.Rejected(
                        attempted = current,
                        errors = listOf(
                            com.elysium.nexus.core.model.ValidationError.FrameTooLarge(
                                field = "touches",
                                actual = existing.size + 1,
                                max = TouchCollection.MAX_TOUCHES
                            )
                        )
                    )
                }
                existing + point
            }
        }
        return commit(current.copy(touches = TouchCollection(next)), t0Ns)
    }

    /**
     * Replace the motion state, or remove it (with `null`).
     */
    fun submitMotion(motion: MotionState?): SubmitResult {
        if (!_engineState.value.isActive()) {
            return SubmitResult.WrongStateMachine(
                state = _engineState.value,
                reason = "motion submission requires Active"
            )
        }
        return commit(_state.value.copy(motion = motion))
    }

    /**
     * Replace the battery state, or remove it (with `null`).
     */
    fun submitBattery(battery: BatteryState?): SubmitResult {
        if (!_engineState.value.isActive()) {
            return SubmitResult.WrongStateMachine(
                state = _engineState.value,
                reason = "battery submission requires Active"
            )
        }
        return commit(_state.value.copy(battery = battery))
    }

    // -- State machine -----------------------------------------------

    /**
     * Transition the engine to [newState]. Per §32 / §38,
     * every transition emits a neutral frame: the canonical
     * state is reset to its neutral defaults before the
     * transition is reported.
     *
     * The neutralization is unconditional: even a
     * `Connected → Active` transition (which is a "starting to
     * emit" transition, not a "stopping" one) emits a
     * neutral frame first, so the very first frame the
     * transport sees is the disconnect-target.
     *
     * @return [TransitionResult.Transitioned] on a real
     *   transition, [TransitionResult.NoOp] if the engine was
     *   already in [newState].
     */
    fun transitionTo(newState: EngineState): TransitionResult {
        val previous = _engineState.value
        if (previous == newState) {
            return TransitionResult.NoOp(previous)
        }
        _engineState.value = newState
        val seq = nextSequence
        val postNeutral = neutral(sequence = seq)
        // Bump the sequence so a subsequent submission produces
        // a strictly later number than the post-transition
        // neutral frame. Without this, the first post-transition
        // submission would re-use the same sequence the
        // transition frame carried.
        nextSequence = seq + 1uL
        _state.value = postNeutral
        return TransitionResult.Transitioned(
            from = previous,
            to = newState,
            state = postNeutral
        )
    }

    /**
     * Force-neutralize the canonical state without changing
     * the engine state. Useful for the transport layer to call
     * on a perceived disconnect while the state-machine
     * transition is in flight.
     */
    fun neutralize() {
        _state.value = consumeNeutral()
    }

    // -- Internals ---------------------------------------------------

    /**
     * The current sequence counter (read-only). Tests use this to
     * pin the engine's progress through a scenario.
     */
    fun sequence(): ULong = nextSequence

    /**
     * The current engine state (read-only). Convenience accessor
     * for callers that already have the engine reference but
     * not the StateFlow.
     */
    fun engineStateValue(): EngineState = _engineState.value

    /**
     * Build a neutral state with a fresh sequence and a
     * timestamp that is guaranteed to be strictly greater than
     * the last emitted timestamp.
     */
    private fun neutral(sequence: ULong): UniversalControllerState {
        val ts = monotonicTimestamp()
        return UniversalControllerState.neutral(
            sequence = sequence,
            timestampNs = ts
        )
    }

    /**
     * Like [neutral] but also bumps [nextSequence] so the
     * returned state's sequence is consumed. Used by
     * [neutralize] which fires from outside the state machine.
     */
    private fun consumeNeutral(): UniversalControllerState {
        val seq = nextSequence
        nextSequence = seq + 1uL
        return neutral(sequence = seq)
    }

    /**
     * Compute the next timestamp. The engine is the only writer
     * of timestamps; this function guarantees that the returned
     * value is strictly greater than the previously emitted
     * one, even if the wall clock jumps backwards.
     */
    private fun monotonicTimestamp(): ULong {
        val now = clock()
        val next = if (now > lastTimestampNs) now else lastTimestampNs + 1uL
        lastTimestampNs = next
        return next
    }

    /**
     * Validate the candidate state, assign a fresh sequence
     * and timestamp, and commit it. On validation failure,
     * the canonical state is unchanged and the rejection is
     * returned.
     *
     * [t0Ns] is the §30 T0 timestamp, when present. The
     * engine records the diff between T2 (the commit time)
     * and T0 into the [latencyTracker] if one is configured.
     * The recording happens *after* validation so a
     * rejected submission does not pollute the latency
     * percentiles.
     */
    private fun commit(
        candidate: UniversalControllerState,
        t0Ns: Long? = null
    ): SubmitResult {
        val seq = nextSequence
        val ts = monotonicTimestamp()
        val next = candidate.copy(sequence = seq, timestampNs = ts)
        when (val r = UniversalControllerState.validate(next)) {
            is com.elysium.nexus.core.model.ValidationResult.Valid -> {
                _state.value = next
                // Only bump the public sequence *after* the
                // commit succeeds, so a rejected submission does
                // not consume a sequence number.
                nextSequence = max(nextSequence, seq) + 1uL
                // Record the per-event latency into the
                // tracker, if any. The diff is in nanoseconds.
                if (latencyTracker != null && t0Ns != null) {
                    val t2 = ts.toLong()
                    val diff = t2 - t0Ns
                    latencyTracker.record(if (diff < 0) 0L else diff)
                }
                return SubmitResult.Accepted(next)
            }
            is com.elysium.nexus.core.model.ValidationResult.Invalid -> {
                return SubmitResult.Rejected(next, r.errors)
            }
        }
    }
}
