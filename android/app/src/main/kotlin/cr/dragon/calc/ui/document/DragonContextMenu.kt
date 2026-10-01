package cr.dragon.calc.ui.document

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.dragon.calc.ui.*

/**
 * DragonContextMenu: Menú contextual vidrioso (Glassmorphic) con estética Imperial.
 */
@Composable
fun DragonContextMenu(
    isVisible: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    hasPasteContent: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)) +
                expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
               shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow))
    ) {
        Column(
            modifier = modifier
                .width(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.9f))
                .padding(8.dp)
        ) {
            ContextMenuItem(
                icon = Icons.Filled.Share,
                label = "COPIAR FORMULA",
                onClick = onCopy,
                color = DragonCyan
            )

            if (hasPasteContent) {
                Spacer(modifier = Modifier.height(4.dp))
                ContextMenuItem(
                    icon = Icons.Filled.Add,
                    label = "PEGAR CONTENIDO",
                    onClick = onPaste,
                    color = DragonGreen
                )
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMono
        )
    }
}
