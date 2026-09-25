package com.elysium369.meet.core.agent.evair

import androidx.compose.ui.geometry.Rect
import com.elysium369.meet.core.agent.anchor.AgentAnchorId
import com.elysium369.meet.core.agent.anchor.AgentAnchorRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates the EVAIR living guide, semantic UI anchors, and Teach Mode engine.
 * Master Order Omega §5, §10, §11, §12.
 */
class EvairLivingGuideTest {

    private val registry = AgentAnchorRegistry.default
    private val engine = EvairTutorialEngine.default

    @Before
    fun setup() {
        registry.clear()
        engine.dismiss()
    }

    @Test
    fun `AgentAnchorRegistry registers and retrieves dynamic element geometry`() {
        val anchorId = AgentAnchorId.SCANNER_CONNECT
        val bounds = Rect(left = 100f, top = 200f, right = 300f, bottom = 260f)

        registry.registerAnchor(anchorId, bounds, isVisible = true)

        val retrieved = registry.getAnchor(anchorId)
        assertNotNull(retrieved)
        assertEquals(200f, retrieved!!.centerX, 0.01f)
        assertEquals(230f, retrieved.centerY, 0.01f)
        assertEquals(200f, retrieved.width, 0.01f)
        assertEquals(60f, retrieved.height, 0.01f)
        assertTrue(registry.isAnchorVisible(anchorId))

        registry.unregisterAnchor(anchorId)
        assertNull(registry.getAnchor(anchorId))
        assertFalse(registry.isAnchorVisible(anchorId))
    }

    @Test
    fun `EvairTutorialEngine executes step-by-step Teach Mode progression`() {
        val tutorial = StandardTutorials.OBD_SCANNER_GUIDE
        engine.startTutorial(tutorial, mode = AgentInteractionMode.TEACH_ME)

        val initialState = engine.state.value
        assertTrue(initialState.isVisible)
        assertEquals(AvatarState.POINTING, initialState.avatarState)
        assertEquals(AgentInteractionMode.TEACH_ME, initialState.interactionMode)
        assertEquals(AgentAnchorId.SCANNER_CONNECT, initialState.activeAnchor)
        assertEquals(0, initialState.currentStepIndex)
        assertTrue(initialState.speechText.contains("CONECTAR"))

        // When user taps a WRONG button, engine does NOT advance or celebrate
        val wrongTapResult = engine.notifyUserTappedAnchor(AgentAnchorId.SCANNER_TAB_DIAGNOSTIC)
        assertFalse("Wrong button tap must not trigger completion", wrongTapResult)
        assertEquals(AvatarState.POINTING, engine.state.value.avatarState)

        // When user taps the RIGHT button taught by EVAIR:
        val correctTapResult = engine.notifyUserTappedAnchor(AgentAnchorId.SCANNER_CONNECT)
        assertTrue("Correct button tap must be acknowledged", correctTapResult)
        assertEquals(AvatarState.CELEBRATING, engine.state.value.avatarState)
        assertNotNull(engine.state.value.celebrationText)

        // Advance to step 2
        engine.advanceStep()
        val step2State = engine.state.value
        assertEquals(1, step2State.currentStepIndex)
        assertEquals(AgentAnchorId.SCANNER_TAB_DIAGNOSTIC, step2State.activeAnchor)
        assertEquals(AvatarState.POINTING, step2State.avatarState)

        // Advance to step 3
        engine.advanceStep()
        val step3State = engine.state.value
        assertEquals(2, step3State.currentStepIndex)
        assertEquals(AgentAnchorId.SCANNER_ADD_PID, step3State.activeAnchor)

        // Completing all steps celebrates total tutorial completion
        engine.advanceStep()
        val completedState = engine.state.value
        assertEquals(AvatarState.CELEBRATING, completedState.avatarState)
        assertTrue(completedState.title.contains("Completado"))

        // Dismiss hides overlay and enters sleeping
        engine.dismiss()
        assertFalse(engine.state.value.isVisible)
        assertEquals(AvatarState.SLEEPING, engine.state.value.avatarState)
    }

    @Test
    fun `toggleInteractionMode switches between Teach Me and Do It`() {
        engine.startTutorial(StandardTutorials.MOBILITY_RIDE_GUIDE, mode = AgentInteractionMode.TEACH_ME)
        assertEquals(AgentInteractionMode.TEACH_ME, engine.state.value.interactionMode)

        engine.toggleInteractionMode()
        assertEquals(AgentInteractionMode.DO_IT, engine.state.value.interactionMode)

        engine.toggleInteractionMode()
        assertEquals(AgentInteractionMode.TEACH_ME, engine.state.value.interactionMode)
    }
}
