package com.elysium369.meet.core.agent.evair

import com.elysium369.meet.core.agent.anchor.AgentAnchorId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Living states for the EVAIR embodied digital entity.
 * Master Order Omega §8.
 */
enum class AvatarState {
    SLEEPING,
    IDLE,
    OBSERVING,
    THINKING,
    PLANNING,
    POINTING,
    TOUCHING,
    EXPLAINING,
    WAITING_USER_ACTION,
    CELEBRATING,
    ERROR,
}

/**
 * Interaction modes for human guidance vs execution.
 * Master Order Omega §12 (Teach Mode).
 */
enum class AgentInteractionMode {
    TEACH_ME, // EVAIR points, touches, explains, and waits for the human to learn and tap.
    DO_IT,    // EVAIR demonstrates the action and executes the capability on behalf of user.
}

data class TutorialStep(
    val stepNumber: Int,
    val totalSteps: Int,
    val targetAnchor: AgentAnchorId,
    val title: String,
    val message: String,
    val buttonActionLabel: String = "¡Toca aquí!",
    val celebrationMessage: String = "¡Perfecto! Has dominado este paso.",
)

data class EvairTutorial(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<TutorialStep>,
)

object StandardTutorials {
    val OBD_SCANNER_GUIDE = EvairTutorial(
        id = "obd_scanner_guide",
        title = "Guía Maestra: Escaneo de Vehículo & Emisiones",
        description = "Aprende a conectar el escáner OBD-II, evaluar emisiones y monitorear sensores.",
        steps = listOf(
            TutorialStep(
                stepNumber = 1,
                totalSteps = 3,
                targetAnchor = AgentAnchorId.SCANNER_CONNECT,
                title = "1. Conectar Adaptador OBD-II",
                message = "Toca el botón CONECTAR arriba a la derecha para enlazar tu adaptador Bluetooth/Wi-Fi con el vehículo.",
                buttonActionLabel = "Toca 'CONECTAR'",
                celebrationMessage = "¡Excelente! Has aprendido a conectar el escáner.",
            ),
            TutorialStep(
                stepNumber = 2,
                totalSteps = 3,
                targetAnchor = AgentAnchorId.SCANNER_TAB_DIAGNOSTIC,
                title = "2. Pestaña de Diagnóstico",
                message = "Toca la pestaña DIAGNÓSTICO para ver códigos de falla DTC, Mode \$06 y la inspección Pre-ITV / DEKRA.",
                buttonActionLabel = "Toca 'DIAGNÓSTICO'",
                celebrationMessage = "¡Muy bien! Esta sección evalúa la salud de tu motor.",
            ),
            TutorialStep(
                stepNumber = 3,
                totalSteps = 3,
                targetAnchor = AgentAnchorId.SCANNER_ADD_PID,
                title = "3. Agregar Sensores en Vivo",
                message = "Toca este botón (+) para agregar medidores en vivo de O2, RPM, temperatura o combustible.",
                buttonActionLabel = "Toca el botón (+)",
                celebrationMessage = "¡Fantástico! Ya sabes cómo personalizar tu escáner.",
            ),
        )
    )

    val MOBILITY_RIDE_GUIDE = EvairTutorial(
        id = "mobility_ride_guide",
        title = "Guía de Movilidad: Pedir un Viaje",
        description = "Aprende el flujo de solicitud de viaje con precios autoritativos y sin trampas.",
        steps = listOf(
            TutorialStep(
                stepNumber = 1,
                totalSteps = 3,
                targetAnchor = AgentAnchorId.RIDE_PICKUP,
                title = "1. Punto de Partida",
                message = "Toca aquí para definir o confirmar tu lugar de recogida.",
                buttonActionLabel = "Toca el campo de recogida",
            ),
            TutorialStep(
                stepNumber = 2,
                totalSteps = 3,
                targetAnchor = AgentAnchorId.RIDE_DESTINATION,
                title = "2. Destino",
                message = "Indica a dónde deseas viajar para calcular la ruta exacta.",
                buttonActionLabel = "Toca el campo de destino",
            ),
            TutorialStep(
                stepNumber = 3,
                totalSteps = 3,
                targetAnchor = AgentAnchorId.RIDE_CONFIRM,
                title = "3. Confirmación Segura",
                message = "Revisa la tarifa y confirma. Cero cargos o peticiones hasta tu aprobación explícita.",
                buttonActionLabel = "Toca 'SOLICITAR VIAJE'",
            ),
        )
    )
}

data class EvairGuideState(
    val isVisible: Boolean = false,
    val avatarState: AvatarState = AvatarState.IDLE,
    val interactionMode: AgentInteractionMode = AgentInteractionMode.TEACH_ME,
    val activeTutorial: EvairTutorial? = null,
    val currentStepIndex: Int = 0,
    val activeAnchor: AgentAnchorId? = null,
    val title: String = "",
    val speechText: String = "",
    val celebrationText: String? = null,
) {
    val currentStep: TutorialStep?
        get() = activeTutorial?.steps?.getOrNull(currentStepIndex)
}

/**
 * Controller and state machine for EVAIR's living guide and Teach Mode.
 */
class EvairTutorialEngine private constructor() {

    private val _state = MutableStateFlow(EvairGuideState())
    val state: StateFlow<EvairGuideState> = _state.asStateFlow()

    fun startTutorial(tutorial: EvairTutorial, mode: AgentInteractionMode = AgentInteractionMode.TEACH_ME) {
        val firstStep = tutorial.steps.firstOrNull() ?: return
        _state.update {
            EvairGuideState(
                isVisible = true,
                avatarState = AvatarState.POINTING,
                interactionMode = mode,
                activeTutorial = tutorial,
                currentStepIndex = 0,
                activeAnchor = firstStep.targetAnchor,
                title = firstStep.title,
                speechText = firstStep.message,
                celebrationText = null,
            )
        }
    }

    fun notifyUserTappedAnchor(tappedAnchor: AgentAnchorId): Boolean {
        val current = _state.value
        if (!current.isVisible) return false
        val step = current.currentStep ?: return false

        if (step.targetAnchor == tappedAnchor) {
            // User performed the expected action! Celebrate and advance
            _state.update {
                it.copy(
                    avatarState = AvatarState.CELEBRATING,
                    celebrationText = step.celebrationMessage,
                )
            }
            return true
        }
        return false
    }

    fun advanceStep() {
        val current = _state.value
        val tutorial = current.activeTutorial ?: return
        val nextIndex = current.currentStepIndex + 1

        if (nextIndex < tutorial.steps.size) {
            val nextStep = tutorial.steps[nextIndex]
            _state.update {
                it.copy(
                    currentStepIndex = nextIndex,
                    avatarState = AvatarState.POINTING,
                    activeAnchor = nextStep.targetAnchor,
                    title = nextStep.title,
                    speechText = nextStep.message,
                    celebrationText = null,
                )
            }
        } else {
            // Completed all steps!
            _state.update {
                it.copy(
                    avatarState = AvatarState.CELEBRATING,
                    title = "¡Tutorial Completado!",
                    speechText = "¡Has completado con éxito todos los pasos! Ya puedes operar esta función por ti mismo.",
                    activeAnchor = null,
                    celebrationText = "¡Excelente trabajo!",
                )
            }
        }
    }

    fun previousStep() {
        val current = _state.value
        val tutorial = current.activeTutorial ?: return
        val prevIndex = (current.currentStepIndex - 1).coerceAtLeast(0)
        val step = tutorial.steps[prevIndex]

        _state.update {
            it.copy(
                currentStepIndex = prevIndex,
                avatarState = AvatarState.POINTING,
                activeAnchor = step.targetAnchor,
                title = step.title,
                speechText = step.message,
                celebrationText = null,
            )
        }
    }

    fun toggleInteractionMode() {
        _state.update {
            val newMode = if (it.interactionMode == AgentInteractionMode.TEACH_ME) {
                AgentInteractionMode.DO_IT
            } else {
                AgentInteractionMode.TEACH_ME
            }
            it.copy(interactionMode = newMode)
        }
    }

    fun dismiss() {
        _state.update {
            it.copy(
                isVisible = false,
                avatarState = AvatarState.SLEEPING,
                activeAnchor = null,
            )
        }
    }

    fun showGuideOrb() {
        _state.update {
            it.copy(
                isVisible = true,
                avatarState = AvatarState.IDLE,
                title = "EVAIR — Tu Asistente Inteligente",
                speechText = "Hola, soy EVAIR. Toca 'Aprender' para que te enseñe paso a paso cómo usar cada botón de la aplicación.",
                activeAnchor = null,
                celebrationText = null,
            )
        }
    }

    companion object {
        val default: EvairTutorialEngine by lazy { EvairTutorialEngine() }
    }
}
