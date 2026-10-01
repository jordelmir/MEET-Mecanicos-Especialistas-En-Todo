package com.elysium.nexus.core.settings

/**
 * The user-tunable, app-level settings.
 *
 * The settings are the knobs that affect *every*
 * profile: stick sensitivity, axis inversion,
 * haptics on/off, theme. A profile's per-control
 * layout (the [com.elysium.nexus.core.profile.Profile])
 * is separate from the settings; the settings are
 * the "I own a Magic V2, my left stick is loose,
 * and I want haptic feedback on every button
 * press" story.
 *
 * The data class is total and validated: every
 * field is in the right range; an out-of-range
 * constructor argument throws. The default
 * instance is the "no-op" configuration: the
 * raw stick (sensitivity `1.0`, no inversion),
 * haptics on, dark theme.
 *
 * ## Why the surface is so flat
 *
 * The settings are a small bag of values that
 * the user toggles in the §15 settings screen.
 * The granularity of "save" is the whole bag;
 * the granularity of "I changed sensitivity" is
 * one field. We keep the schema flat for two
 * reasons:
 *
 *  1. **Persistence**: a flat schema maps onto
 *     `SharedPreferences` keys with no serialiser.
 *     A nested schema needs JSON, and JSON needs
 *     a parser (the §15 profile parser is
 *     hand-written; we do not duplicate the work
 *     for a 6-field document).
 *  2. **Forward compatibility**: a new field is
 *     a new key. A new nested object is a
 *     schema migration. Phase 1.18's MVP can
 *     grow without the latter.
 *
 * The engine's `StickConfig` has 10 fields; the
 * settings surface is the 4 the user actually
 * wants (sensitivity per side, invert per axis
 * per side). The other 6 (deadzone, response
 * curve, snap, …) live in the profile editor
 * and are out of scope for the §15 settings.
 *
 * ## Why a `data class` and not a richer type
 *
 * The settings are a single user-visible document.
 * The §15 spec describes them as a small list of
 * knobs; a `data class` is the smallest type that
 * expresses "a bag of validated values". A sealed
 * class with a per-knob variant would be ceremony
 * for no gain: the user toggles each knob
 * independently, and the document is the
 * composition of those values.
 */
data class AppSettings(
    /**
     * The left stick's sensitivity multiplier.
     * Values in `[0.5, 2.0]`; default `1.0` (no
     * amplification, no attenuation).
     */
    val leftStickSensitivity: Float = 1.0f,

    /**
     * The right stick's sensitivity multiplier.
     * Same range and default as [leftStickSensitivity].
     */
    val rightStickSensitivity: Float = 1.0f,

    /**
     * Invert the left stick's X axis. The default
     * `false` keeps the canonical "right is
     * positive" convention. The user flips for
     * left-handed controls or D-pad-as-stick
     * rotations.
     */
    val invertLeftX: Boolean = false,

    /**
     * Invert the left stick's Y axis. The default
     * `false` keeps "up is positive". Some games
     * prefer "down is positive"; the user flips.
     */
    val invertLeftY: Boolean = false,

    /**
     * Invert the right stick's X axis.
     */
    val invertRightX: Boolean = false,

    /**
     * Invert the right stick's Y axis.
     */
    val invertRightY: Boolean = false,

    /**
     * Whether the engine emits
     * [com.elysium.nexus.core.haptics.HapticEvent]s
     * on button presses. The default is `true`
     * (haptics on).
     */
    val hapticsEnabled: Boolean = true,

    /**
     * Whether the UI is in dark theme. The default
     * is `true` (dark). The activity's theme is
     * currently always `Theme.ElysiumNexus` (dark);
     * the flag is persisted so a future "light
     * theme" toggle can be added without a
     * settings schema change.
     */
    val darkTheme: Boolean = true
) {
    init {
        require(leftStickSensitivity in MIN_SENSITIVITY..MAX_SENSITIVITY) {
            "leftStickSensitivity must be in [$MIN_SENSITIVITY, $MAX_SENSITIVITY] " +
                "(got $leftStickSensitivity)."
        }
        require(rightStickSensitivity in MIN_SENSITIVITY..MAX_SENSITIVITY) {
            "rightStickSensitivity must be in [$MIN_SENSITIVITY, $MAX_SENSITIVITY] " +
                "(got $rightStickSensitivity)."
        }
    }

    companion object {
        /** The minimum sensitivity (50% of raw). */
        const val MIN_SENSITIVITY: Float = 0.5f
        /** The maximum sensitivity (200% of raw). */
        const val MAX_SENSITIVITY: Float = 2.0f
    }
}
