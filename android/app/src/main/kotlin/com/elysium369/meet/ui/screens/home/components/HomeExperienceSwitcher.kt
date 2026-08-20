package com.elysium369.meet.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.home.HomeExperience
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun HomeExperienceSwitcherHeaderButton(
    currentExperience: HomeExperience,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = MeetColors.cardBackground,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("⌘", color = MeetColors.neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = currentExperience.displayName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("▾", color = MeetColors.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
fun HomeExperienceSelectionDialog(
    currentExperience: HomeExperience,
    onDismiss: () -> Unit,
    onSelectExperience: (HomeExperience) -> Unit,
    onPreviewExperience: (HomeExperience) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1B30),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌘", fontSize = 20.sp, color = MeetColors.neonGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "EXPERIENCIA DE INICIO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Personaliza cómo deseas acceder y visualizar MEET:",
                    color = MeetColors.textMuted,
                    fontSize = 12.sp
                )

                // Classic Option
                ExperienceOptionCard(
                    title = "Vanguard Classic",
                    subtitle = "Cuadrícula completa de módulos siempre visibles. Ideal para control directo y usuarios avanzados.",
                    isSelected = currentExperience == HomeExperience.CLASSIC,
                    onClick = {
                        onSelectExperience(HomeExperience.CLASSIC)
                        onDismiss()
                    }
                )

                // Adaptive Option
                ExperienceOptionCard(
                    title = "Vanguard Command",
                    subtitle = "Inicio inteligente y contextual. Prioriza vehículo activo, diagnósticos, acciones pendientes y mantenimiento.",
                    isSelected = currentExperience == HomeExperience.ADAPTIVE,
                    onClick = {
                        onSelectExperience(HomeExperience.ADAPTIVE)
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CERRAR", color = MeetColors.textMuted, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ExperienceOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MeetColors.neonGreen else MeetColors.neonGreen.copy(alpha = 0.15f)
    val bgColor = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.08f) else MeetColors.cardBackground

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MeetColors.neonGreen,
                    unselectedColor = MeetColors.textMuted
                )
            )
            Column {
                Text(
                    text = title,
                    color = if (isSelected) MeetColors.neonGreen else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MeetColors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun HomeExperiencePreviewBanner(
    previewExperience: HomeExperience,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1E293B),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("👁️", fontSize = 14.sp)
                Column {
                    Text(
                        "VISTA PREVIA",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        previewExperience.displayName,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.textMuted.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("VOLVER", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onCommit,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("USAR ESTE HOME", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
