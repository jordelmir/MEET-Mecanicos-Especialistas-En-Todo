package com.elysium.nexus.ui.responsive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elysium.nexus.core.posture.Posture

/**
 * The screen-size buckets the §15 hierarchy adapts to.
 *
 * Per `MASTER_ORDER.md` §16, the editor shall adapt
 * to the device size:
 *
 *  - **Compact** — phones in portrait (≤ 600dp wide).
 *    The hub is 1 column; the device picker is a
 *    vertical list.
 *  - **Medium** — phones in landscape, small tablets
 *    (601-840dp wide). The hub is 2 columns; the
 *    device picker is a 2-column grid.
 *  - **Expanded** — tablets, foldables open, large
 *    phones (841-1200dp wide). The hub is 3 columns;
 *    the device picker is a 3-column grid.
 *  - **Large** — desktops, foldables open landscape,
 *    Chromebooks (≥ 1201dp wide). The hub is 4
 *    columns; the device picker is a 4-column grid.
 *
 * The buckets are the Material 3 [Window Size Class]
 * defaults, plus a "Large" bucket for the
 * desktop / Chrome OS case.
 */
enum class ScreenSize {
    Compact,    // ≤ 600dp
    Medium,     // 601-840dp
    Expanded,   // 841-1200dp
    Large;      // ≥ 1201dp

    companion object {
        fun fromWidth(widthDp: Dp): ScreenSize = when {
            widthDp < 600.dp -> Compact
            widthDp < 840.dp -> Medium
            widthDp < 1200.dp -> Expanded
            else -> Large
        }

        /**
         * The number of columns the hub / picker
         * uses for this screen size. The TV
         * connection flow is always 1 column.
         */
        fun columnCount(size: ScreenSize): Int = when (size) {
            Compact -> 1
            Medium -> 2
            Expanded -> 3
            Large -> 4
        }
    }
}

/**
 * `BoxWithConstraints`-driven layout primitive that
 * gives you a [ScreenSize] and an [ScreenInfo]
 * based on the current window size. The caller
 * passes a lambda that renders the content; the
 * lambda gets the screen info and a [Modifier] for
 * the outer container.
 *
 * The `BoxWithConstraints` is the standard Compose
 * way to read the available size. We use it to
 * decide the column count, the side padding, the
 * title size, etc.
 *
 * The optional [posture] parameter lets the caller
 * pass the current foldable posture. The
 * [ScreenInfo] exposes it so the layout can adapt
 * (e.g. switch to a two-pane layout in
 * HALF_OPENED).
 */
@Composable
fun ResponsiveContainer(
    modifier: Modifier = Modifier,
    posture: Posture = Posture.UNKNOWN,
    content: @Composable (ScreenInfo) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val info = ScreenInfo(
            size = ScreenSize.fromWidth(maxWidth),
            widthDp = maxWidth,
            heightDp = maxHeight,
            columns = ScreenSize.columnCount(ScreenSize.fromWidth(maxWidth)),
            posture = posture
        )
        content(info)
    }
}

/**
 * Snapshot of the current screen size + posture.
 * The [ResponsiveContainer] builds this once per
 * recomposition and passes it to the content
 * lambda.
 */
data class ScreenInfo(
    val size: ScreenSize,
    val widthDp: Dp,
    val heightDp: Dp,
    val columns: Int,
    val posture: Posture = Posture.UNKNOWN
) {
    /**
     * The side padding the hub / picker use.
     * Smaller padding on compact (the screen is
     * already tight); more padding on large
     * (the content should breathe).
     */
    val sidePadding: Dp = when (size) {
        ScreenSize.Compact -> 12.dp
        ScreenSize.Medium -> 16.dp
        ScreenSize.Expanded -> 24.dp
        ScreenSize.Large -> 32.dp
    }

    /**
     * The vertical spacing between cards in the
     * grid. Tighter on compact, looser on
     * large.
     */
    val cardSpacing: Dp = when (size) {
        ScreenSize.Compact -> 10.dp
        ScreenSize.Medium -> 12.dp
        ScreenSize.Expanded -> 16.dp
        ScreenSize.Large -> 20.dp
    }

    /**
     * Whether the screen is in a narrow posture
     * (e.g. cover screen, half-opened tabletop).
     * The hub uses this to choose between a
     * single-column hero + scrollable list and
     * a side-by-side two-pane layout.
     */
    val isNarrow: Boolean = widthDp < 480.dp
}
