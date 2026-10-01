package com.elysium.nexus.core.model

/**
 * Canonical touch surface state.
 *
 * A list of currently-active [TouchPoint]s plus a cap on how many
 * touches the frame may carry. The cap is per the §9 rule that
 * "oversized frames" are rejected.
 *
 * Why a class instead of a `List<TouchPoint>`? Because we want a
 * single place to express:
 *
 *  - the cap (`MAX_TOUCHES`),
 *  - the empty state (`EMPTY`),
 *  - the validation contract.
 *
 * The collection is immutable; to update it, build a new one with
 * [copy] or [fromList].
 */
data class TouchCollection(
    val points: List<TouchPoint>
) {
    init {
        // We *do not* call validate() here. Data-class init blocks
        // are not supposed to throw on user-facing business errors;
        // we want the engine to decide whether to drop, clamp, or
        // surface the failure. A list over the cap is a recoverable
        // condition in some hosts; we let the upper layer choose.
        //
        // The cap *is* an invariant from the engine's perspective,
        // and the engine enforces it via [validate] before producing
        // a state. We document the invariant here so a future
        // contributor does not silently grow the list.
        require(points.size <= MAX_TOUCHES) {
            "TouchCollection cannot carry more than $MAX_TOUCHES points (got ${points.size})."
        }
    }

    /** @return `true` if no finger is currently on the surface. */
    fun isEmpty(): Boolean = points.isEmpty()

    /** @return the number of currently-active touches. */
    fun size(): Int = points.size

    companion object {
        /**
         * The maximum number of touches a single frame may carry.
         *
         * The exact value is a product decision; it is high enough
         * to cover every gamepad-style multitouch scenario
         * (DualSense / DualShock / Switch allow 2; the touchpad
         * itself allows 10; some emulators go higher). We pick 10
         * to match the touchpad budget on the highest-end official
         * device. §9 calls this "oversized frame" when violated.
         */
        const val MAX_TOUCHES: Int = 10

        /** No touches active. The neutral value per §32. */
        val EMPTY: TouchCollection = TouchCollection(emptyList())

        /**
         * Validate a touch collection: each point must be valid, and
         * the collection must not exceed the cap.
         */
        fun validate(collection: TouchCollection): ValidationResult {
            val errors = buildList {
                if (collection.points.size > MAX_TOUCHES) {
                    add(
                        ValidationError.FrameTooLarge(
                            field = "points",
                            actual = collection.points.size,
                            max = MAX_TOUCHES
                        )
                    )
                }
                collection.points.forEachIndexed { i, point ->
                    when (val r = TouchPoint.validate(point)) {
                        is ValidationResult.Valid -> Unit
                        is ValidationResult.Invalid -> {
                            r.errors.forEach { nested ->
                                add(
                                    ValidationError.IncompatibleState(
                                        field = "points[$i].${nested.fieldName()}",
                                        reason = nested.reasonText()
                                    )
                                )
                            }
                        }
                    }
                }
            }
            return ValidationResult.of(errors)
        }

        private fun ValidationError.fieldName(): String = when (this) {
            is ValidationError.NaN -> field
            is ValidationError.Infinity -> field
            is ValidationError.OutOfRange -> field
            is ValidationError.IntegerOutOfRange -> field
            is ValidationError.FrameTooLarge -> field
            is ValidationError.IncompatibleState -> field
        }

        private fun ValidationError.reasonText(): String = when (this) {
            is ValidationError.NaN -> "NaN"
            is ValidationError.Infinity -> "Infinity"
            is ValidationError.OutOfRange ->
                "value $value out of [$min, $max]"
            is ValidationError.IntegerOutOfRange ->
                "value $value out of [$min, $max]"
            is ValidationError.FrameTooLarge ->
                "size $actual > $max"
            is ValidationError.IncompatibleState -> reason
        }
    }
}
