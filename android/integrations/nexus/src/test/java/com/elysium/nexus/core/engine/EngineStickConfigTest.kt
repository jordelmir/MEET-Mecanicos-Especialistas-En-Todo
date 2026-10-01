package com.elysium.nexus.core.engine

import com.elysium.nexus.core.filter.StickConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for the §15 settings → engine wiring
 * (Phase 1.23).
 *
 * The engine's per-side [StickConfig] is mutable;
 * a settings change updates the config in place.
 * The next [CanonicalInputEngine.submitStick] call
 * uses the new config.
 *
 * The tests do not exercise the filter pipeline's
 * output (the filter is the [StickFilters] module's
 * concern); they verify the engine honours the
 * updated config. The `submitStick` path requires
 * the engine to be in [EngineState.Active]; the
 * tests drive the state machine first.
 */
class EngineStickConfigTest {

    private lateinit var scope: CoroutineScope
    private lateinit var engine: CanonicalInputEngine

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        engine = CanonicalInputEngine(
            leftStickConfig = StickConfig(),
            rightStickConfig = StickConfig(),
            scope = scope
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `currentStickConfig returns the constructor's defaults`() {
        assertEquals(StickConfig(), engine.currentStickConfig(StickSide.Left))
        assertEquals(StickConfig(), engine.currentStickConfig(StickSide.Right))
    }

    @Test
    fun `updateStickConfig replaces the left config`() {
        val newConfig = StickConfig(sensitivity = 1.7f)
        engine.updateStickConfig(StickSide.Left, newConfig)
        assertEquals(1.7f, engine.currentStickConfig(StickSide.Left).sensitivity, 0.0001f)
        // The right config is unchanged.
        assertEquals(StickConfig(), engine.currentStickConfig(StickSide.Right))
    }

    @Test
    fun `updateStickConfig replaces the right config independently`() {
        val newConfig = StickConfig(sensitivity = 0.6f)
        engine.updateStickConfig(StickSide.Right, newConfig)
        assertEquals(0.6f, engine.currentStickConfig(StickSide.Right).sensitivity, 0.0001f)
        assertEquals(StickConfig(), engine.currentStickConfig(StickSide.Left))
    }

    @Test
    fun `updateStickConfig is independent for each side`() {
        engine.updateStickConfig(StickSide.Left, StickConfig(sensitivity = 1.4f))
        engine.updateStickConfig(StickSide.Right, StickConfig(sensitivity = 0.8f))
        assertEquals(1.4f, engine.currentStickConfig(StickSide.Left).sensitivity, 0.0001f)
        assertEquals(0.8f, engine.currentStickConfig(StickSide.Right).sensitivity, 0.0001f)
    }

    @Test
    fun `updateStickConfig rejects an invalid config`() {
        // `outerThreshold` must be strictly greater
        // than `innerDeadzone`. The default is
        // `innerDeadzone = 0.10, outerThreshold = 0.95`
        // (valid). A config with `innerDeadzone = 0.5`
        // and `outerThreshold = 0.3` is invalid; the
        // `StickConfig.init` block throws before the
        // engine ever sees the value.
        var threw = false
        try {
            engine.updateStickConfig(
                StickSide.Left,
                StickConfig(innerDeadzone = 0.5f, outerThreshold = 0.3f)
            )
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected IllegalArgumentException for invalid config", threw)
        // The engine's state is unchanged: the
        // default config is still in place.
        assertEquals(StickConfig(), engine.currentStickConfig(StickSide.Left))
    }

    @Test
    fun `submitStick uses the updated config`() {
        // Drive the engine to Active.
        engine.transitionTo(EngineState.Discovering)
        engine.transitionTo(EngineState.Pairing)
        engine.transitionTo(EngineState.Authenticating)
        engine.transitionTo(EngineState.Negotiating)
        engine.transitionTo(EngineState.Connected)
        engine.transitionTo(EngineState.Active)
        // Push the left stick to a "high" raw value.
        // The default config (sensitivity = 1.0)
        // passes the value through. With a higher
        // sensitivity the filtered value should be
        // larger.
        val raw = com.elysium.nexus.core.model.StickState(x = 0.5f, y = 0.0f)
        val resultBefore = engine.submitStick(StickSide.Left, raw)
        val magnitudeBefore = when (resultBefore) {
            is com.elysium.nexus.core.engine.SubmitResult.Accepted ->
                kotlin.math.sqrt(
                    resultBefore.state.leftStick.x * resultBefore.state.leftStick.x +
                        resultBefore.state.leftStick.y * resultBefore.state.leftStick.y
                )
            else -> error("Expected Accepted, got $resultBefore")
        }
        // Bump the sensitivity to 2.0 and submit
        // the same raw value.
        engine.updateStickConfig(StickSide.Left, StickConfig(sensitivity = 2.0f))
        val resultAfter = engine.submitStick(StickSide.Left, raw)
        val magnitudeAfter = when (resultAfter) {
            is com.elysium.nexus.core.engine.SubmitResult.Accepted ->
                kotlin.math.sqrt(
                    resultAfter.state.leftStick.x * resultAfter.state.leftStick.x +
                        resultAfter.state.leftStick.y * resultAfter.state.leftStick.y
                )
            else -> error("Expected Accepted, got $resultAfter")
        }
        // The higher sensitivity should produce a
        // larger magnitude (the saturation cap of 1.0
        // is the ceiling).
        assertTrue(
            "Expected magnitude after ($magnitudeAfter) > magnitude before ($magnitudeBefore).",
            magnitudeAfter > magnitudeBefore
        )
    }

    @Test
    fun `two updates produce a different config than one`() {
        engine.updateStickConfig(StickSide.Left, StickConfig(sensitivity = 1.0f))
        val first = engine.currentStickConfig(StickSide.Left)
        engine.updateStickConfig(StickSide.Left, StickConfig(sensitivity = 1.5f))
        val second = engine.currentStickConfig(StickSide.Left)
        assertNotEquals(first, second)
    }
}
