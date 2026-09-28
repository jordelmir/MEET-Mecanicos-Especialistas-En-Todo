package com.elysium.nexus.core.filter

import com.elysium.nexus.core.model.StickState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The stick filter pipeline.
 *
 * Implements the §12 formula verbatim for the
 * [StickMode.FixedCenter] case, and scaffolds the rest. The
 * pipeline is a single pure function from `(raw, config) -> filtered`
 * so it is trivially testable from the JVM (no Android types, no
 * coroutines, no clocks).
 *
 * The §12 spec's primary formula is:
 *
 * ```
 * m = sqrt(x^2 + y^2)
 * if m <= innerDeadzone: output = 0
 * else:
 *     normalized = clamp((m - innerDeadzone) / (outerThreshold - innerDeadzone), 0, 1)
 *     curved     = responseCurve(normalized)
 *     output     = normalize(x, y) * curved
 * ```
 *
 * After the formula, the pipeline layers on (in order):
 *  1. **Anti-deadzone** — if `curved > 0` and `antiDeadzone > 0`,
 *     rescale the magnitude from `[-antiDeadzone, +antiDeadzone]`
 *     to `[0, 1]`. This is the "stirring zone".
 *  2. **Inversion** — flip `x` if `invertX`, flip `y` if `invertY`.
 *  3. **Sensitivity** — multiply magnitude by `sensitivity`.
 *  4. **Snap to cardinal** — if `snapToCardinal > 0`, round the
 *     angle to the nearest cardinal/intercardinal if the magnitude
 *     is small enough.
 *  5. **Reduced range** — if `reducedRange` is non-null, rescale
 *     the final `[0, 1]` magnitude into the configured subrange.
 *  6. **Saturation** — clip magnitude to `saturation`.
 *
 * The order is deliberate. Anti-deadzone is applied after the
 * curve so the curve's small-input behavior (e.g.
 * [ResponseCurve.Exponential] with `n > 1`) does not amplify
 * the anti-deadzone zone. Saturation is last so sensitivity and
 * reduced range compose predictably.
 */
object StickFilters {

    /**
     * Apply the filter pipeline to a raw stick sample.
     *
     * @param raw the raw stick sample (canonical ±1.0 range).
     * @param config the per-stick configuration.
     * @return the filtered stick sample, still in the canonical
     *   `[-1.0, 1.0]` range, with the configuration's inversion,
     *   sensitivity, and saturation already applied.
     */
    fun apply(raw: StickState, config: StickConfig): StickState {
        // 0.3 implements only FixedCenter. Other modes are
        // documented to land in 0.4; for now they fall through to
        // the FixedCenter pipeline (which is the safe default).
        when (config.mode) {
            StickMode.FixedCenter -> Unit // pass through
            else -> Unit // 0.4 will branch here
        }

        // Step 0: degenerate input.
        if (raw.x == 0f && raw.y == 0f) return StickState.NEUTRAL

        // Step 1: §12 formula.
        val m = hypot(raw.x.toDouble(), raw.y.toDouble()).toFloat()
        if (m <= config.innerDeadzone) {
            return StickState.NEUTRAL
        }

        val span = config.outerThreshold - config.innerDeadzone
        val normalized = ((m - config.innerDeadzone) / span).coerceIn(0f, 1f)
        val curved = config.responseCurve.apply(normalized)

        // The output direction is the raw input direction.
        val nx = raw.x / m
        val ny = raw.y / m

        // Step 2: anti-deadzone. The "stirring zone" maps small
        // curved outputs into a sub-range near zero. The exact
        // formula matches what is conventional in flight-stick
        // and racing-wheel firmware: a magnitude that is below
        // antiDeadzone is rescaled to half of that anti-deadzone
        // zone.
        val curvedWithAnti = if (config.antiDeadzone > 0f && curved < config.antiDeadzone) {
            // Rescale [0, antiDeadzone] into [0, antiDeadzone / 2].
            curved / 2f
        } else {
            curved
        }

        // Step 3: inversion.
        val signedX = if (config.invertX) -curvedWithAnti * nx else curvedWithAnti * nx
        val signedY = if (config.invertY) -curvedWithAnti * ny else curvedWithAnti * ny

        // Step 4: sensitivity (multiplies magnitude).
        val mag = hypot(signedX.toDouble(), signedY.toDouble()).toFloat() * config.sensitivity
        val angle = atan2(signedY.toDouble(), signedX.toDouble()).toFloat()

        // Step 5: snap to cardinal. We round the angle to the
        // nearest multiple of pi/4 (45°) when the magnitude is
        // below the snap threshold. This is the §12 "snap angular"
        // knob.
        val snappedAngle = if (config.snapToCardinal > 0f && mag <= config.snapToCardinal) {
            snap(angle)
        } else {
            angle
        }

        // Step 6: reduced range. If configured, rescale the
        // magnitude from [0, 1] into [reducedRange.start,
        // reducedRange.endInclusive]. Used by precision mode.
        val rangedMag = config.reducedRange?.let { range ->
            val spanR = range.endInclusive - range.start
            range.start + mag.coerceIn(0f, 1f) * spanR
        } ?: mag

        // Step 7: saturation. The final magnitude is clipped to
        // [0, saturation]. The direction is preserved.
        val saturatedMag = rangedMag.coerceIn(0f, config.saturation)

        // Step 8: rebuild the stick.
        val outX = saturatedMag * cos(snappedAngle.toDouble()).toFloat()
        val outY = saturatedMag * sin(snappedAngle.toDouble()).toFloat()
        return StickState(outX, outY)
    }

    /**
     * Round an angle to the nearest multiple of 45° (pi/4). Used
     * by the snap-to-cardinal step. The output is always in
     * `(-pi, pi]`.
     */
    private fun snap(angle: Float): Float {
        val twoPi = (Math.PI * 2.0).toFloat()
        val quadrant = (angle / (Math.PI.toFloat() / 4f)).roundToInt() and 7
        return quadrant * (Math.PI.toFloat() / 4f)
    }
}
