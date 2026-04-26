package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PremiumScreen(onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "MEET Pro",
                color = Color(0xFFFF6B35),
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
            
            Button(
                onClick = { /* Subscribe */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SUSCRIBIRSE POR $4.99/MES", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onClose) {
                Text("Continuar Gratis", color = Color.Gray)
            }
        }
    }
}

@Composable
fun PremiumFeatureRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF6B35), shape = RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
