package com.elysium.nexus.ui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.elysium.nexus.core.model.TouchPoint
import com.elysium.nexus.input.TouchSurfaceView

/**
 * A Compose `AndroidView` host for the [TouchSurfaceView].
 *
 * `MASTER_ORDER.md` §11 says the analog input path
 * shall be evaluated with a specialised `View` because
 * `MotionEvent` exposes historical samples, allows
 * precise per-`pointerId` tracking, reduces
 * recompositions, and gives more control over
 * cancellations. This composable hosts that view
 * inside the Compose tree so:
 *
 *  - The Compose tree is the **single touch
 *    arbiter.** A touch on a control's hitBounds is
 *    consumed by the editor's `pointerInput`; a touch
 *    outside any control flows through to the
 *    `TouchSurfaceView` (via Compose's hierarchical
 *    dispatch).
 *  - The view does not need to be added to a
 *    `FrameLayout` separately — it is part of the
 *    same composition.
 *  - The view's lifecycle (attach / detach) is
 *    managed by `AndroidView`'s factory / update
 *    callbacks, not by the activity.
 *
 * ## Why `AndroidView` and not a `Compose` touch handler
 *
 * Phase 1.3 keeps the §11 view as the analog input
 * path. The Compose UI (toolbar, editor, profile
 * selector) is for non-critical UI; the touch stream
 * is the hot path. The split mirrors the §11 spec.
 *
 * ## Why this fixes Bug #18
 *
 * Phase 1.1 added the editor in a `FrameLayout` with
 * the `TouchSurfaceView` on top. The view consumed
 * every event before the editor saw it. Phase 1.2
 * swapped the order (ComposeView on top) so the
 * toolbar's chips were clickable — but the
 * `TouchSurfaceView` no longer received any events.
 * The fix: put the view **inside** the Compose tree
 * as an `AndroidView` *behind* the editor. The
 * editor's `pointerInput` consumes touches inside
 * control hitBoxes; everything else falls through to
 * the view. The Compose `Modifier` chain is the
 * arbiter; the ViewGroup dispatch is a no-op because
 * the view is *inside* the Compose hierarchy.
 */
@Composable
fun TouchSurfaceViewHost(
    onTouchPointChange: (id: Int, point: TouchPoint?, t0Ns: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    // The view is created once per composition. The
    // `update` block re-wires the callback if the
    // caller passes a new lambda (a stale closure
    // would otherwise point to the old engine
    // reference).
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            TouchSurfaceView(ctx).apply {
                this.onTouchPointChange = onTouchPointChange
            }
        },
        update = { view ->
            view.onTouchPointChange = onTouchPointChange
        }
    )
}
