package com.elysium369.meet.core.agent.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

sealed interface UiSelectionResult {
    data class Success(val controlId: AgentUiControlId, val label: String) : UiSelectionResult
    data class Navigated(val route: String, val label: String) : UiSelectionResult
    data class Ambiguous(val query: String, val candidates: List<UiTargetMatch>) : UiSelectionResult
    data class NotFound(val query: String) : UiSelectionResult
    data class Failed(val code: String) : UiSelectionResult
    object Stale : UiSelectionResult
}

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E V A I R   U I   I N T E R A C T I O N   C O O R D I N A T O R
 *  ──────────────────────────────────────────────────────────────
 *  Orquesta el ciclo de interacción semántica completo:
 *  Resolución -> Teleportación Instantánea -> Verificación Stale ->
 *  Activación del Callback Real -> Retorno al Home Pose.
 * ══════════════════════════════════════════════════════════════════════
 */
class EvairUiInteractionCoordinator(
    private val registry: AgentUiRegistry = AgentUiRegistry.default,
    private val resolver: UiTargetResolver = UiTargetResolver(),
    private val motionPort: CompanionMotionPort? = null,
) {

    suspend fun select(
        targetText: String,
        motion: CompanionMotionPort? = motionPort,
    ): UiSelectionResult {
        val snapshot = registry.snapshot()
        val resolution = resolver.resolve(targetText, snapshot)

        when (resolution) {
            is UiTargetResolutionResult.NotFound -> {
                return UiSelectionResult.NotFound(targetText)
            }
            is UiTargetResolutionResult.Ambiguous -> {
                return UiSelectionResult.Ambiguous(targetText, resolution.candidates)
            }
            is UiTargetResolutionResult.Exact -> {
                return activate(resolution.match.control, motion)
            }
        }
    }

    suspend fun selectControl(id: AgentUiControlId, expectedGeneration: Long, motion: CompanionMotionPort? = motionPort): UiSelectionResult {
        val control = registry.current(id)?.snapshot ?: return UiSelectionResult.Stale
        if (control.generation != expectedGeneration) return UiSelectionResult.Stale
        return activate(control, motion)
    }

    private suspend fun activate(control: AgentUiControlSnapshot, motion: CompanionMotionPort?): UiSelectionResult {
        try {
            motion?.teleportTo(control.bounds)
            val current = registry.current(control.id)
            if (current == null || current.snapshot.generation != control.generation ||
                !current.snapshot.isActionable || current.activate == null) return UiSelectionResult.Stale
            motion?.tapPulse()
            val activated = withContext(Dispatchers.Main.immediate) {
                val ready = registry.current(control.id)
                if (ready == null || ready.snapshot.generation != control.generation ||
                    !ready.snapshot.isActionable || ready.activate == null) false
                else { ready.activate.invoke(); true }
            }
            if (!activated) return UiSelectionResult.Stale
            delay(180)
            return UiSelectionResult.Success(control.id, control.label)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return UiSelectionResult.Failed("UI_ACTIVATION_FAILED")
        } finally {
            withContext(NonCancellable) { runCatching { motion?.returnHome() } }
        }
    }

    suspend fun enterSection(
        targetText: String,
        motion: CompanionMotionPort? = motionPort,
        onNavigate: (String) -> Unit,
    ): UiSelectionResult {
        // 1. Primero intentar encontrar un control o tab visible en la pantalla activa
        val visibleSnapshot = registry.snapshot()
        val visibleMatch = resolver.resolve(targetText, visibleSnapshot)
        if (visibleMatch is UiTargetResolutionResult.Ambiguous) {
            return UiSelectionResult.Ambiguous(targetText, visibleMatch.candidates)
        }
        if (visibleMatch is UiTargetResolutionResult.Exact) {
            return select(targetText, motion)
        }

        // 2. Si no está en pantalla, consultar el catálogo canónico de navegación
        val canonicalEntry = NavigationCatalog.findByQuery(targetText)
        if (canonicalEntry != null) {
            try {
                motion?.tapPulse()
                withContext(Dispatchers.Main.immediate) { onNavigate(canonicalEntry.route) }
                return UiSelectionResult.Navigated(canonicalEntry.route, canonicalEntry.label)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return UiSelectionResult.Failed("UI_NAVIGATION_FAILED")
            } finally {
                withContext(NonCancellable) { runCatching { motion?.returnHome() } }
            }
        }

        return UiSelectionResult.NotFound(targetText)
    }
}
