package com.elysium369.meet.ui.components

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.theme.ThemeColors
import com.elysium369.meet.ui.theme.ColorEntry
import com.elysium369.meet.ui.theme.ColorCategory

// ═══════════════════════════════════════════════════════
// CUSTOMIZER TARGETS FOR SYSTEM THEME
// ═══════════════════════════════════════════════════════

private enum class SystemColorTarget(val label: String, val icon: String) {
    PRIMARY("Primario", "⚡"),
    SECONDARY("Secundario", "🔮"),
    TERTIARY("Terciario", "🚗"),
    QUATERNARY("Cuaternario", "🔬")
}

// ═══════════════════════════════════════════════════════
// MAIN SYSTEM THEME CUSTOMIZER DIALOG
// ═══════════════════════════════════════════════════════

@Composable
fun SystemThemeCustomizerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTarget by remember { mutableStateOf(SystemColorTarget.PRIMARY) }

    // Read current target color state dynamically
    val currentTargetColor = when (selectedTarget) {
        SystemColorTarget.PRIMARY -> MeetColors.neonGreen
        SystemColorTarget.SECONDARY -> MeetColors.electricBlue
        SystemColorTarget.TERTIARY -> MeetColors.cyberCyan
        SystemColorTarget.QUATERNARY -> MeetColors.hotMagenta
    }

    val inf = rememberInfiniteTransition(label = "systemCustomizer")
    val borderGlow by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "systemBorderGlow"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070B14),
                            Color(0xFF050810),
                            Color(0xFF03050A)
                        )
                    )
                )
                .border(
                    1.5.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            currentTargetColor.copy(alpha = borderGlow * 0.6f),
                            currentTargetColor.copy(alpha = borderGlow * 0.15f),
                            currentTargetColor.copy(alpha = borderGlow * 0.4f)
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ══════════════════════════════════════
                // HEADER
                // ══════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 16.dp, 16.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "🎨 Colores del Sistema",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Reset defaults
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22FF1744))
                                .clickable {
                                    MeetColors.reset(context)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AnimatedNeonGlyph(
                                    glyph = "↻",
                                    contentDescription = "Reset",
                                    tint = Color(0xFFFF1744),
                                    fontSize = 14.sp,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "Reset",
                                    color = Color(0xFFFF1744),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        // Close
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedNeonGlyph(
                                glyph = "✕",
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Text(
                    "Personaliza los colores primarios, secundarios y acentos del sistema Elysium Vanguard.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(Modifier.height(14.dp))

                // ══════════════════════════════════════
                // REAL-TIME PREVIEW WINDOW (Masculine & Premium)
                // ══════════════════════════════════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MeetColors.cardBackground)
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Title preview
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "ELYSIUM",
                                color = MeetColors.neonGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "VANGUARD",
                                color = MeetColors.electricBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        
                        // Fake buttons preview
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Primary Accent Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MeetColors.neonGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("IR AL SCANNER", color = MeetColors.backgroundDeep, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                            
                            // Secondary Accent Outlined Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("CONECTAR", color = MeetColors.neonGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Fake Bottom navigation preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF070B14))
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FakeNavItem("Inicio", MeetColors.neonGreen, isSelected = true)
                            FakeNavItem("Scanner", MeetColors.cyberCyan, isSelected = false)
                            FakeNavItem("DTCs", MeetColors.error, isSelected = false)
                            FakeNavItem("Garage", MeetColors.electricBlue, isSelected = false)
                            FakeNavItem("PRO", MeetColors.hotMagenta, isSelected = false)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ══════════════════════════════════════
                // TARGET TABS (Acento Primario, Secundario...)
                // ══════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SystemColorTarget.entries.forEach { target ->
                        val isSelected = target == selectedTarget
                        val targetColor = when (target) {
                            SystemColorTarget.PRIMARY -> MeetColors.neonGreen
                            SystemColorTarget.SECONDARY -> MeetColors.electricBlue
                            SystemColorTarget.TERTIARY -> MeetColors.cyberCyan
                            SystemColorTarget.QUATERNARY -> MeetColors.hotMagenta
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) targetColor.copy(alpha = 0.2f)
                                    else Color(0x15FFFFFF)
                                )
                                .then(
                                    if (isSelected) Modifier.border(
                                        1.dp,
                                        targetColor.copy(alpha = 0.6f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    else Modifier
                                )
                                .clickable { selectedTarget = target }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedNeonGlyph(target.icon, contentDescription = null, fontSize = 14.sp)
                                Text(
                                    target.label,
                                    color = if (isSelected) targetColor else Color.White.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Divider line
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .background(currentTargetColor.copy(alpha = 0.15f))
                )

                Spacer(Modifier.height(4.dp))

                // ══════════════════════════════════════
                // COLOR PRESENTS GRID (scrollable)
                // ══════════════════════════════════════
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                ) {
                    ThemeColors.FULL_COLOR_PALETTE.forEach { category ->
                        // Category Label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp, top = 10.dp)
                        ) {
                            AnimatedNeonGlyph(category.icon, contentDescription = null, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                category.title,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        }

                        // Swatches grid (rows of 6)
                        val columns = 6
                        category.colors.chunked(columns).forEach { rowColors ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowColors.forEach { entry ->
                                    NeonSystemSwatch(
                                        color = entry.color,
                                        isSelected = entry.color.toArgb() == currentTargetColor.toArgb(),
                                        onClick = {
                                            when (selectedTarget) {
                                                SystemColorTarget.PRIMARY -> MeetColors.updateNeonGreen(entry.color, context)
                                                SystemColorTarget.SECONDARY -> MeetColors.updateElectricBlue(entry.color, context)
                                                SystemColorTarget.TERTIARY -> MeetColors.updateCyberCyan(entry.color, context)
                                                SystemColorTarget.QUATERNARY -> MeetColors.updateHotMagenta(entry.color, context)
                                            }
                                        }
                                    )
                                }
                                // Spacer padding
                                repeat(columns - rowColors.size) {
                                    Spacer(Modifier.size(38.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FakeNavItem(label: String, color: Color, isSelected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isSelected) color else Color(0xFF3D4E63))
        )
        Text(
            label,
            color = if (isSelected) color else Color(0xFF3D4E63),
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NeonSystemSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "sysSwatch_${color.toArgb()}")
    val glow by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sysSwGlow"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .drawBehind {
                if (isSelected) {
                    drawCircle(
                        color = color.copy(alpha = glow * 0.45f),
                        radius = size.minDimension / 2f + 4.dp.toPx()
                    )
                    drawCircle(
                        color = color.copy(alpha = glow * 0.2f),
                        radius = size.minDimension / 2f + 8.dp.toPx()
                    )
                }
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.75f)
                    )
                )
            )
            .then(
                if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape)
                else Modifier.border(1.dp, color.copy(alpha = 0.2f), CircleShape)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Text(
                "✓",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
