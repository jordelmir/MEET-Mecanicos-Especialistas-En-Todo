package com.elysium369.meet.core.agent.ui

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E V A I R   U I   R E G I S T R Y
 *  ──────────────────────────────────────────────────────────────
 *  Registro global, thread-safe y de alta velocidad de todos los
 *  controles interactivos visibles en la pantalla actual.
 *  Proporciona a EVAIR la percepción determinista del mundo visual.
 * ══════════════════════════════════════════════════════════════════════
 */
class AgentUiRegistry internal constructor() {

    private val controls = ConcurrentHashMap<AgentUiControlId, RegisteredAgentUiControl>()
    private val generationCounter = AtomicLong(0L)

    private val _visibleControls = MutableStateFlow<List<AgentUiControlSnapshot>>(emptyList())
    val visibleControls: StateFlow<List<AgentUiControlSnapshot>> = _visibleControls.asStateFlow()

    internal fun register(control: RegisteredAgentUiControl) {
        val nextGen = generationCounter.incrementAndGet()
        val stamped = control.copy(
            snapshot = control.snapshot.copy(generation = nextGen)
        )
        controls[control.snapshot.id] = stamped
        publish()
    }

    fun unregister(id: AgentUiControlId) {
        controls.remove(id)
        publish()
    }

    fun updateBounds(id: AgentUiControlId, bounds: Rect, visible: Boolean) {
        controls.computeIfPresent(id) { _, current ->
            if (current.snapshot.bounds == bounds && current.snapshot.visible == visible) {
                return@computeIfPresent current
            }
            val nextGen = generationCounter.incrementAndGet()
            current.copy(
                snapshot = current.snapshot.copy(
                    bounds = bounds,
                    visible = visible,
                    generation = nextGen
                )
            )
        }
        publish()
    }

    fun updateFocus(id: AgentUiControlId, focused: Boolean) {
        controls.computeIfPresent(id) { _, current ->
            if (current.snapshot.focused == focused) current else current.copy(
                snapshot = current.snapshot.copy(focused = focused, generation = generationCounter.incrementAndGet()))
        }
        publish()
    }

    internal fun current(id: AgentUiControlId): RegisteredAgentUiControl? = controls[id]

    /**
     * Snapshot instantáneo de todos los controles accionables visibles en pantalla.
     */
    fun snapshot(): List<AgentUiControlSnapshot> {
        return controls.values
            .map { it.snapshot }
            .filter { it.isActionable }
    }

    /**
     * Devuelve todos los campos de texto activos para dictado por voz.
     */
    fun activeTextFields(): List<AgentUiControlSnapshot> {
        return snapshot().filter {
            it.kind == AgentUiControlKind.TEXT_FIELD || it.kind == AgentUiControlKind.SEARCH_FIELD
        }
    }

    fun clear() {
        controls.clear()
        publish()
    }

    private fun publish() {
        _visibleControls.value = snapshot()
    }

    companion object {
        val default: AgentUiRegistry by lazy { AgentUiRegistry() }
    }
}
