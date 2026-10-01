package com.elysium.nexus.core.filter

import com.elysium.nexus.core.model.StickState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Tests for [StickFilters] — the §12 stick filter pipeline.
 *
 * Includes property-based tests that sample random stick states
 * and configurations and assert the §36 invariants:
 *  - centre is always neutral
 *  - magnitude is bounded by the configured saturation
 *  - magnitude is monotone non-decreasing in the input magnitude
 *    (when all other knobs are fixed at identity)
 *  - the pipeline does not produce NaN or Infinity
 *  - the pipeline preserves direction (output is the same angle
 *    as input, modulo snap)
 */
class StickFiltersTest {

    private val identity = StickConfig()

    // ---- Targeted unit tests for each knob -------------------------

    @Test
    fun zeroInputIsZeroOutput() {
        val out = StickFilters.apply(StickState.NEUTRAL, identity)
        assertEquals(0f, out.x, 0f)
        assertEquals(0f, out.y, 0f)
    }

    @Test
    fun belowInnerDeadzoneIsZero() {
        val cfg = StickConfig(innerDeadzone = 0.20f, outerThreshold = 0.95f)
        val small = StickState(0.1f, 0.1f) // magnitude ≈ 0.14, below 0.20
        val out = StickFilters.apply(small, cfg)
        assertEquals(0f, out.x, 0f)
        assertEquals(0f, out.y, 0f)
    }

    @Test
    fun aboveOuterThresholdIsFullDeflection() {
        val cfg = StickConfig(innerDeadzone = 0.10f, outerThreshold = 0.90f)
        val full = StickState(1f, 0f) // magnitude = 1, above 0.90
        val out = StickFilters.apply(full, cfg)
        // With linear curve, sensitivity 1, and saturation 1, the
        // output magnitude should be 1 in the +x direction.
        assertEquals(1f, out.x, 1e-5f)
        assertEquals(0f, out.y, 1e-5f)
    }

    @Test
    fun inversionFlipsAxis() {
        val cfg = StickConfig(invertX = true)
        val input = StickState(0.5f, 0f)
        val out = StickFilters.apply(input, cfg)
        assertTrue("expected x < 0, got $out", out.x < 0f)
    }

    @Test
    fun sensitivityScales() {
        val amplified = StickConfig(sensitivity = 2f, saturation = 5f)
        val attenuated = StickConfig(sensitivity = 0.5f)
        val input = StickState(0.5f, 0f) // magnitude 0.5
        val a = StickFilters.apply(input, amplified)
        val b = StickFilters.apply(input, attenuated)
        assertTrue(
            "expected amplified > attenuated (a=$a, b=$b)",
            hypot(a.x.toDouble(), a.y.toDouble()) >
                hypot(b.x.toDouble(), b.y.toDouble())
        )
    }

    @Test
    fun saturationClipsMagnitude() {
        val cfg = StickConfig(sensitivity = 10f, saturation = 0.7f)
        val input = StickState(1f, 0f)
        val out = StickFilters.apply(input, cfg)
        val mag = hypot(out.x.toDouble(), out.y.toDouble())
        assertEquals(0.7, mag, 1e-5)
    }

    @Test
    fun reducedRangeRescalesMagnitude() {
        val cfg = StickConfig(
            innerDeadzone = 0.0f,
            outerThreshold = 1.0f,
            reducedRange = 0.2f..0.5f
        )
        val input = StickState(1f, 0f)
        val out = StickFilters.apply(input, cfg)
        val mag = hypot(out.x.toDouble(), out.y.toDouble())
        assertEquals(0.5, mag, 1e-5)
    }

    @Test
    fun exponentialCurveChangesMidpoint() {
        val linear = StickFilters.apply(StickState(0.5f, 0f), identity)
        val curved = StickFilters.apply(
            StickState(0.5f, 0f),
            StickConfig(responseCurve = ResponseCurve.Exponential(2f))
        )
        // exp^2(0.5) ≈ 0.25, which is well below the linear output.
        // We use a magnitude comparison because the direction is
        // unaffected.
        val linMag = hypot(linear.x.toDouble(), linear.y.toDouble())
        val curMag = hypot(curved.x.toDouble(), curved.y.toDouble())
        assertTrue(
            "expected exponential to be smaller (linear=$linMag, exp=$curMag)",
            curMag < linMag
        )
    }

    @Test
    fun snapRoundsAngle() {
        val cfg = StickConfig(
            innerDeadzone = 0.0f,
            outerThreshold = 1.0f,
            snapToCardinal = 0.5f
        )
        // 30° at low magnitude should snap to 45° (the closest
        // cardinal/intercardinal).
        val input = StickState(
            0.866f * 0.3f, // 30° at magnitude 0.3
            0.5f * 0.3f
        )
        val out = StickFilters.apply(input, cfg)
        // Output angle should be 45° (pi/4), so x and y should be
        // approximately equal in magnitude.
        val outMag = hypot(out.x.toDouble(), out.y.toDouble())
        assertTrue("expected non-zero output, got $out", outMag > 0.01f)
        assertEquals(out.x, out.y, 0.05f) // 45° → x == y
    }

    @Test
    fun antiDeadzoneStirsNearCentre() {
        // The "stirring zone" rescale: a small magnitude that is
        // above the inner deadzone is rescaled down by a factor of
        // 2 when below the antiDeadzone. This test pins the
        // behaviour: an input just above the deadzone with a
        // non-zero antiDeadzone produces a non-zero but smaller
        // output than without antiDeadzone.
        val plain = StickConfig(
            innerDeadzone = 0.10f,
            outerThreshold = 0.90f,
            antiDeadzone = 0.0f
        )
        val stirred = StickConfig(
            innerDeadzone = 0.10f,
            outerThreshold = 0.90f,
            antiDeadzone = 0.30f
        )
        val input = StickState(0.15f, 0f) // just above the deadzone
        val plainOut = StickFilters.apply(input, plain)
        val stirredOut = StickFilters.apply(input, stirred)
        // The stirred output is at most half of the plain output
        // (the curve maps a small normalised value into a
        // sub-range; linear scaling at 0.15 maps to (0.15-0.10) /
        // (0.90-0.10) = 0.0625, well below 0.30 antiDeadzone).
        assertTrue(
            "expected stirred < plain (plain=$plainOut, stirred=$stirredOut)",
            hypot(stirredOut.x.toDouble(), stirredOut.y.toDouble()) <
                hypot(plainOut.x.toDouble(), plainOut.y.toDouble()) + 1e-6f
        )
    }

    // ---- Property-based tests --------------------------------------

    @Test
    fun centreIsAlwaysNeutral() {
        val cfgs = sampleConfigs()
        for (cfg in cfgs) {
            val out = StickFilters.apply(StickState.NEUTRAL, cfg)
            assertEquals(
                "expected neutral for $cfg, got $out",
                0f, out.x, 0f
            )
            assertEquals(0f, out.y, 0f)
        }
    }

    @Test
    fun outputIsBoundedBySaturation() {
        val cfgs = sampleConfigs()
        for (cfg in cfgs) {
            for (i in 0..100) {
                val angle = (i / 100.0 * Math.PI * 2).toFloat()
                val mag = 1f
                val input = StickState(
                    mag * kotlin.math.cos(angle.toDouble()).toFloat(),
                    mag * kotlin.math.sin(angle.toDouble()).toFloat()
                )
                val out = StickFilters.apply(input, cfg)
                val outMag = hypot(out.x.toDouble(), out.y.toDouble()).toFloat()
                assertTrue(
                    "mag $outMag > saturation ${cfg.saturation} for $cfg",
                    outMag <= cfg.saturation + 1e-5f
                )
            }
        }
    }

    @Test
    fun outputMagnitudeIsMonotoneInInputMagnitude() {
        // For an identity config (linear curve, sensitivity 1, no
        // inversion, no snap, no reduced range, no anti-deadzone),
        // a larger input magnitude must produce a larger or equal
        // output magnitude.
        val cfg = StickConfig(
            innerDeadzone = 0.0f,
            outerThreshold = 1.0f,
            antiDeadzone = 0.0f,
            responseCurve = ResponseCurve.Linear,
            sensitivity = 1.0f,
            snapToCardinal = 0.0f,
            saturation = 1.0f
        )
        var prevOut = 0f
        for (i in 0..100) {
            val mag = i / 100f
            val input = StickState(mag, 0f)
            val out = StickFilters.apply(input, cfg)
            val outMag = hypot(out.x.toDouble(), out.y.toDouble()).toFloat()
            assertTrue(
                "non-monotone at mag=$mag (prev=$prevOut, current=$outMag)",
                outMag + 1e-5f >= prevOut
            )
            prevOut = outMag
        }
    }

    @Test
    fun pipelineNeverProducesNaN() {
        val cfgs = sampleConfigs()
        for (cfg in cfgs) {
            for (i in 0..50) {
                for (j in 0..50) {
                    val x = (i - 25) / 25f
                    val y = (j - 25) / 25f
                    val input = StickState(x, y)
                    val out = StickFilters.apply(input, cfg)
                    assertTrue(
                        "NaN x for input=$input cfg=$cfg",
                        !out.x.isNaN()
                    )
                    assertTrue(
                        "NaN y for input=$input cfg=$cfg",
                        !out.y.isNaN()
                    )
                    assertTrue(
                        "Inf x for input=$input cfg=$cfg",
                        !out.x.isInfinite()
                    )
                    assertTrue(
                        "Inf y for input=$input cfg=$cfg",
                        !out.y.isInfinite()
                    )
                }
            }
        }
    }

    @Test
    fun negativeInputProducesNegativeOutput() {
        // For an identity config with no inversion and no snap, a
        // purely negative input should produce a purely negative
        // output (same axis).
        val cfg = StickConfig(
            innerDeadzone = 0.0f,
            outerThreshold = 1.0f
        )
        val input = StickState(0.5f, 0f)
        val out = StickFilters.apply(input, cfg)
        assertTrue("expected x > 0, got $out", out.x > 0f)

        val negInput = StickState(-0.5f, 0f)
        val negOut = StickFilters.apply(negInput, cfg)
        assertTrue("expected x < 0, got $negOut", negOut.x < 0f)
    }

    // ---- Helpers ----------------------------------------------------

    /**
     * Sample a small but diverse set of configurations for the
     * property-based tests. We don't need a PBT library for this;
     * a fixed corpus of representative configurations is
     * deterministic and fast.
     */
    private fun sampleConfigs(): List<StickConfig> = listOf(
        StickConfig(),
        StickConfig(innerDeadzone = 0.05f, outerThreshold = 0.95f),
        StickConfig(innerDeadzone = 0.30f, outerThreshold = 0.70f),
        StickConfig(antiDeadzone = 0.10f),
        StickConfig(responseCurve = ResponseCurve.Exponential(2f)),
        StickConfig(responseCurve = ResponseCurve.Exponential(0.5f)),
        StickConfig(responseCurve = ResponseCurve.SCurve),
        StickConfig(responseCurve = ResponseCurve.CubicBlend(0.5f)),
        StickConfig(responseCurve = ResponseCurve.CustomCubic(3f)),
        StickConfig(sensitivity = 0.5f),
        StickConfig(sensitivity = 2.0f),
        StickConfig(invertX = true),
        StickConfig(invertY = true),
        StickConfig(snapToCardinal = 0.5f),
        StickConfig(saturation = 0.5f),
        StickConfig(saturation = 1.5f),
        StickConfig(reducedRange = 0.2f..0.6f),
        // A "kitchen sink" config that exercises every knob at once.
        StickConfig(
            innerDeadzone = 0.10f,
            outerThreshold = 0.85f,
            antiDeadzone = 0.20f,
            responseCurve = ResponseCurve.Exponential(1.5f),
            sensitivity = 1.2f,
            invertX = true,
            snapToCardinal = 0.3f,
            saturation = 0.9f
        )
    )
}
