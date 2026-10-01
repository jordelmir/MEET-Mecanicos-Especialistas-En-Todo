package com.elysium.nexus.core.model

/**
 * Canonical D-pad state.
 *
 * 8 directions plus center. The D-pad is digital (it is either pushed
 * in one of the 8 cardinal/intercardinal directions, or it is at
 * rest), so we use a closed enum instead of a pair of floats.
 *
 * A "diagonal" is one of the 4 intercardinal states; you cannot
 * legally report N+E as two separate primitives — the engine reports
 * it as [NorthEast]. The mapping from a finger-driven D-pad widget to
 * [Direction] is a UI concern, not a model concern; the model
 * guarantees only that diagonals are one state, not two.
 */
enum class DpadState {
    Center,
    North,
    NorthEast,
    East,
    SouthEast,
    South,
    SouthWest,
    West,
    NorthWest;

    /**
     * @return `true` if the D-pad is currently deflected away from
     *   [Center]. Used by host backends that want a "D-pad active"
     *   boolean.
     */
    fun isActive(): Boolean = this != Center

    /**
     * @return the unit-vector direction `(x, y)` with `x` in `{-1, 0,
     *   +1}` and `y` in `{-1, 0, +1}`. Returns `(0, 0)` for [Center].
     *   Useful for the rare case a backend needs an analog fallback
     *   (e.g. the Android touch surface pretending to be a D-pad but
     *   publishing to a stick-like consumer).
     */
    fun unitVector(): Pair<Int, Int> = when (this) {
        Center -> 0 to 0
        North -> 0 to +1
        NorthEast -> +1 to +1
        East -> +1 to 0
        SouthEast -> +1 to -1
        South -> 0 to -1
        SouthWest -> -1 to -1
        West -> -1 to 0
        NorthWest -> -1 to +1
    }

    companion object {
        /**
         * Convert a 4-bit "hat switch" integer to a [DpadState].
         *
         * The USB HID Hat Switch encoding (see USB HID Usage Tables)
         * uses 0–7 for the 8 directions clockwise from North, and 8
         * for the neutral position. The D-pad is a digital input
         * surface on the host side, so this is the right shape to
         * share with a HID report decoder.
         *
         * @return the matching [DpadState] or `null` if [hatValue] is
         *   outside `0..8` (defensive: malformed reports should
         *   produce `null` so the engine can reject them rather than
         *   silently mapping a garbage value to [Center]).
         */
        fun fromHatSwitch(hatValue: Int): DpadState? = when (hatValue) {
            0 -> North
            1 -> NorthEast
            2 -> East
            3 -> SouthEast
            4 -> South
            5 -> SouthWest
            6 -> West
            7 -> NorthWest
            8 -> Center
            else -> null
        }

        /**
         * The 4-bit "hat switch" integer counterpart of
         * [fromHatSwitch]. The inverse mapping is total over the
         * enum, so this is the wire form a HID report emits.
         */
        fun toHatSwitch(state: DpadState): Int = when (state) {
            North -> 0
            NorthEast -> 1
            East -> 2
            SouthEast -> 3
            South -> 4
            SouthWest -> 5
            West -> 6
            NorthWest -> 7
            Center -> 8
        }
    }
}
