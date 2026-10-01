package com.elysium.nexus.core.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for [ResponseCurve] — the curve shapes the stick filter
 * pipeline applies to the normalised magnitude.
 *
 * The curves have three hard invariants:
 *  1. `f(0) == 0`
 *  2. `f(1) == 1`
 *  3. monotone non-decreasing on `[0, 1]`
 */
class ResponseCurveTest {

    @Test
    fun linearIsIdentity() {
        assertEquals(0f, ResponseCurve.Linear.apply(0f), 0f)
        assertEquals(0.5f, ResponseCurve.Linear.apply(0.5f), 1e-6f)
        assertEquals(1f, ResponseCurve.Linear.apply(1f), 0f)
    }

    @Test
    fun exponentialMapsEndpoints() {
        val curve = ResponseCurve.Exponential(exponent = 2f)
        assertEquals(0f, curve.apply(0f), 0f)
        assertEquals(1f, curve.apply(1f), 1e-6f)
    }

    @Test
    fun exponentialPushesMidRangeDown() {
        // For exponent > 1, the mid-range is below linear.
        val curve = ResponseCurve.Exponential(exponent = 2f)
        val mid = curve.apply(0.5f)
        assertTrue("expected 0.25, got $mid", abs(mid - 0.25f) < 1e-6f)
    }

    @Test
    fun exponentialInvalidExponent() {
        try {
            ResponseCurve.Exponential(exponent = 0f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            ResponseCurve.Exponential(exponent = -1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun sCurveMapsEndpoints() {
        assertEquals(0f, ResponseCurve.SCurve.apply(0f), 1e-6f)
        assertEquals(1f, ResponseCurve.SCurve.apply(1f), 1e-6f)
    }

    @Test
    fun sCurvePassesThroughMidpoint() {
        // 0.5 * (1 - cos(pi * 0.5)) = 0.5 * (1 - 0) = 0.5
        assertEquals(0.5f, ResponseCurve.SCurve.apply(0.5f), 1e-6f)
    }

    @Test
    fun cubicBlendAtZeroIsLinear() {
        // cubicWeight = 0 → pure linear
        val curve = ResponseCurve.CubicBlend(cubicWeight = 0f)
        assertEquals(0.3f, curve.apply(0.3f), 1e-6f)
    }

    @Test
    fun cubicBlendAtOneIsFullCubic() {
        // cubicWeight = 1 → t^3
        val curve = ResponseCurve.CubicBlend(cubicWeight = 1f)
        assertEquals(0.027f, curve.apply(0.3f), 1e-6f) // 0.3^3
    }

    @Test
    fun cubicBlendInvalidWeight() {
        try {
            ResponseCurve.CubicBlend(cubicWeight = -0.1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            ResponseCurve.CubicBlend(cubicWeight = 1.5f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun customCubicMapsEndpoints() {
        val curve = ResponseCurve.CustomCubic(inverseExponent = 3f)
        assertEquals(0f, curve.apply(0f), 0f)
        assertEquals(1f, curve.apply(1f), 1e-6f)
    }

    @Test
    fun allCurvesAreMonotone() {
        // Sample 100 points across [0, 1] and verify the sequence
        // is non-decreasing for every curve type.
        val curves: List<ResponseCurve> = listOf(
            ResponseCurve.Linear,
            ResponseCurve.Exponential(0.5f),
            ResponseCurve.Exponential(2f),
            ResponseCurve.Exponential(4f),
            ResponseCurve.SCurve,
            ResponseCurve.CubicBlend(0.3f),
            ResponseCurve.CubicBlend(0.7f),
            ResponseCurve.CustomCubic(2f),
            ResponseCurve.CustomCubic(3f)
        )
        for (curve in curves) {
            var prev = curve.apply(0f)
            for (i in 1..100) {
                val t = i / 100f
                val current = curve.apply(t)
                assertTrue(
                    "Curve $curve not monotone at t=$t (prev=$prev, current=$current)",
                    current + 1e-6f >= prev
                )
                prev = current
            }
        }
    }
}
