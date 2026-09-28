package com.elysium.nexus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlElement
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The first controls editor canvas.
 *
 * `MASTER_ORDER.md` §15 says the user can "create
 * controls from scratch" with "drag, scale, rotate,
 * duplicate, group, lock, align, distribute, opacity".
 * The canvas has grown across phases:
 *
 *  - Phase 1.1: render + drag (`detectDragGestures`).
 *  - Phase 1.2: `selectedId` outline + EditorToolbar
 *    "Add" / "Save" / "Reset" chips.
 *  - Phase 1.3: scale (pinch) + rotate (two-finger
 *    twist) via `detectTransformGestures`, long-press
 *    to delete via `detectTapGestures(onLongPress)`.
 *    The "drag" gesture is now inside a
 *    `Box` that hosts the `TouchSurfaceView` via
 *    `AndroidView` (Phase 1.3's Bug #18 fix).
 *
 * Phase 1.4+ adds: opacity slider (per-control), the
 * `hitBounds` editor, the alignment / distribution
 * helpers, the import / export, the signature.
 *
 * ## Why a `Box` with `Modifier.offset` instead of
 * `Modifier.layout` or `Canvas`
 *
 * The first slice's requirement is "render the
 * control at its bounds, allow drag". The
 * `Modifier.offset { x, y }` is the simplest Compose
 * primitive that does this. `Canvas` is for custom
 * drawing (Phase 1.2+). `Modifier.layout` is for
 * custom measure (overkill here).
 *
 * ## Why the `onMoved` callback, not a state hoisted in
 * the activity
 *
 * The editor is a *projection* of the profile. The
 * profile is the source of truth; the editor observes
 * the profile via a `StateFlow` (or a `MutableState`
 * for the 1.1 in-memory case). The `onMoved` callback
 * is the *write* path: the activity updates the
 * profile, the editor recomposes. This is the same
 * pattern as the engine: a single source of truth, a
 * unidirectional flow.
 */
@Composable
fun EditorCanvas(
    profile: Profile,
    onMoved: (controlId: Int, newVisualBounds: NormalizedRect) -> Unit,
    onScaled: (controlId: Int, newWidth: Float, newHeight: Float) -> Unit,
    onRotated: (controlId: Int, newRotation: Float) -> Unit,
    onTapped: (controlId: Int) -> Unit,
    onLongPressed: (controlId: Int) -> Unit,
    selectedId: Int? = null,
    modifier: Modifier = Modifier
) {
    // The parent size, used to convert the control's
    // normalized bounds to pixel positions.
    val parentSize = remember { mutableStateOf(IntSize.Zero) }
    // Read the State at the TOP of the composable
    // so the EditorCanvas itself is invalidated
    // when the size changes. Without this, the
    // dependency is only inside the Box's content
    // lambda, which doesn't trigger a re-execution
    // of the forEach.
    val currentSize = parentSize.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12)) // brand_ink
            .onSizeChanged {
                parentSize.value = it
            }
    ) {
        // The forEach must read `parentSize.value` (not
        // just the captured `currentSize`) so the Box's
        // content lambda is invalidated when the size
        // changes. Without the State read inside the
        // lambda, the Box is not recomposed on size
        // change and the forEach is not re-executed.
        val sizeForControls = parentSize.value
        profile.controls.sortedBy { it.zIndex }.forEach { control ->
            ControlView(
                control = control,
                parentSize = sizeForControls,
                isSelected = selectedId == control.id,
                onMoved = onMoved,
                onScaled = onScaled,
                onRotated = onRotated,
                onTapped = onTapped,
                onLongPressed = onLongPressed
            )
        }
    }
}

/**
 * Render a single [ControlElement] as a draggable,
 * scalable, rotatable circle. The control's
 * `visualBounds` is converted to pixel positions using
 * the parent's size; the gestures update the visual
 * bounds and call [onMoved] / [onScaled] / [onRotated]
 * to persist the change.
 *
 * ## Why three `pointerInput` blocks and not one
 *
 * Compose's `pointerInput { }` blocks are stacked.
 * If we used a single block with all three
 * detectors, the first to claim the gesture would
 * win and the others would be silent. Splitting
 * into three blocks lets `detectTapGestures` and
 * `detectDragGestures` and `detectTransformGestures`
 * all observe the touch stream; each one consumes
 * only when its gesture is recognised.
 *
 * `detectTapGestures` consumes a `Press` only when
 * the gesture is recognised (a single tap or a
 * long press). The `detectDragGestures` /
 * `detectTransformGestures` blocks consume their
 * own events. The `change.consume()` calls mark
 * the events as handled, so the underlying
 * `pointerInput` tree does not propagate them
 * further (in particular, the `TouchSurfaceView`
 * behind the editor does not see them).
 */
@Composable
private fun ControlView(
    control: ControlElement,
    parentSize: IntSize,
    isSelected: Boolean,
    onMoved: (controlId: Int, newVisualBounds: NormalizedRect) -> Unit,
    onScaled: (controlId: Int, newWidth: Float, newHeight: Float) -> Unit,
    onRotated: (controlId: Int, newRotation: Float) -> Unit,
    onTapped: (controlId: Int) -> Unit,
    onLongPressed: (controlId: Int) -> Unit
) {
    if (parentSize == IntSize.Zero) return

    val density = LocalDensity.current
    val pixelWidthPx = with(density) { control.visualBounds.width.toDp() }
    val pixelHeightPx = with(density) { control.visualBounds.height.toDp() }
    val pixelX = with(density) { control.visualBounds.x.toDp() }
    val pixelY = with(density) { control.visualBounds.y.toDp() }

    val label = when (val b = control.binding) {
        is CanonicalBinding.Button -> b.button.name
        is CanonicalBinding.Stick -> "Stick ${b.side.name}"
        is CanonicalBinding.Trigger -> "Trigger ${b.side.name}"
        CanonicalBinding.Neutralize -> "Neutralize"
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (parentSize.width * control.visualBounds.x).roundToInt(),
                    y = (parentSize.height * control.visualBounds.y).roundToInt()
                )
            }
            .size(width = pixelWidthPx, height = pixelHeightPx)
            .rotate(control.rotation)
            .alpha(control.opacity)
            .background(
                color = when (control.binding) {
                    is CanonicalBinding.Neutralize -> Color(0xFFB42318) // brand_danger
                    else -> Color(0xFF1F6FEB) // brand_accent
                },
                shape = CircleShape
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFFF2F2F4), // brand_paper
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            // Tap detector: single tap selects the
            // control; long press deletes it. Both
            // gestures are recognised by the same
            // detector; the long-press recogniser
            // wins if the press is held past the
            // platform's long-press timeout (~500ms).
            .pointerInput(control.id) {
                detectTapGestures(
                    onTap = { onTapped(control.id) },
                    onLongPress = { onLongPressed(control.id) }
                )
            }
            // Drag detector: 1-finger drag moves the
            // control. The drag is normalised to the
            // parent's size. The drag also calls
            // `onTapped` to mark the control as
            // selected (the same effect as a tap).
            .pointerInput(control.id) {
                detectDragGestures(
                    onDragStart = { onTapped(control.id) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val parentW = parentSize.width.toFloat()
                        val parentH = parentSize.height.toFloat()
                        if (parentW <= 0f || parentH <= 0f) return@detectDragGestures
                        val dxNormalized = dragAmount.x / parentW
                        val dyNormalized = dragAmount.y / parentH
                        val moved = control.movedBy(dxNormalized, dyNormalized)
                        onMoved(control.id, moved.visualBounds)
                    }
                )
            }
            // Transform detector: 2-finger pinch +
            // twist. `detectTransformGestures` only
            // fires after the second pointer is
            // down; a 1-finger drag is left to
            // `detectDragGestures` above. The
            // detector calls `change.consume()` on
            // the centroid / pan / zoom / rotation
            // changes, so the underlying touch
            // surface does not see the gestures.
            .pointerInput(control.id) {
                detectTransformGestures(
                    onGesture = { _, _, zoom, rotationDelta ->
                        // Pan is ignored here because
                        // detectDragGestures handles
                        // 1-finger movement. Zoom and
                        // rotation are the 2-finger
                        // gestures.
                        if (zoom != 1f) {
                            val newW = (control.visualBounds.width * zoom)
                                .coerceIn(0.05f, 1f - control.visualBounds.x)
                            val newH = (control.visualBounds.height * zoom)
                                .coerceIn(0.05f, 1f - control.visualBounds.y)
                            onScaled(control.id, newW, newH)
                        }
                        if (rotationDelta != 0f) {
                            val newRotation = ((control.rotation + rotationDelta) % 360f + 360f) % 360f
                            onRotated(control.id, newRotation)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The minimum width / height of a control in
 * normalized coordinates. Below this, a pinch would
 * produce a 0-sized control. The threshold is 5% of
 * the parent's shorter axis — small enough that the
 * user can intentionally shrink to a corner marker,
 * large enough that a control is still visible and
 * tappable.
 */
private const val MIN_CONTROL_DIM: Float = 0.05f

/**
 * The maximum width / height of a control. The
 * upper bound is the parent's axis minus the
 * control's current offset (so the control cannot
 * overflow the parent). The `coerceIn` clamp is
 * applied per-axis at the call site.
 */
@Suppress("unused")
private fun maxControlDim(coord: Float, parentAxis: Float): Float =
    min(parentAxis - coord, parentAxis)

/**
 * A helper used in tests: the smallest of the two
 * axes of a control, in normalized `[0, 1]`
 * coordinates. Used as the minimum-size floor for
 * the §15 "hitbox" feature.
 */
@Suppress("unused")
internal fun minDim(rect: NormalizedRect): Float = max(MIN_CONTROL_DIM, min(rect.width, rect.height))
