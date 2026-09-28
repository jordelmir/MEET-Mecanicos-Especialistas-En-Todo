package com.elysium.nexus.core.profile

/**
 * A single control the user has placed on the touch
 * surface.
 *
 * `MASTER_ORDER.md` §15 specifies the data class. For
 * 1.1 the implementation is the §15 shape with the
 * following knobs:
 *
 *  - `id` — a stable identifier (UUID in 1.2+; the
 *    short-form `id: Int` is the 1.1 placeholder; the
 *    Room entity will have its own auto-generated id).
 *  - `type` — the kind of control (button, stick,
 *    trigger, dpad, touchpad).
 *  - `visualBounds` — the rect the control *draws*
 *    within, in normalized coordinates.
 *  - `hitBounds` — the rect the control *consumes
 *    touches* from. By default equal to `visualBounds`;
 *    the user can grow the hitbox beyond the visual
 *    bounds (§15 "Aumentar hitbox").
 *  - `zIndex` — draw order. Higher = on top.
 *  - `rotation` — visual rotation in degrees (0..360).
 *  - `opacity` — 0.0 (invisible) to 1.0 (opaque).
 *  - `binding` — what the control emits (canonical
 *    button, stick, trigger, etc.).
 *  - `behavior` — the per-type behaviour (Phase 1.2+).
 *  - `accessibility` — accessibility metadata
 *    (Phase 1.2+).
 *
 * ## Why the bounds are separate from the type
 *
 * A `Button` and a `Stick` are both drawn as a
 * rectangle; the difference is what they *do*. Keeping
 * the visual shape orthogonal to the type means the
 * editor can resize a control without changing its
 * semantic. A `Stick` of size 100x100 px is a small
 * thumbstick; the same `Stick` of size 200x200 is a
 * full-size analog stick. The editor cares only about
 * `visualBounds`; the `Mapping and Profile Engine`
 * cares only about `binding`.
 *
 * ## Why `id: Int` in 1.1
 *
 * The domain shape is a stable identifier; `Int` is the
 * 1.1 placeholder. Phase 1.2 promotes to `UUID` (or
 * `ULong` if we want to keep the JVM-native type). The
 * Room entity will have its own auto-generated `id`
 * (separate from the domain `id`); the mapping
 * domain-id ↔ entity-id lives in the repository.
 */
data class ControlElement(
    val id: Int,
    val type: ControlType,
    val visualBounds: NormalizedRect,
    val hitBounds: NormalizedRect = visualBounds,
    val zIndex: Int = 0,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val binding: CanonicalBinding
) {
    init {
        require(id >= 0) { "id must be non-negative (got $id)." }
        require(rotation in 0f..360f) {
            "rotation must be in [0, 360] (got $rotation)."
        }
        require(opacity in 0f..1f) {
            "opacity must be in [0, 1] (got $opacity)."
        }
    }

    /**
     * @return a copy of this element with the visual
     *   bounds moved by ([dx], [dy]) in normalized
     *   coordinates. The bounds are clamped to the
     *   valid `[0, 1] x [0, 1]` range. Used by the
     *   editor's drag gesture.
     */
    fun movedBy(dx: Float, dy: Float): ControlElement =
        copy(
            visualBounds = visualBounds.movedBy(dx, dy),
            hitBounds = hitBounds.movedBy(dx, dy)
        )

    /**
     * @return a copy of this element with the visual
     *   bounds resized to ([newWidth], [newHeight])
     *   in normalized coordinates. The bounds are
     *   clamped to the valid `[0, 1]` range on each
     *   axis. The position is unchanged; the
     *   control grows / shrinks toward the bottom-
     *   right. Used by the editor's pinch gesture.
     *
     * Phase 1.3+ replaces the "grow toward BR" with
     * a "grow from centre" or "grow from corner"
     * option (the §15 "transform" menu).
     */
    fun resized(newWidth: Float, newHeight: Float): ControlElement {
        val w = newWidth.coerceIn(0.05f, 1f - visualBounds.x)
        val h = newHeight.coerceIn(0.05f, 1f - visualBounds.y)
        return copy(
            visualBounds = NormalizedRect(
                x = visualBounds.x,
                y = visualBounds.y,
                width = w,
                height = h
            ),
            hitBounds = NormalizedRect(
                x = hitBounds.x,
                y = hitBounds.y,
                width = w,
                height = h
            )
        )
    }

    /**
     * @return a copy of this element with the visual
     *   rotation set to [newRotation] degrees, in
     *   the range `[0, 360]`. Used by the editor's
     *   two-finger twist gesture.
     */
    fun rotated(newRotation: Float): ControlElement {
        val r = ((newRotation % 360f) + 360f) % 360f
        return copy(rotation = r)
    }

    /**
     * @return a copy of this element with the visual
     *   opacity set to [newOpacity], clamped to
     *   `[0, 1]`. Used by the editor's opacity
     *   slider (§15 "opacidad").
     */
    fun withOpacity(newOpacity: Float): ControlElement {
        val o = newOpacity.coerceIn(0f, 1f)
        return copy(opacity = o)
    }
}

private fun NormalizedRect.movedBy(dx: Float, dy: Float): NormalizedRect {
    val newX = (x + dx).coerceIn(0f, 1f - width)
    val newY = (y + dy).coerceIn(0f, 1f - height)
    return NormalizedRect(newX, newY, width, height)
}
