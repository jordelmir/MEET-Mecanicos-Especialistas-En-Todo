package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by remember { mutableStateOf(1) }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                1 -> {
                    Text("MEET", style = MaterialTheme.typography.displayLarge, color = Color(0xFF39FF14), fontWeight = FontWeight.Black)
                    Text("OBD2", style = MaterialTheme.typography.headlineLarge, color = Color(0xFFCC00FF), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("El diagnóstico que tu taller merece.", color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Conéctate a cualquier vehículo y obtén métricas profesionales.", color = Color.Gray, textAlign = TextAlign.Center)
                }
                2 -> {
                    Text("Tipo de Adaptador", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    listOf("📡  Bluetooth (Recomendado)", "🔵  BLE", "📶  WiFi").forEach { label ->
                        OutlinedButton(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth().height(60.dp).border(1.dp, Color(0xFF00AAFF).copy(alpha = 0.3f), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0A0E1A))) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text("¿No sabes cuál tienes? Elige Bluetooth.", color = Color.DarkGray, textAlign = TextAlign.Center)
                }
                3 -> {
                    Text("✅", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("¡Todo Listo!", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Ya puedes escanear vehículos.", color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                (1..3).forEach { s -> Surface(color = if (s == step) Color(0xFF39FF14) else Color(0xFF39FF14).copy(alpha = 0.15f), shape = RoundedCornerShape(50), modifier = Modifier.padding(horizontal = 4.dp).size(if (s == step) 10.dp else 8.dp)) {} }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { if (step < 3) step++ else onFinish() }, modifier = Modifier.fillMaxWidth().height(54.dp).border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp)), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)), shape = RoundedCornerShape(8.dp)) {
                Text(if (step < 3) "SIGUIENTE →" else "COMENZAR", fontWeight = FontWeight.Bold, color = Color(0xFF39FF14))
            }
            if (step < 3) { TextButton(onClick = onFinish) { Text("Saltar por ahora", color = Color.DarkGray) } }
        }
    }
}
