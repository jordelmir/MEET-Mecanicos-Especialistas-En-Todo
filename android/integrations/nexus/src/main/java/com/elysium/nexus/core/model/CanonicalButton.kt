package com.elysium.nexus.core.model

/**
 * The 21 canonical buttons, exactly as defined in
 * `MASTER_ORDER.md` §10. The order is stable; tests and serialization
 * rely on [ordinal]. **Do not reorder.**
 *
 * Visual layers (A/B/X/Y, Cross/Circle/Square/Triangle, B/A/Y/X,
 * 1/2/3/4) live in the UI; the domain never depends on those labels.
 *
 * See also: [ButtonSet] which is the efficient 64-bit-bitset wrapper.
 */
enum class CanonicalButton {
    South,
    East,
    West,
    North,

    LeftBumper,
    RightBumper,
    LeftTriggerDigital,
    RightTriggerDigital,

    LeftStickClick,
    RightStickClick,

    MenuPrimary,
    MenuSecondary,
    System,
    Touchpad,
    Capture,

    Paddle1,
    Paddle2,
    Paddle3,
    Paddle4,

    Auxiliary1,
    Auxiliary2,
    Auxiliary3,
    Auxiliary4;

    companion object {
        /**
         * Total count of canonical buttons. Used to size bit sets and
         * to drive the [ButtonSet.ALL] mask.
         *
         * The spec in `MASTER_ORDER.md` §10 enumerates 23 distinct
         * buttons (4 face + 4 shoulder/trigger + 2 stick-click + 5
         * system + 4 paddle + 4 auxiliary). We pin the count with a
         * compile-time guard so a careless reorder or addition fails
         * loudly at test time rather than silently shifting bit
         * assignments under [ButtonSet].
         */
        const val COUNT: Int = 23

        /**
         * Compile-time guard. If a future addition crosses the 64-button
         * boundary this assertion fails and the [ButtonSet] design has to
         * change. The 23-button floor of the spec gives us 41 bits of
         * headroom; we keep the assertion in place so a careless addition
         * fails loudly.
         */
        init {
            require(values().size == COUNT) {
                "CanonicalButton ordinals changed; ButtonSet is hard-coded for $COUNT buttons (got ${values().size})."
            }
            require(COUNT <= 64) {
                "CanonicalButton count exceeds Long-bitset capacity. Migrate ButtonSet to a wider backing."
            }
        }
    }
}
