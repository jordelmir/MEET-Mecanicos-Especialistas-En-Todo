package com.elysium.nexus.ui.editor

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlElement
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile

/**
 * The editor's pure-data actions.
 *
 * `MASTER_ORDER.md` §15 calls for a controls editor
 * with "drag, scale, rotate, duplicate, group, lock,
 * align, distribute, opacity". Each of these is a
 * pure-data transformation on the [Profile] — the
 * function takes the current profile and returns a
 * new one with the change applied.
 *
 * The Compose composables ([EditorCanvas],
 * [EditorToolbar], [ProfileSelector]) are the
 * Android adapters. They observe the [Profile],
 * dispatch a gesture / chip tap, and call one of
 * the functions here to compute the new state.
 * The functions are **stateless** and **JVM-
 * testable** — the test surface is the same data
 * that the production code uses, so the unit tests
 * verify the editor's *semantics* without going
 * through Compose's runtime.
 *
 * ## Why a static class and not an `interface` or
 * `class` with DI
 *
 * The functions are pure: the only state they read
 * is the inputs. A `class` instance would have no
 * fields, so a `class` adds no value over a top-
 * level `object`. A `companion object` inside the
 * composable would couple the test to the
 * composable; a separate file keeps the test
 * surface decoupled.
 */
object EditorActions {

    /**
     * Build a fresh [ControlElement] with the given
     * [id] and [kind]. The position is centred
     * ([NormalizedRect.CENTERED_SMALL]); the user
     * drags the control to its final position.
     */
    fun createControl(id: Int, kind: ControlKind): ControlElement {
        val bounds = NormalizedRect.CENTERED_SMALL
        val binding: CanonicalBinding = when (kind) {
            ControlKind.Button -> CanonicalBinding.Neutralize
            ControlKind.Stick -> CanonicalBinding.Stick(StickSide.Left)
            ControlKind.Trigger -> CanonicalBinding.Trigger(StickSide.Left)
        }
        val type: ControlType = when (kind) {
            ControlKind.Button -> ControlType.Button
            ControlKind.Stick -> ControlType.Stick
            ControlKind.Trigger -> ControlType.Trigger
        }
        return ControlElement(
            id = id,
            type = type,
            visualBounds = bounds,
            binding = binding
        )
    }

    /**
     * Add a new control of [kind] to the [profile].
     * The new control's id is `max(existing) + 1`
     * (or 0 if the profile is empty). The
     * `updatedAt` is set to [now].
     */
    fun addControl(profile: Profile, kind: ControlKind, now: Long): Profile {
        val newId = (profile.controls.maxOfOrNull { it.id } ?: -1) + 1
        return profile.withControlAdded(createControl(newId, kind), now)
    }

    /**
     * Remove the control with [controlId] from the
     * [profile]. The `updatedAt` is set to [now].
     * If no such control exists, the profile is
     * returned unchanged.
     */
    fun removeControl(profile: Profile, controlId: Int, now: Long): Profile =
        profile.withControlRemoved(controlId, now)

    /**
     * Move the control with [controlId] to the new
     * [newVisualBounds]. The bounds are already
     * clamped (the editor's gesture pipeline does
     * the clamping). The `updatedAt` is set to
     * [now].
     */
    fun moveControl(
        profile: Profile,
        controlId: Int,
        newVisualBounds: NormalizedRect,
        now: Long
    ): Profile = profile.withControlReplaced(
        controlId = controlId,
        updated = profile.controls.first { it.id == controlId }
            .copy(visualBounds = newVisualBounds),
        now = now
    )

    /**
     * Resize the control with [controlId] to the
     * new [newWidth] and [newHeight] (normalised).
     * The bounds are clamped in
     * [ControlElement.resized]. The `updatedAt` is
     * set to [now].
     */
    fun resizeControl(
        profile: Profile,
        controlId: Int,
        newWidth: Float,
        newHeight: Float,
        now: Long
    ): Profile = profile.withControlReplaced(
        controlId = controlId,
        updated = profile.controls.first { it.id == controlId }
            .resized(newWidth = newWidth, newHeight = newHeight),
        now = now
    )

    /**
     * Rotate the control with [controlId] to the
     * new [newRotation] (degrees, normalised to
     * `[0, 360]` in [ControlElement.rotated]). The
     * `updatedAt` is set to [now].
     */
    fun rotateControl(
        profile: Profile,
        controlId: Int,
        newRotation: Float,
        now: Long
    ): Profile = profile.withControlReplaced(
        controlId = controlId,
        updated = profile.controls.first { it.id == controlId }
            .rotated(newRotation = newRotation),
        now = now
    )

    /**
     * Set the opacity of the control with [controlId]
     * to [newOpacity] (clamped to `[0, 1]` in
     * [ControlElement.withOpacity]). The `updatedAt`
     * is set to [now].
     */
    fun setOpacity(
        profile: Profile,
        controlId: Int,
        newOpacity: Float,
        now: Long
    ): Profile = profile.withControlReplaced(
        controlId = controlId,
        updated = profile.controls.first { it.id == controlId }
            .withOpacity(newOpacity = newOpacity),
        now = now
    )

    // ---- Alignment (§15) ----
    //
    // Alignment moves a control's `x` or `y`
    // coordinate to match a reference value. The
    // reference is the bounding box of the
    // *currently-selected* controls (or, if no
    // selection, the bounding box of *every* other
    // control). The editor exposes the alignment
    // action as a single button per axis (left,
    // right, top, bottom); the function takes the
    // full alignment intent.

    /**
     * Move the control with [controlId] so its
     * left edge aligns with the leftmost edge of
     * the other controls. If the profile has fewer
     * than 2 controls, the profile is returned
     * unchanged.
     */
    fun alignLeft(profile: Profile, controlId: Int, now: Long): Profile {
        if (profile.controls.size < 2) return profile
        val target = profile.controls
            .filter { it.id != controlId }
            .minOf { it.visualBounds.x }
        return profile.withControlReplaced(
            controlId = controlId,
            updated = profile.controls.first { it.id == controlId }
                .copy(visualBounds = profile.controls.first { it.id == controlId }
                    .visualBounds.copy(x = target)),
            now = now
        )
    }

    /**
     * Move the control with [controlId] so its
     * right edge aligns with the rightmost edge of
     * the other controls. The right edge is the
     * control's `x + width`.
     */
    fun alignRight(profile: Profile, controlId: Int, now: Long): Profile {
        if (profile.controls.size < 2) return profile
        val target = profile.controls
            .filter { it.id != controlId }
            .maxOf { it.visualBounds.x + it.visualBounds.width }
        val control = profile.controls.first { it.id == controlId }
        val newX = (target - control.visualBounds.width).coerceIn(0f, 1f - control.visualBounds.width)
        return profile.withControlReplaced(
            controlId = controlId,
            updated = control.copy(visualBounds = control.visualBounds.copy(x = newX)),
            now = now
        )
    }

    /**
     * Move the control with [controlId] so its
     * top edge aligns with the topmost edge of the
     * other controls.
     */
    fun alignTop(profile: Profile, controlId: Int, now: Long): Profile {
        if (profile.controls.size < 2) return profile
        val target = profile.controls
            .filter { it.id != controlId }
            .minOf { it.visualBounds.y }
        return profile.withControlReplaced(
            controlId = controlId,
            updated = profile.controls.first { it.id == controlId }
                .copy(visualBounds = profile.controls.first { it.id == controlId }
                    .visualBounds.copy(y = target)),
            now = now
        )
    }

    /**
     * Move the control with [controlId] so its
     * bottom edge aligns with the bottommost edge
     * of the other controls.
     */
    fun alignBottom(profile: Profile, controlId: Int, now: Long): Profile {
        if (profile.controls.size < 2) return profile
        val target = profile.controls
            .filter { it.id != controlId }
            .maxOf { it.visualBounds.y + it.visualBounds.height }
        val control = profile.controls.first { it.id == controlId }
        val newY = (target - control.visualBounds.height).coerceIn(0f, 1f - control.visualBounds.height)
        return profile.withControlReplaced(
            controlId = controlId,
            updated = control.copy(visualBounds = control.visualBounds.copy(y = newY)),
            now = now
        )
    }

    // ---- Distribution (§15) ----
    //
    // Distribution evenly spaces the controls along
    // an axis. The first and last controls keep
    // their positions; the controls in between are
    // placed at the equally-spaced positions.

    /**
     * Distribute the controls' *centers* evenly
     * along the x axis. The leftmost and rightmost
     * controls keep their positions; the rest are
     * placed at the equally-spaced positions. The
     * profile needs at least 3 controls to be
     * distributed (the first and last are the
     * anchors).
     */
    fun distributeHorizontally(profile: Profile, now: Long): Profile {
        if (profile.controls.size < 3) return profile
        val sorted = profile.controls.sortedBy { it.visualBounds.x }
        val first = sorted.first()
        val last = sorted.last()
        val firstCenterX = first.visualBounds.x + first.visualBounds.width / 2f
        val lastCenterX = last.visualBounds.x + last.visualBounds.width / 2f
        val step = (lastCenterX - firstCenterX) / (sorted.size - 1)
        val middle = sorted.drop(1).dropLast(1)
        val updated = middle.mapIndexed { i, control ->
            val newCenterX = firstCenterX + step * (i + 1)
            val newX = (newCenterX - control.visualBounds.width / 2f)
                .coerceIn(0f, 1f - control.visualBounds.width)
            control.copy(visualBounds = control.visualBounds.copy(x = newX))
        }
        val byId = updated.associateBy { it.id }
        val newControls = profile.controls.map { c -> byId[c.id] ?: c }
        return profile.copy(controls = newControls, updatedAt = now)
    }

    /**
     * Distribute the controls' *centers* evenly
     * along the y axis. The topmost and bottommost
     * controls keep their positions; the rest are
     * placed at the equally-spaced positions. The
     * profile needs at least 3 controls to be
     * distributed.
     */
    fun distributeVertically(profile: Profile, now: Long): Profile {
        if (profile.controls.size < 3) return profile
        val sorted = profile.controls.sortedBy { it.visualBounds.y }
        val first = sorted.first()
        val last = sorted.last()
        val firstCenterY = first.visualBounds.y + first.visualBounds.height / 2f
        val lastCenterY = last.visualBounds.y + last.visualBounds.height / 2f
        val step = (lastCenterY - firstCenterY) / (sorted.size - 1)
        val middle = sorted.drop(1).dropLast(1)
        val updated = middle.mapIndexed { i, control ->
            val newCenterY = firstCenterY + step * (i + 1)
            val newY = (newCenterY - control.visualBounds.height / 2f)
                .coerceIn(0f, 1f - control.visualBounds.height)
            control.copy(visualBounds = control.visualBounds.copy(y = newY))
        }
        val byId = updated.associateBy { it.id }
        val newControls = profile.controls.map { c -> byId[c.id] ?: c }
        return profile.copy(controls = newControls, updatedAt = now)
    }

    // ---- HitBounds (§15 "Aumentar hitbox") ----
    //
    // The hitBounds is the rect that consumes
    // touches. By default it equals the visualBounds.
    // The user can grow the hitbox beyond the
    // visual rect (e.g. a 50x50 px visual button with
    // a 100x100 px hitbox). The grow is independent
    // of the visual rect.

    /**
     * Set the hitBounds of the control with
     * [controlId] to [newHitBounds]. The visualBounds
     * is unchanged. The `updatedAt` is set to [now].
     * If [newHitBounds] is invalid (out of `[0, 1]`),
     * the function throws — the editor's input
     * validation happens at the gesture level, so a
     * throw is a "should never happen" diagnostic.
     */
    fun setHitBounds(
        profile: Profile,
        controlId: Int,
        newHitBounds: NormalizedRect,
        now: Long
    ): Profile = profile.withControlReplaced(
        controlId = controlId,
        updated = profile.controls.first { it.id == controlId }
            .copy(hitBounds = newHitBounds),
        now = now
    )

    /**
     * @return the next id that
     * [addControl] will use for a new control.
     * Useful for tests that want to know what id
     * the new control will have without having to
     * inspect the profile after the call.
     */
    fun nextControlId(profile: Profile): Int =
        (profile.controls.maxOfOrNull { it.id } ?: -1) + 1
}
