package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.obd.ObdState
import java.util.Calendar

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val obdState by viewModel.connectionState.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            "$greeting, Mecánico",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            "MEET • Diagnóstico Profesional",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF00FFCC).copy(alpha = 0.5f)
        )

        // DTC Alert Banner
        if (activeDtcs.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFF003C), RoundedCornerShape(12.dp))
                    .clickable { navController.navigate("dtc") },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️ ${activeDtcs.size} DTCs Detectados", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold)
                    Surface(
                        color = Color(0xFFFF003C).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.dp, Color(0xFFFF003C), RoundedCornerShape(4.dp))
                    ) {
                        Text("VER →", color = Color(0xFFFF003C), fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Active Vehicle Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("VEHÍCULO ACTIVO", color = Color(0xFF00FFCC).copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing)
                Spacer(modifier = Modifier.height(8.dp))
                if (activeVehicle != null) {
                    Text(
                        "${activeVehicle?.make} ${activeVehicle?.model} ${activeVehicle?.year}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "VIN: ${activeVehicle?.vin ?: "N/A"}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { navController.navigate("scanner") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("IR AL SCANNER", fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                    }
                } else {
                    Text("Sin vehículo seleccionado", color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { navController.navigate("garage") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFCC00FF), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SELECCIONAR VEHÍCULO", fontWeight = FontWeight.Bold, color = Color(0xFFCC00FF))
                    }
                }
            }
        }

        // OBD2 Connection Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CONEXIÓN OBD2", color = Color(0xFF00FFCC).copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusText = when(obdState) {
                        ObdState.CONNECTED -> "CONECTADO"
                        ObdState.DISCONNECTED -> "DESCONECTADO"
                        ObdState.CONNECTING -> "CONECTANDO..."
                        ObdState.NEGOTIATING -> "NEGOCIANDO..."
                        ObdState.ERROR -> "ERROR"
                    }
                    val statusColor = when(obdState) {
                        ObdState.CONNECTED -> Color(0xFF00FFCC)
                        ObdState.ERROR -> Color(0xFFFF003C)
                        ObdState.CONNECTING, ObdState.NEGOTIATING -> Color(0xFFFFD700)
                        else -> Color.Gray
                    }
                    Text(statusText, color = statusColor, fontWeight = FontWeight.Black)
                }
                if (obdState != ObdState.CONNECTED && obdState != ObdState.CONNECTING) {
                    Button(
                        onClick = { navController.navigate("connect") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp))
                    ) {
                        Text("CONECTAR", fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                    }
                }
            }
        }

        // Quick Actions
        Text("ACCIONES RÁPIDAS", color = Color(0xFF00FFCC).copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickActionCard("⚡", "Scanner", Color(0xFF00FFCC), Modifier.weight(1f)) { navController.navigate("scanner") }
            QuickActionCard("⚠️", "DTCs", Color(0xFFFF003C), Modifier.weight(1f)) { navController.navigate("dtc") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickActionCard("🚗", "Garage", Color(0xFFCC00FF), Modifier.weight(1f)) { navController.navigate("garage") }
            QuickActionCard("🤖", "IA", Color(0xFFCC00FF), Modifier.weight(1f)) { navController.navigate("ai") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickActionCard("🔧", "Terminal", Color(0xFF00FFCC), Modifier.weight(1f)) { navController.navigate("terminal") }
            QuickActionCard("⚙️", "Ajustes", Color.Gray, Modifier.weight(1f)) { navController.navigate("settings") }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(72.dp)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
            Text(label, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
