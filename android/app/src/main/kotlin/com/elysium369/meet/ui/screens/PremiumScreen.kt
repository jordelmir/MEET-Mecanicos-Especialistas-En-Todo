package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteTextButton

@Composable
fun PremiumScreen(onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(com.elysium369.meet.ui.theme.MeetColors.backgroundDeep).padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Elysium Pro",
                color = MeetColors.warning,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black
            )
            
            Text(
                text = "Diagnóstico Sin Límites",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            PremiumFeatureRow("PIDs ilimitados en tiempo real")
            PremiumFeatureRow("Análisis IA Experto con Gemini")
            PremiumFeatureRow("Exportación de Reportes PDF")
            PremiumFeatureRow("Modo 22 (Fabricante Específico)")
            PremiumFeatureRow("Sincronización Cloud Automática")
            
            Spacer(modifier = Modifier.weight(1f))
            
            EliteButton(
                text = "SUSCRIBIRSE POR $4.99/MES",
                onClick = { /* Subscribe */ },
                color = MeetColors.warning,
                textColor = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            EliteTextButton(
                text = "Continuar Gratis",
                onClick = onClose,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun PremiumFeatureRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(MeetColors.warning, shape = RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
