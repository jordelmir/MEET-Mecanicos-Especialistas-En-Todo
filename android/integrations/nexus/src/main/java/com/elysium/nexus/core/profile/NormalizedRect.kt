package com.elysium.nexus.core.profile

/**
 * A rectangle in normalized coordinates `[0, 1] x [0, 1]`.
 *
 * The control editor uses normalized coordinates so a
 * profile is portable across device sizes. The same
 * profile works on Honor Magic V2, Pixel 9, a tablet,
 * or an Android TV; the editor scales the rect to the
 * device's actual pixel dimensions at render time.
 *
 * The y axis is the conventional "screen y" (top = 0,
 * bottom = 1), matching the [com.elysium.nexus.core.touch.PointerInfo]
 * convention. The `width` and `height` are in `[0, 1]`,
 * not pixel counts.
 *
 * Per `MASTER_ORDER.md` §16, the editor must support
 * foldable postures (open, half-folded, tabletop) and
 * the cover screen. Normalized coordinates are the
 * abstraction that lets a profile translate across
 * postures: the editor maps the profile's normalized
 * rect to the active posture's pixel dimensions at
 * render time. Phase 1.2 adds the foldable-aware
 * mapping.
 */
data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    init {
        require(x in 0f..1f) { "x must be in [0, 1] (got $x)." }
        require(y in 0f..1f) { "y must be in [0, 1] (got $y)." }
        require(width > 0f && width <= 1f) {
            "width must be in (0, 1] (got $width)."
        }
        require(height > 0f && height <= 1f) {
            "height must be in (0, 1] (got $height)."
        }
        require(x + width <= 1f) {
            "x + width ($x + $width) must be <= 1."
        }
        require(y + height <= 1f) {
            "y + height ($y + $height) must be <= 1."
        }
    }

    companion object {
        /** A small button-sized rect at the centre. Useful for tests. */
        val CENTERED_SMALL: NormalizedRect = NormalizedRect(
            x = 0.4f, y = 0.4f, width = 0.2f, height = 0.2f
        )
    }
}
