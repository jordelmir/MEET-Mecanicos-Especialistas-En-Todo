package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════════════════════════════
// ELITE UI V2 — Phantom Carbon Components
// ═══════════════════════════════════════════════════════════════

/**
 * Elite Card — Premium glassmorphism card with animated accent border.
 */
@Composable
fun EliteCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = MeetColors.cardBackground,
    borderColor: Color = MeetColors.borderSubtle,
    glowColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    // ═══ CRITICAL: neonGlow MUST come BEFORE clip so light renders OUTSIDE the container ═══
    val baseModifier = modifier
        .then(
            if (glowColor != null) Modifier.neonGlow(
                glowColor, shape,
                minElevation = 2f, maxElevation = 10f,
                minAlpha = 0.1f, maxAlpha = 0.35f
            ) else Modifier
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    backgroundColor.copy(alpha = 0.85f),
                    backgroundColor.copy(alpha = 0.65f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor.copy(alpha = 0.4f),
                    borderColor.copy(alpha = 0.1f),
                    borderColor.copy(alpha = 0.4f)
                )
            ),
            shape = shape
        )
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Elite Button — Solid premium button with glow and gradient.
 */
@Composable
fun EliteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MeetColors.neonGreen,
    textColor: Color = MeetColors.backgroundDeep,
    isEnabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(52.dp)
            .then(
                if (isEnabled) Modifier.neonGlow(
                    color, RoundedCornerShape(14.dp),
                    minElevation = 4f, maxElevation = 14f,
                    minAlpha = 0.3f, maxAlpha = 0.6f
                ) else Modifier
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isEnabled) Brush.horizontalGradient(
                    colors = listOf(color, color.copy(alpha = 0.8f))
                ) else Brush.horizontalGradient(
                    colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.2f))
                )
            ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = if (isEnabled) textColor else MeetColors.textSecondary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * Elite Outlined Button — Ghost button with animated border glow.
 */
@Composable
fun EliteOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MeetColors.neonGreen,
    isEnabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = isEnabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = color.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEnabled) Brush.horizontalGradient(
                listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.3f), color.copy(alpha = 0.8f))
            ) else Brush.horizontalGradient(
                listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.15f))
            )
        ),
        modifier = modifier
            .height(52.dp)
            .then(
                if (isEnabled) Modifier.neonGlow(
                    color.copy(alpha = 0.4f), RoundedCornerShape(14.dp),
                    minElevation = 1f, maxElevation = 6f,
                    minAlpha = 0.05f, maxAlpha = 0.2f
                ) else Modifier
            ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = text.uppercase(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun EliteTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MeetColors.electricBlue,
    isEnabled: Boolean = true
) {
    TextButton(onClick = onClick, enabled = isEnabled, modifier = modifier) {
        Text(text = text, color = if (isEnabled) color else MeetColors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EliteIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color? = null,
    isEnabled: Boolean = true
) {
    IconButton(
        onClick = onClick, enabled = isEnabled,
        modifier = modifier.then(
            if (glowColor != null && isEnabled) Modifier.neonGlow(
                glowColor, RoundedCornerShape(50),
                minElevation = 2f, maxElevation = 6f,
                minAlpha = 0.1f, maxAlpha = 0.25f
            ) else Modifier
        )
    ) { icon() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EliteTopAppBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = MeetColors.backgroundDeep
) {
    TopAppBar(
        title = {
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, color = MeetColors.neonGreen, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Text("←", color = MeetColors.neonGreen, style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
    )
}

@Composable
fun EliteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "ACEPTAR",
    dismissText: String = "CANCELAR",
    isDestructive: Boolean = false
) {
    val accentColor = if (isDestructive) MeetColors.error else MeetColors.neonGreen

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MeetColors.backgroundDeep.copy(alpha = 0.97f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .neonGlow(accentColor, RoundedCornerShape(20.dp),
                        minElevation = 6f, maxElevation = 20f,
                        minAlpha = 0.15f, maxAlpha = 0.4f)
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(accentColor.copy(alpha = 0.5f), accentColor.copy(alpha = 0.15f))
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title.uppercase(), color = accentColor, fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = onDismiss, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.textSecondary.copy(alpha = 0.5f))
                        ) { Text(dismissText, color = MeetColors.textSecondary, fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f)
                                .neonGlow(accentColor, RoundedCornerShape(12.dp),
                                    minElevation = 4f, maxElevation = 10f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text(confirmText, color = MeetColors.backgroundDeep, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phantom Section Header — Consistent header across all screens.
 */
@Composable
fun PhantomSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MeetColors.neonGreen
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(
                    Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.2f))),
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            color = accentColor.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}
