package com.elysium369.meet.core.agent.ui

import com.elysium369.meet.core.agent.capability.CapabilityRegistry
import com.elysium369.meet.core.agent.context.AgentContextProvider
import com.elysium369.meet.core.agent.context.AgentContextSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.elysium369.meet.core.agent.domain.AgentInput
import com.elysium369.meet.core.agent.domain.AgentPlan
import com.elysium369.meet.core.agent.domain.InputSource
import com.elysium369.meet.core.agent.intent.DeterministicIntentResolver
import com.elysium369.meet.core.agent.intent.IntentResolver
import com.elysium369.meet.core.agent.planning.AgentPlanCompiler
import com.elysium369.meet.core.agent.planning.PlanCompilationResult
import com.elysium369.meet.core.audio.VoiceTranscriptEvent
import com.elysium369.meet.ui.navigation.SemanticUiGraph

sealed interface EvairInteractionResult {
    data class MagicSelectExecuted(val controlId: AgentUiControlId, val label: String) : EvairInteractionResult
    data class MagicSectionNavigated(val route: String, val label: String) : EvairInteractionResult
    data class MagicAmbiguous(val query: String, val candidates: List<UiTargetMatch>) : EvairInteractionResult
    data class MagicNotFound(val query: String) : EvairInteractionResult
    data class FormBound(val fieldId: AgentUiControlId, val text: String) : EvairInteractionResult
    data class MultiSlotFormBound(val slots: Map<AgentUiControlId, String>) : EvairInteractionResult
    data class FormRejected(val message: String) : EvairInteractionResult
    data class GoalDeferred(val plan: AgentPlan, val message: String) : EvairInteractionResult
    data class Unhandled(val text: String, val unavailableMessage: String? = null) : EvairInteractionResult
}

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E V A I R   I N T E R A C T I O N   O R C H E S T R A T O R
 *  ──────────────────────────────────────────────────────────────
 *  Cerebro unificado de interacción de EVAIR.
 *  Canaliza la voz del usuario en orden de prioridad estricto:
 *  1. Comandos Mágicos deterministas ("SELECCIONA", "SECCIÓN")
 *  2. Form Binding (dictado inteligente en campos visibles)
 *  3. Grafo Semántico de Navegación (SemanticUiGraph)
 *  4. Resolución de intención y compilación con el registro de capacidades actual
 *  5. Fallback conversacional o no manejado
 * ══════════════════════════════════════════════════════════════════════
 */
class EvairInteractionOrchestrator(
    private val uiCoordinator: EvairUiInteractionCoordinator = EvairUiInteractionCoordinator(),
    private val formBinder: VoiceFormBinder = VoiceFormBinder(),
    private val semanticUiGraph: SemanticUiGraph = SemanticUiGraph.createDefault(),
    private val contextProvider: AgentContextProvider? = null,
    private val capabilityRegistry: CapabilityRegistry = CapabilityRegistry.default,
    private val intentResolver: IntentResolver = DeterministicIntentResolver(),
) {
    private val commandMutex = Mutex()
    private val handledUtterances = LinkedHashSet<String>()
    private var pendingOptions: List<UiTargetMatch>? = null
    private var pendingContext: AgentContextSnapshot? = null
    private val choiceRegex = Regex("""^(?:(?:selecciona|elige)\s+)?(?:(?:el|la)\s+)?(?:opcion\s+)?(primero|primera|segundo|segunda|tercero|tercera|cuarto|cuarta|quinto|quinta|sexto|sexta|[1-9])$""")

    private fun selectionResult(result: UiSelectionResult, query: String): EvairInteractionResult = when (result) {
        is UiSelectionResult.Success -> EvairInteractionResult.MagicSelectExecuted(result.controlId, result.label)
        is UiSelectionResult.Navigated -> EvairInteractionResult.MagicSectionNavigated(result.route, result.label)
        is UiSelectionResult.Ambiguous -> {
            pendingOptions = result.candidates
            pendingContext = contextProvider?.currentSnapshot()
            EvairInteractionResult.MagicAmbiguous(result.query, result.candidates)
        }
        is UiSelectionResult.NotFound -> EvairInteractionResult.MagicNotFound(result.query)
        UiSelectionResult.Stale -> EvairInteractionResult.FormRejected("El control cambió. Repite la orden en la pantalla actual.")
        is UiSelectionResult.Failed -> EvairInteractionResult.FormRejected("No pude activar el control. Puedes intentarlo desde la pantalla.")
    }

    private var bindingUtteranceId: String? = null
    private fun beginUtterance(id: String) {
        if (bindingUtteranceId != id) { formBinder.resetSession(); bindingUtteranceId = id }
    }

    suspend fun handlePartialTranscript(
        event: VoiceTranscriptEvent.Partial,
    ): VoiceBindingResult = commandMutex.withLock {
        if (event.utteranceId in handledUtterances) return@withLock VoiceBindingResult.NotApplicable
        beginUtterance(event.utteranceId)
        if (MagicUiCommandParser.isCommandLike(event.text) || MagicUiCommandParser.isNegatedCommand(event.text)) return@withLock VoiceBindingResult.NotApplicable
        formBinder.bindPartial(event.text)
    }

    suspend fun handleFinalTranscript(
        event: VoiceTranscriptEvent.Final,
        motion: CompanionMotionPort? = null,
        onNavigate: (String) -> Unit = {},
    ): EvairInteractionResult = commandMutex.withLock {
        if (!handledUtterances.add(event.utteranceId)) return@withLock EvairInteractionResult.FormRejected("Ya procesé esa orden.")
        if (handledUtterances.size > 128) handledUtterances.remove(handledUtterances.first())
        processFinalTranscript(event, motion, onNavigate)
    }

    private suspend fun processFinalTranscript(
        event: VoiceTranscriptEvent.Final,
        motion: CompanionMotionPort? = null,
        onNavigate: (String) -> Unit = {},
    ): EvairInteractionResult {
        beginUtterance(event.utteranceId)
        val text = event.text.trim()
        if (text.isBlank()) return EvairInteractionResult.Unhandled(text)
        if (MagicUiCommandParser.isNegatedCommand(text)) return EvairInteractionResult.FormRejected("No ejecuté esa acción.")

        val options = pendingOptions
        if (options != null) {
            val choice = choiceRegex.matchEntire(UiTextNormalizer.normalize(text))?.groupValues?.get(1)
            if (choice != null) {
                val index = choice.toIntOrNull()?.minus(1) ?: when (choice) {
                    "primero", "primera" -> 0
                    "segundo", "segunda" -> 1
                    "tercero", "tercera" -> 2
                    "cuarto", "cuarta" -> 3
                    "quinto", "quinta" -> 4
                    else -> 5
                }
                val option = options.getOrNull(index)
                    ?: return EvairInteractionResult.FormRejected("Esa opción no está en la lista.")
                val before = pendingContext
                val now = contextProvider?.currentSnapshot()
                pendingOptions = null; pendingContext = null
                if (before == null || now == null || before.principalId != now.principalId ||
                    before.currentScreen != now.currentScreen || before.activeVehicleId != now.activeVehicleId ||
                    before.activeRideId != now.activeRideId || before.contextGeneration != now.contextGeneration) {
                    return EvairInteractionResult.FormRejected("El contexto cambió. Repite la orden en la pantalla actual.")
                }
                return selectionResult(uiCoordinator.selectControl(option.control.id, option.control.generation, motion), text)
            }
            pendingOptions = null; pendingContext = null
            if (UiTextNormalizer.normalize(text) in setOf("cancelar", "olvidalo", "ninguna")) {
                return EvairInteractionResult.FormRejected("Selección cancelada.")
            }
        }

        // 1. FAST PATH: Comandos Mágicos ("SELECCIONA", "SECCIÓN")
        val magicCommand = MagicUiCommandParser.parse(text)
        if (magicCommand != null) {
            when (magicCommand) {
                is MagicUiCommand.Select -> {
                    val selResult = uiCoordinator.select(magicCommand.targetText, motion)
                    return selectionResult(selResult, magicCommand.targetText)
                }
                is MagicUiCommand.Section -> {
                    val secResult = uiCoordinator.enterSection(magicCommand.targetText, motion, onNavigate)
                    return selectionResult(secResult, magicCommand.targetText)
                }
            }
        }

        // An incomplete command must not become message text or other form input.
        if (MagicUiCommandParser.isCommandLike(text)) {
            val screen = semanticUiGraph.resolveTargetScreen(text)
            if (screen != null) {
                onNavigate(screen.route)
                return EvairInteractionResult.MagicSectionNavigated(screen.route, screen.displayName)
            }
            return EvairInteractionResult.MagicNotFound(text)
        }

        // 2. Dictado inteligente en campos visibles (Form Binding)
        val formResult = formBinder.bindFinal(text)
        when (formResult) {
            is VoiceBindingResult.Applied -> {
                return EvairInteractionResult.FormBound(formResult.fieldId, formResult.text)
            }
            is VoiceBindingResult.MultiSlotApplied -> {
                return EvairInteractionResult.MultiSlotFormBound(formResult.slots)
            }
            is VoiceBindingResult.DeniedSecretField -> return EvairInteractionResult.FormRejected("Introduce contraseñas y datos secretos manualmente; no los completaré por voz.")
            is VoiceBindingResult.StaleHumanEditRace -> return EvairInteractionResult.FormRejected("El campo cambió mientras hablabas. Conservé tu edición; repite el dictado si lo necesitas.")
            is VoiceBindingResult.NotApplicable,
            is VoiceBindingResult.Ambiguous -> {
                // Passthrough to unhandled or general agent resolver
            }
        }

        // 3. Grafo Semántico de Navegación (SemanticUiGraph)
        val targetScreen = semanticUiGraph.resolveTargetScreen(text)
        if (targetScreen != null) {
            onNavigate(targetScreen.route)
            return EvairInteractionResult.MagicSectionNavigated(targetScreen.route, targetScreen.displayName)
        }

        // Planning uses the same registry as the existing agent compiler. It never executes
        // untyped parameters or invents an execution gateway when a domain is not connected.
        val context = contextProvider?.currentSnapshot()
            ?: return EvairInteractionResult.Unhandled(text, "No hay contexto de cuenta disponible para esta acción.")
        val resolution = intentResolver.resolve(AgentInput(text, InputSource.VOICE), context)
        if (!resolution.isConfident) return EvairInteractionResult.Unhandled(text)
        return when (val result = AgentPlanCompiler(capabilityRegistry).compile(resolution, context)) {
            is PlanCompilationResult.Success -> {
                val current = contextProvider?.currentSnapshot()
                    ?: return EvairInteractionResult.Unhandled(text, "El contexto ya no está disponible.")
                val unchanged = current.principalId == context.principalId &&
                    current.contextGeneration == context.contextGeneration &&
                    current.activeVehicleId == context.activeVehicleId && current.activeRideId == context.activeRideId &&
                    current.currentScreen == context.currentScreen
                if (!unchanged) EvairInteractionResult.Unhandled(text, "El contexto cambió. Repite la solicitud en la cuenta y vehículo actuales.")
                else EvairInteractionResult.GoalDeferred(result.plan,
                    if (result.plan.confirmationPrompt != null)
                        "Plan preparado; requiere confirmación en la sección correspondiente. Aún no se ha ejecutado."
                    else "Plan preparado; esta capacidad aún no tiene un canal de ejecución conectado aquí. No se ha ejecutado ninguna acción.")
            }
            is PlanCompilationResult.ClarificationNeeded -> EvairInteractionResult.Unhandled(text, result.prompt)
            is PlanCompilationResult.CompilationError -> EvairInteractionResult.Unhandled(text,
                "La automatización de esta acción aún no está integrada. Puedes realizarla desde su sección.")
        }
    }
}
