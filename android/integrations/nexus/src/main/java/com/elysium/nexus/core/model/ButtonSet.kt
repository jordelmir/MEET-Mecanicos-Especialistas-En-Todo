package com.elysium.nexus.core.model

/**
 * Efficient 64-bit bitset for the 21 canonical buttons.
 *
 * Why a [value class] wrapping [Long]? Because the canonical state is
 * sent on the wire tens of times per second per stick sample (latest-wins
 * frames per `MASTER_ORDER.md` §19.3) and the cost of an `EnumSet<…>` or
 * a `Set<CanonicalButton>` per frame is measurable. A `Long` bitset is
 * 8 bytes flat; the inliner handles the boxing at call sites.
 *
 * Each button is mapped to a single bit by [ordinal]. The compile-time
 * guard in [CanonicalButton.Companion] keeps the total under 64.
 *
 * Iteration over pressed buttons uses [forEachPressed] to avoid
 * materialising a `Set<CanonicalButton>` unless the caller actually
 * needs one.
 */
@JvmInline
value class ButtonSet(val bits: Long) {

    /** @return true if [button] is currently held. */
    fun isPressed(button: CanonicalButton): Boolean {
        val mask = 1L shl button.ordinal
        return (bits and mask) != 0L
    }

    /**
     * Returns a new [ButtonSet] with [button]'s pressed state set to
     * [pressed]. The receiver is not mutated.
     */
    fun with(button: CanonicalButton, pressed: Boolean): ButtonSet {
        val mask = 1L shl button.ordinal
        return if (pressed) {
            ButtonSet(bits or mask)
        } else {
            ButtonSet(bits and mask.inv())
        }
    }

    /** Number of currently-pressed buttons. */
    fun size(): Int = java.lang.Long.bitCount(bits)

    /** @return true if no button is currently held. */
    fun isEmpty(): Boolean = bits == 0L

    /**
     * Iterate every pressed button. Allocates an iterator only if the
     * caller pulls all values out; for-each over a `Long` mask walks the
     * word directly.
     */
    inline fun forEachPressed(action: (CanonicalButton) -> Unit) {
        var remaining = bits
        while (remaining != 0L) {
            val ordinal = java.lang.Long.numberOfTrailingZeros(remaining)
            if (ordinal >= CanonicalButton.COUNT) break
            action(CanonicalButton.values()[ordinal])
            remaining = remaining and (remaining - 1L)
        }
    }

    /**
     * Materialise the pressed set. Use only when the consumer truly
     * needs a collection (e.g. a serialization step that doesn't care
     * about allocation cost). For iteration, prefer [forEachPressed].
     */
    fun pressed(): List<CanonicalButton> = buildList(size()) {
        forEachPressed { add(it) }
    }

    companion object {
        /** No buttons pressed. The starting point of every state. */
        val EMPTY: ButtonSet = ButtonSet(0L)

        /**
         * All 21 buttons pressed. Mostly useful for the §38 disconnect
         * test (hold all four face buttons + shoulders, then cut the
         * link, then verify a neutral state).
         */
        val ALL: ButtonSet = ButtonSet((1L shl CanonicalButton.COUNT) - 1L)
    }
}
