package com.elysium369.meet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity

@Composable
fun DtcCard(
    dtcCode: String,
    definition: DtcDefinitionEntity?,
    isPremium: Boolean,
    onConsultIa: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
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
                    "CRITICAL" -> Color.Red
                    "MODERATE" -> Color.Yellow
                    else -> Color.Gray
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
            
            Text(
                text = definition?.descriptionEs ?: "Descripción no disponible offline",
                color = Color.LightGray
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Posibles Causas:", color = Color.White, fontWeight = FontWeight.Bold)
                Text(definition?.possibleCauses ?: "--", color = Color.Gray)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { onConsultIa(dtcCode) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text("🤖 Consultar IA")
                    }
                    
                    if (isPremium) {
                        OutlinedButton(onClick = { /* Ver Freeze Frame */ }) {
                            Text("Ver Freeze Frame", color = Color(0xFFFF6B35))
                        }
                    }
                }
            }
        }
    }
}
