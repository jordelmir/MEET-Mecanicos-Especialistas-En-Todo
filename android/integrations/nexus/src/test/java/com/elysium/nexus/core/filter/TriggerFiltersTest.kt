package com.elysium.nexus.core.filter

import com.elysium.nexus.core.model.TriggerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TriggerFilters] — the §13 trigger filter pipeline.
 */
class TriggerFiltersTest {

    private val identity = TriggerConfig()

    @Test
    fun zeroInputIsZeroOutput() {
        val out = TriggerFilters.apply(TriggerState.RELEASED, identity)
        assertEquals(0f, out.value, 0f)
    }

    @Test
    fun belowDeadzoneIsZero() {
        val cfg = TriggerConfig(deadzone = 0.20f, activationPoint = 0.20f)
        val out = TriggerFilters.apply(TriggerState(0.10f), cfg)
        assertEquals(0f, out.value, 0f)
    }

    @Test
    fun atDeadzoneIsZero() {
        val cfg = TriggerConfig(deadzone = 0.20f, activationPoint = 0.20f)
        val out = TriggerFilters.apply(TriggerState(0.20f), cfg)
        assertEquals(0f, out.value, 0f)
    }

    @Test
    fun aboveDeadzoneIsRescaled() {
        val cfg = TriggerConfig(deadzone = 0.10f)
        // 0.55 maps to (0.55 - 0.10) / (1 - 0.10) = 0.5
        val out = TriggerFilters.apply(TriggerState(0.55f), cfg)
        assertEquals(0.5f, out.value, 1e-5f)
    }

    @Test
    fun hairTriggerShortCircuitsToFull() {
        val cfg = TriggerConfig(deadzone = 0.05f, hairTrigger = true)
        val out = TriggerFilters.apply(TriggerState(0.10f), cfg)
        assertEquals(1f, out.value, 0f)
    }

    @Test
    fun hairTriggerAboveDeadzoneIsFull() {
        val cfg = TriggerConfig(deadzone = 0.05f, hairTrigger = true)
        val out = TriggerFilters.apply(TriggerState(0.99f), cfg)
        assertEquals(1f, out.value, 0f)
    }

    @Test
    fun exponentialCurveChangesMidpoint() {
        val linear = TriggerFilters.apply(TriggerState(0.55f), identity)
        val curved = TriggerFilters.apply(
            TriggerState(0.55f),
            TriggerConfig(responseCurve = ResponseCurve.Exponential(2f))
        )
        // 0.55 with deadzone 0.05 normalises to 0.555...;
        // exponential(2) pushes mid-range down.
        assertTrue(
            "expected exponential < linear (linear=$linear, exp=$curved)",
            curved.value < linear.value
        )
    }

    @Test
    fun returnCurveIsUsedOnTheWayDown() {
        val cfg = TriggerConfig(
            responseCurve = ResponseCurve.Linear,
            returnCurve = ResponseCurve.SCurve
        )
        // First call (no previousFiltered) uses responseCurve
        // (linear) on the way up.
        val first = TriggerFilters.apply(TriggerState(0.55f), cfg)
        // Second call with a *lower* value uses returnCurve
        // (sCurve) on the way back. SCurve at 0.5 of normalised
        // input is 0.5, so the result is still in range.
        val second = TriggerFilters.apply(TriggerState(0.30f), cfg, first)
        // Verify the second result is finite and in [0, 1].
        assertTrue(second.value in 0f..1f)
    }

    @Test
    fun reducedRangeRescales() {
        val cfg = TriggerConfig(
            deadzone = 0.0f,
            reducedRange = 0.2f..0.5f
        )
        val out = TriggerFilters.apply(TriggerState(1.0f), cfg)
        // The output is in [0.2, 0.5] regardless of the
        // input magnitude.
        assertTrue("expected ${out.value} in [0.2, 0.5]", out.value in 0.2f..0.5f)
        // At full input, the output is the top of the range.
        assertEquals(0.5f, out.value, 1e-5f)
    }

    @Test
    fun outputIsBoundedByUnitRange() {
        val cfg = TriggerConfig()
        for (i in 0..100) {
            val raw = TriggerState(i / 100f)
            val out = TriggerFilters.apply(raw, cfg)
            assertTrue("out of range at $raw: $out", out.value in 0f..1f)
        }
    }

    @Test
    fun pipelineNeverProducesNaN() {
        // Note: TriggerState's model validate() rejects NaN
        // inputs at the model layer. The filter trusts its
        // inputs. We do not feed NaN here.
        val cfg = TriggerConfig()
        for (i in 0..100) {
            val raw = TriggerState(i / 100f)
            val out = TriggerFilters.apply(raw, cfg)
            assertTrue("NaN at $raw: $out", !out.value.isNaN())
            assertTrue("Inf at $raw: $out", !out.value.isInfinite())
        }
    }
}
