package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: ObdViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes Avanzados", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Text("←", color = Color(0xFF00FFCC), style = MaterialTheme.typography.titleLarge) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                CyberpunkSettingsSection("ADAPTADOR OBD2", Color(0xFF00FFCC)) {
                    SettingsRow("Tipo de Conexión", "WiFi (192.168.0.10:35000)")
                    SettingsRow("Modo Clon Forzado", "Desactivado", isToggle = true)
                }
            }
            item {
                CyberpunkSettingsSection("INTELIGENCIA ARTIFICIAL", Color(0xFFCC00FF)) {
                    SettingsRow("Motor IA", "Google Gemini Flash 1.5")
                    SettingsRow("Custom Endpoint", "No configurado")
                }
            }
            item {
                CyberpunkSettingsSection("UNIDADES", Color(0xFF00FFCC)) {
                    SettingsRow("Velocidad", "km/h")
                    SettingsRow("Temperatura", "Celsius (°C)")
                }
            }
            item {
                CyberpunkSettingsSection("DEBUG", Color(0xFFFF003C)) {
                    Button(onClick = { context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE).edit().remove("onboarding_completed").apply() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, Color(0xFFFF003C), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) { Text("Resetear Onboarding", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold) }
                }
            }
            item {
                CyberpunkSettingsSection("CUENTA", Color(0xFF00FFCC)) {
                    SettingsRow("Estado", "MEET Pro Premium", valueColor = Color(0xFF00FFCC))
                    Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, Color(0xFFCC00FF), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) { Text("Gestionar Suscripción", color = Color(0xFFCC00FF), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun CyberpunkSettingsSection(title: String, accentColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(title, color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsRow(label: String, value: String, isToggle: Boolean = false, valueColor: Color = Color.Gray) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White)
        if (isToggle) { Switch(checked = false, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FFCC), checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.3f))) }
        else { Text(value, color = valueColor) }
    }
}
