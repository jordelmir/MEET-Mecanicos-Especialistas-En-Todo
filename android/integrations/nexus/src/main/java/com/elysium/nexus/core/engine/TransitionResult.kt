package com.elysium.nexus.core.engine

import com.elysium.nexus.core.model.UniversalControllerState

/**
 * The result of a state-machine transition.
 *
 * Per `MASTER_ORDER.md` §32, every transition out of `Active` (and
 * arguably every transition, period) emits a neutral frame. The
 * engine captures that in the [state] field of [Transitioned].
 */
sealed class TransitionResult {

    /**
     * The engine transitioned from [from] to [to]. The
     * post-transition neutral state is [state] — useful for
     * tests and for the transport layer to log.
     */
    data class Transitioned(
        val from: EngineState,
        val to: EngineState,
        val state: UniversalControllerState
    ) : TransitionResult()

    /**
     * The engine was already in [state], so the transition
     * was a no-op. The canonical state is unchanged.
     */
    data class NoOp(val state: EngineState) : TransitionResult()
}
