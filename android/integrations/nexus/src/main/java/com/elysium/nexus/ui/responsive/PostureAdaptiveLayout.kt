package com.elysium.nexus.ui.responsive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.posture.Posture
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * Phase ULT.6 — Foldable-posture-aware layout.
 *
 * The Mac/Universal control surface adapts to
 * the device's foldable posture:
 *
 *  - **OPEN** (flat or fully unfolded): the
 *    content fills the screen, the trackpad
 *    sits in the middle, the modifier bar at
 *    the bottom.
 *  - **HALF_OPENED** (tabletop / laptop, the
 *    user folded the device to ~90°): the
 *    screen is split **vertically** along the
 *    hinge. The top half is the **trackpad**
 *    (gesture area); the bottom half is the
 *    **keyboard + bar** (input area). The user
 *    props the device on the table and uses it
 *    like a laptop trackpad + keyboard.
 *  - **CLOSED** (cover screen only, e.g. the
 *    Honor Magic V2 folded shut): a compact
 *    single-screen view; the user sees a
 *    "desplegar para usar" lock screen.
 *  - **FLAT / UNKNOWN**: same as OPEN.
 *
 * The composable takes three slots:
 *
 *  - [topSlot] is the **gesture area** (trackpad
 *    on the Mac/Universal surface, button grid
 *    on the TV surface, etc.). In OPEN mode it
 *    fills the middle; in HALF_OPENED mode it
 *    fills the **top half** above the hinge.
 *  - [bottomSlot] is the **input area** (the
 *    keyboard + modifier bar). In OPEN mode it
 *    sits at the bottom; in HALF_OPENED mode
 *    it fills the **bottom half** below the
 *    hinge.
 *  - [closedSlot] is what the user sees on the
 *    cover screen. Only used in CLOSED mode.
 *
 * The composable is the §16 "posture-driven
 * layout" surface. It is intentionally small
 * (~80 lines) and content-agnostic: the
 * trackpad, the keyboard, and the TV buttons
 * are all slots.
 *
 * ## Why a slot composable
 *
 * The Mac control surface, the Universal
 * control surface, and the TV control surface
 * all need to adapt to the posture. Instead of
 * duplicating the OPEN / HALF_OPENED /
 * CLOSED logic in every screen, the screens
 * just provide three slots and the composable
 * picks the right one. New screens (e.g. a
 * gamepad mapping UI) get the posture-aware
 * layout for free.
 */
@Composable
fun PostureAdaptiveLayout(
    posture: Posture,
    topSlot: @Composable () -> Unit,
    bottomSlot: @Composable () -> Unit,
    closedSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (posture) {
            Posture.CLOSED -> {
                closedSlot()
            }
            Posture.HALF_OPENED -> {
                // Tabletop / laptop. The screen is
                // split horizontally: top = gesture
                // area, bottom = input area. The
                // user props the device on a table
                // and uses the top half like a
                // trackpad, the bottom half like a
                // keyboard.
                Column(modifier = Modifier.fillMaxSize()) {
                    HingeBadge(text = "Top half: gestures")
                    Box(modifier = Modifier.weight(1f)) {
                        topSlot()
                    }
                    HingeDivider()
                    Box(modifier = Modifier.weight(1f)) {
                        bottomSlot()
                    }
                    HingeBadge(text = "Bottom half: keyboard")
                }
            }
            Posture.OPEN,
            Posture.FLAT,
            Posture.UNKNOWN -> {
                // Default layout. Top slot fills
                // the middle; bottom slot sits at
                // the bottom.
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        topSlot()
                    }
                    bottomSlot()
                }
            }
        }
    }
}

/**
 * A small "TOP / BOTTOM HALF" badge rendered
 * along the hinge in HALF_OPENED mode. Lets
 * the user know which half is which.
 */
@Composable
private fun HingeBadge(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonStatusPill(
            label = text,
            color = ElysiumColors.NeonOrange
        )
    }
}

/**
 * The horizontal divider along the hinge in
 * HALF_OPENED mode. A thin neon-orange line
 * that visually separates the two halves.
 */
@Composable
private fun HingeDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ElysiumColors.NeonOrange.copy(alpha = 0f),
                            ElysiumColors.NeonOrange,
                            ElysiumColors.NeonOrange.copy(alpha = 0f)
                        )
                    )
                )
        )
    }
}

/**
 * A pre-built "desplegar para usar" cover-screen
 * content. Shown on the cover screen (folded
 * clamshell) when the app is in the foreground.
 */
@Composable
fun CoverScreenContent(
    appName: String = "Elysium Nexus",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ElysiumColors.Surface,
                        ElysiumColors.SurfaceHigh
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ElysiumColors.NeonPurple.copy(alpha = 0.2f))
                    .border(2.dp, ElysiumColors.NeonPurple, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = ElysiumColors.NeonPurple,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = appName,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Desplega el dispositivo para usar el control remoto.",
                style = TextStyle(fontSize = 13.sp),
                color = ElysiumColors.OnSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
