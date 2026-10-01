package com.elysium369.meet.core.agent.ui

import androidx.compose.ui.geometry.Rect
import com.elysium369.meet.core.audio.VoiceTranscriptEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class EvairUiInteractionSafetyTest {
    private val registry = AgentUiRegistry()
    private val id = AgentUiControlId("test.send")
    private val bounds = Rect(0f, 0f, 40f, 40f)
    @Before fun main() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun reset() { Dispatchers.resetMain() }
    @Test fun naturalSectionAndButtonCommandsResolveTheirActualLabel() {
        assertEquals(MagicUiCommand.Section("Mensajes"), MagicUiCommandParser.parse("entra a sección Mensajes"))
        assertEquals(MagicUiCommand.Section("Garage"), MagicUiCommandParser.parse("entra en la sección Garage"))
        assertEquals(MagicUiCommand.Select("Enviar"), MagicUiCommandParser.parse("por favor, selecciona el botón Enviar"))
        assertTrue(MagicUiCommandParser.isCommandLike("por favor, selecciona"))
        assertNull(MagicUiCommandParser.parse("no selecciones Enviar"))
        assertEquals("messages", NavigationCatalog.findByQuery("Mensajes")?.route)
        assertEquals("Servicios Activos", NavigationCatalog.findByQuery("Servicios Activos")?.label)
    }
    @Test fun conversationalMentionDoesNotNavigateOrWriteToAnUnfocusedField() = runTest {
        val graph = com.elysium369.meet.ui.navigation.SemanticUiGraph.createDefault()
        assertNull(graph.resolveTargetScreen("¿Por qué no veo los mensajes?"))
        assertEquals("messages", graph.resolveTargetScreen("abre mensajes")?.route)
        var writes = 0
        registry.register(RegisteredAgentUiControl(AgentUiControlSnapshot(id, "Mensaje", kind = AgentUiControlKind.TEXT_FIELD, bounds = bounds), writeText = { writes++ }))
        assertEquals(VoiceBindingResult.NotApplicable, VoiceFormBinder(registry).bindFinal("¿Por qué no veo los mensajes?"))
        assertEquals(0, writes)
    }
    private fun control(label: String = "Enviar", activate: (() -> Unit)? = null) = RegisteredAgentUiControl(
        AgentUiControlSnapshot(id, label, bounds = bounds), activate = activate)
    private class Motion(val teleport: () -> Unit = {}, val pulse: () -> Unit = {}) : CompanionMotionPort {
        override var currentHomePose = CompanionHomePose(0f, 0f)
        var returned = false
        override fun updateHomePose(pose: CompanionHomePose) { currentHomePose = pose }
        override suspend fun teleportTo(target: Rect) { teleport() }
        override suspend fun tapPulse() { pulse() }
        override suspend fun returnHome() { returned = true }
    }
    @Test fun missingCallbackCannotReportSelectionSuccess() = runTest {
        registry.register(control())
        val motion = Motion()
        assertEquals(UiSelectionResult.Stale, EvairUiInteractionCoordinator(registry).select("Enviar", motion))
        assertTrue(motion.returned)
    }
    @Test fun replacementDuringTeleportDoesNotActivateEitherButton() = runTest {
        var taps = 0
        registry.register(control { taps++ })
        val motion = Motion(teleport = { registry.register(control("Eliminar") { taps++ }) })
        assertEquals(UiSelectionResult.Stale, EvairUiInteractionCoordinator(registry).select("Enviar", motion))
        assertEquals(0, taps)
    }
    @Test fun removalDuringPulseDoesNotReportSuccess() = runTest {
        var taps = 0
        registry.register(control { taps++ })
        val motion = Motion(pulse = { registry.unregister(id) })
        assertEquals(UiSelectionResult.Stale, EvairUiInteractionCoordinator(registry).select("Enviar", motion))
        assertEquals(0, taps)
    }
    @Test fun repeatedIdenticalLayoutDoesNotInvalidateAction() = runTest {
        var taps = 0
        registry.register(control { taps++ })
        val motion = Motion(teleport = { registry.updateBounds(id, bounds, true) })
        assertTrue(EvairUiInteractionCoordinator(registry).select("Enviar", motion) is UiSelectionResult.Success)
        assertEquals(1, taps)
    }
    @Test fun duplicateVisibleLabelsRequireClarificationInsteadOfCanonicalNavigation() = runTest {
        registry.register(control("Garage") {})
        registry.register(RegisteredAgentUiControl(AgentUiControlSnapshot(AgentUiControlId("test.other"), "Garage", bounds = bounds), activate = {}))
        var navigated = false
        val result = EvairUiInteractionCoordinator(registry).enterSection("Garage", onNavigate = { navigated = true })
        assertTrue(result is UiSelectionResult.Ambiguous)
        assertFalse(navigated)
    }
    @Test fun partialNavigationCommandNeverWritesIntoVisibleField() = runTest {
        var text = "Original"
        registry.register(RegisteredAgentUiControl(AgentUiControlSnapshot(id, "Nombre", kind = AgentUiControlKind.TEXT_FIELD, bounds = bounds, focused = true), readText = { text }, writeText = { text = it }))
        val orchestrator = EvairInteractionOrchestrator(formBinder = VoiceFormBinder(registry))
        assertEquals(VoiceBindingResult.NotApplicable, orchestrator.handlePartialTranscript(VoiceTranscriptEvent.Partial("voice", "selecciona Garage")))
        assertEquals("Original", text)
    }
    @Test fun progressiveMessageDictationStripsInstructionsWithoutDuplicatingPartials() = runTest {
        var text = ""
        registry.register(RegisteredAgentUiControl(AgentUiControlSnapshot(id, "Mensaje", kind = AgentUiControlKind.TEXT_FIELD, role = AgentTextFieldRole.MESSAGE, bounds = bounds), readText = { text }, writeText = { text = it }))
        val binder = VoiceFormBinder(registry)
        binder.bindPartial("mensaje Hola")
        binder.bindPartial("mensaje Hola Jorge")
        binder.bindFinal("mensaje Hola Jorge, ya llegué")
        assertEquals("Hola Jorge, ya llegué", text)
    }
    @Test fun manualEditWinsOverTheNextVoicePartial() = runTest {
        var text = ""
        registry.register(RegisteredAgentUiControl(AgentUiControlSnapshot(id, "Mensaje", kind = AgentUiControlKind.TEXT_FIELD, bounds = bounds, focused = true), readText = { text }, writeText = { text = it }))
        val binder = VoiceFormBinder(registry)
        binder.bindPartial("Hola")
        text = "Edición del humano"
        assertTrue(binder.bindPartial("Hola Jorge") is VoiceBindingResult.StaleHumanEditRace)
        assertTrue(binder.bindPartial("Hola Jorge, ya llegué") is VoiceBindingResult.StaleHumanEditRace)
        assertTrue(binder.bindFinal("Hola Jorge, ya llegué") is VoiceBindingResult.StaleHumanEditRace)
        assertEquals("Edición del humano", text)
    }
    @Test fun secretFieldIsRejectedWithoutRunningFallbackIntelligence() = runTest {
        var writes = 0
        registry.register(RegisteredAgentUiControl(AgentUiControlSnapshot(id, "Contraseña", kind = AgentUiControlKind.TEXT_FIELD, bounds = bounds, sensitivity = AgentUiSensitivity.SECRET, focused = true), writeText = { writes++ }))
        val result = EvairInteractionOrchestrator(formBinder = VoiceFormBinder(registry))
            .handleFinalTranscript(VoiceTranscriptEvent.Final("voice", "texto secreto", 1f))
        assertTrue(result is EvairInteractionResult.FormRejected)
        assertEquals(0, writes)
    }
    @Test fun callbackFailureRestoresAvatarAndCannotReportSuccess() = runTest {
        registry.register(control { error("private failure") })
        val motion = Motion()
        assertTrue(EvairUiInteractionCoordinator(registry).select("Enviar", motion) is UiSelectionResult.Failed)
        assertTrue(motion.returned)
    }
    @Test fun clarificationActivatesChosenRealControlOnlyOnce() = runTest {
        val secondId = AgentUiControlId("test.second")
        var chosen: AgentUiControlId? = null
        var taps = 0
        registry.register(control("Enviar") { chosen = id; taps++ })
        registry.register(RegisteredAgentUiControl(AgentUiControlSnapshot(secondId, "Enviar", bounds = bounds), activate = { chosen = secondId; taps++ }))
        val provider = object : com.elysium369.meet.core.agent.context.AgentContextProvider {
            override fun currentSnapshot() = com.elysium369.meet.core.agent.context.AgentContextSnapshot(principalId = "account", currentScreen = "messages")
        }
        val orchestrator = EvairInteractionOrchestrator(uiCoordinator = EvairUiInteractionCoordinator(registry), contextProvider = provider)
        val options = orchestrator.handleFinalTranscript(VoiceTranscriptEvent.Final("choose", "selecciona Enviar", 1f)) as EvairInteractionResult.MagicAmbiguous
        val command = VoiceTranscriptEvent.Final("answer", "la segunda", 1f)
        assertTrue(orchestrator.handleFinalTranscript(command) is EvairInteractionResult.MagicSelectExecuted)
        assertEquals(options.candidates[1].control.id, chosen)
        assertTrue(orchestrator.handleFinalTranscript(command) is EvairInteractionResult.FormRejected)
        assertEquals(1, taps)
    }
    @Test fun accountSwitchInvalidatesPendingClarification() = runTest {
        var taps = 0
        registry.register(control("Enviar") { taps++ })
        registry.register(RegisteredAgentUiControl(AgentUiControlSnapshot(AgentUiControlId("test.second"), "Enviar", bounds = bounds), activate = { taps++ }))
        var account = "owner-a"
        val provider = object : com.elysium369.meet.core.agent.context.AgentContextProvider {
            override fun currentSnapshot() = com.elysium369.meet.core.agent.context.AgentContextSnapshot(principalId = account, currentScreen = "messages")
        }
        val orchestrator = EvairInteractionOrchestrator(uiCoordinator = EvairUiInteractionCoordinator(registry), contextProvider = provider)
        assertTrue(orchestrator.handleFinalTranscript(VoiceTranscriptEvent.Final("choose", "selecciona Enviar", 1f)) is EvairInteractionResult.MagicAmbiguous)
        account = "owner-b"
        assertTrue(orchestrator.handleFinalTranscript(VoiceTranscriptEvent.Final("answer", "la primera", 1f)) is EvairInteractionResult.FormRejected)
        assertEquals(0, taps)
    }
}
