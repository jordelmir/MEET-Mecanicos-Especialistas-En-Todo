package com.elysium369.meet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteOutlinedButton

@Composable
fun DtcCard(
    dtcCode: String,
    definition: DtcDefinitionEntity?,
    isPremium: Boolean,
    onConsultIa: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    EliteCard(
        backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = dtcCode, 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace
                )
                
                val severityColor = when(definition?.severity) {
                    "CRITICAL" -> com.elysium369.meet.ui.theme.MeetColors.error
                    "MODERATE" -> com.elysium369.meet.ui.theme.MeetColors.warning
                    else -> MeetColors.textSecondary
                }
                
                Surface(
                    color = severityColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = definition?.severity ?: "UNKNOWN", 
                        color = severityColor, 
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val rawEs = definition?.descriptionEs
            val descriptionText = if (rawEs.isNullOrBlank() || rawEs.contains("no disponible") || rawEs.contains("no encontrada")) {
                DtcUtils.getDynamicDtcFallbackDescription(dtcCode, isSpanish = true)
            } else {
                rawEs
            }
            
            Text(
                text = descriptionText,
                color = Color.LightGray
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MeetColors.borderBlue)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Posibles Causas:", color = Color.White, fontWeight = FontWeight.Bold)
                Text(definition?.possibleCauses ?: "--", color = MeetColors.textSecondary)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EliteButton(
                        text = "🤖 Consultar IA",
                        onClick = { onConsultIa(dtcCode) },
                        color = com.elysium369.meet.ui.theme.MeetColors.electricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (isPremium) {
                        EliteOutlinedButton(
                            text = "Freeze Frame",
                            onClick = { /* Ver Freeze Frame */ },
                            color = MeetColors.warning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
