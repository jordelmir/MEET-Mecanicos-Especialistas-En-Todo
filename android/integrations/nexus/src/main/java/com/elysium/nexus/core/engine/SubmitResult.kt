package com.elysium.nexus.core.engine

import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.model.ValidationError

/**
 * The result of submitting an input sample to the engine.
 *
 * The engine is strict by design: it does not silently
 * normalize or "best-effort" a bad input. A rejection is a
 * release-blocker event the producer has to fix.
 */
sealed class SubmitResult {

    /**
     * The input was accepted. The engine's [CanonicalInputEngine.state]
     * is now the [state] value, and the [UniversalControllerState.sequence]
     * of that state is the new monotonic sequence number.
     */
    data class Accepted(val state: UniversalControllerState) : SubmitResult()

    /**
     * The input was rejected because the candidate canonical
     * state failed §9 validation. The engine's state is
     * unchanged. The producer should fix the input and
     * re-submit.
     */
    data class Rejected(
        val attempted: UniversalControllerState,
        val errors: List<ValidationError>
    ) : SubmitResult()

    /**
     * The input was rejected because the engine is not in a
     * state that accepts it. Per §32, only [EngineState.Active]
     * is a non-neutral emitting state; in every other state, the
     * engine holds the canonical state at neutral and rejects
     * incoming input.
     *
     * The [state] is the current engine state, so the caller
     * can decide whether to buffer the input (and replay it on
     * transition to Active) or drop it.
     */
    data class WrongStateMachine(
        val state: EngineState,
        val reason: String
    ) : SubmitResult()
}
