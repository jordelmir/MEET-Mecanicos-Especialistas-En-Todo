package com.elysium.nexus.core.filter

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign

/**
 * Response curve applied to a stick's radial magnitude.
 *
 * `MASTER_ORDER.md` §12 lists the curve as a knob the user can dial
 * per stick. Common shapes:
 *
 *  - [Linear] — pass-through. Useful for testing and as a default.
 *  - [Exponential] — `[0, 1] -> [0, 1]` via `t^n`; pushes the
 *    mid-range to small values, expands the outer range. Common for
 *    first-person shooters where the player wants small aim
 *    corrections.
 *  - [Polynomial] — `n * t^3 + (1 - n) * t`; family that blends
 *    linear and cubic. `n = 0` is linear; `n = 1` is full cubic.
 *  - [SCurve] — `0.5 * (1 - cos(pi * t))`; smoothstep, smooth at
 *    both ends. Useful for flight / racing sims where the player
 *    wants a wide "no-op" zone and a wide "full-deflection" zone
 *    with a sharp middle.
 *  - [CustomCubic] — `sign(t) * |t|^(1/n)`. Inverse of
 *    [Exponential]; expands the small end at the cost of the
 *    large end. Common when the player wants small motions
 *    to be easier.
 *
 * All curves are pure functions `[0, 1] -> [0, 1]` and `0` → `0`,
 * `1` → `1`. The curve is applied to a magnitude *before* the
 * sign of the input is reattached, so the curve itself is unsigned.
 */
sealed class ResponseCurve {

    /** Map `[0, 1] -> [0, 1]`. The contract is `0 -> 0`, `1 -> 1`,
     *  monotone non-decreasing, continuous. Implementations enforce
     *  this; tests pin the invariants. */
    abstract fun apply(t: Float): Float

    object Linear : ResponseCurve() {
        override fun apply(t: Float): Float = t
    }

    /**
     * `t^n` for `n > 0`. The shape is steeper near zero for
     * `n > 1` (pushes small inputs to be smaller) and shallower
     * near zero for `0 < n < 1` (expands small inputs).
     */
    data class Exponential(val exponent: Float) : ResponseCurve() {
        init {
            require(exponent > 0f) {
                "Exponential exponent must be positive (got $exponent)."
            }
        }

        override fun apply(t: Float): Float = t.pow(exponent)
    }

    /**
     * Smoothstep. `0.5 * (1 - cos(pi * t))`. Symmetric s-curve.
     * `0 -> 0`, `0.5 -> 0.5`, `1 -> 1`. Smooth first derivative at
     * both ends.
     */
    object SCurve : ResponseCurve() {
        override fun apply(t: Float): Float {
            val clamped = t.coerceIn(0f, 1f)
            return 0.5f * (1f - cos(Math.PI.toFloat() * clamped))
        }
    }

    /**
     * `n * t^3 + (1 - n) * t`. A blend between linear and cubic.
     * `n = 0` is exactly linear; `n = 1` is full cubic. Negative
     * `n` would invert the curve's shape and is rejected.
     */
    data class CubicBlend(val cubicWeight: Float) : ResponseCurve() {
        init {
            require(cubicWeight in 0f..1f) {
                "CubicBlend weight must be in [0, 1] (got $cubicWeight)."
            }
        }

        override fun apply(t: Float): Float {
            val cubic = cubicWeight * t * t * t
            val linear = (1f - cubicWeight) * t
            return cubic + linear
        }
    }

    /**
     * `sign(t) * |t|^(1/n)`. Inverse of [Exponential]. Useful for
     * players who want small motions to feel bigger.
     */
    data class CustomCubic(val inverseExponent: Float) : ResponseCurve() {
        init {
            require(inverseExponent > 0f) {
                "CustomCubic inverseExponent must be positive (got $inverseExponent)."
            }
        }

        override fun apply(t: Float): Float {
            val mag = kotlin.math.abs(t)
            // t == 0 maps to 0; for any other t, raise the magnitude
            // to (1 / inverseExponent) and re-attach the sign.
            return if (mag == 0f) 0f else sign(t) * mag.pow(1f / inverseExponent)
        }
    }
}
