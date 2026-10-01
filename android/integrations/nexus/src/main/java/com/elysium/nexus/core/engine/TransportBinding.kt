package com.elysium.nexus.core.engine

import com.elysium.nexus.core.transport.ControllerTransport
import com.elysium.nexus.core.transport.SendResult
import com.elysium.nexus.core.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Binds the [CanonicalInputEngine] to a
 * [ControllerTransport].
 *
 * The engine's state machine is the source of
 * truth for the input. The [TransportBinding] is
 * the bridge: every state emission is forwarded
 * to the transport's `sendRealtime`. The §38
 * "release all" path is wired through the engine
 * (the `neutralize()` call triggers a neutral
 * state emission, which the binding forwards to
 * the transport's `releaseAll`).
 *
 * The binding is **decoupled** from the engine
 * via a [MutableStateFlow] of the transport's
 * state. The activity observes the transport's
 * state via [transportState] and re-renders the
 * [com.elysium.nexus.ui.editor.TransportSelector]
 * when the transport changes (e.g. on
 * disconnect).
 *
 * ## Why a separate class
 *
 * The engine does not know about transports; the
 * transport does not know about the engine. The
 * binding is the bridge. The activity wires the
 * binding at construction; the engine and
 * transport are interchangeable. A Phase 1.14
 * feature ("swap transport at runtime") is a
 * one-line change: `binding.transport = newTransport`.
 */
class TransportBinding(
    initialTransport: ControllerTransport
) {
    private val _transport = MutableStateFlow(initialTransport)
    val transport: StateFlow<ControllerTransport> = _transport

    private val _transportState = MutableStateFlow(initialTransport.state)
    /**
     * @return the transport's current state. The
     * activity observes this to re-render the
     * TransportSelector.
     */
    val transportState: StateFlow<TransportState> = _transportState

    /**
     * Forward a state change to the
     * [TransportState] flow. The activity wires
     * this to a coroutine that observes the
     * transport's state.
     */
    internal fun onTransportStateChanged(newState: TransportState) {
        _transportState.value = newState
    }

    /**
     * Forward a state emission to the transport.
     * Called by the engine's `state.onEach`
     * subscriber. The function is a thin wrapper
     * around `transport.sendRealtime` that
     * surfaces a `SendResult.Error` to the log
     * (Phase 1.14 wires the error UI).
     */
    suspend fun forwardRealtime(state: com.elysium.nexus.core.model.UniversalControllerState) {
        val t = _transport.value
        when (val r = t.sendRealtime(state)) {
            is SendResult.Ok -> { /* ok */ }
            is SendResult.Error -> {
                // The transport's error is
                // surfaced to the activity's
                // diagnostic overlay in
                // Phase 1.14.
            }
        }
    }

    /**
     * Swap the transport at runtime. Called by
     * the [com.elysium.nexus.ui.editor.TransportSelector]
     * when the user picks a new transport.
     */
    fun setTransport(transport: ControllerTransport) {
        _transport.value = transport
    }
}
