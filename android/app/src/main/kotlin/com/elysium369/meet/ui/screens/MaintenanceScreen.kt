package com.elysium369.meet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.data.local.entities.MaintenanceAlertEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    alerts: List<MaintenanceAlertEntity>,
    currentOdometer: Long,
    onMarkAsDone: (MaintenanceAlertEntity) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mantenimientos", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", color = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            items(alerts) { alert ->
                val remaining = alert.nextDueKm - currentOdometer
                val (statusColor, statusText) = when {
                    remaining <= 0 -> Color.Red to "VENCIDO"
                    remaining <= 500 -> Color.Yellow to "PRÓXIMO"
                    else -> Color.Green to "AL DÍA"
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(alert.type, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Surface(color = statusColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text(statusText, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Intervalo: ${alert.intervalKm} km", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("Último cambio: ${alert.lastDoneKm} km", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("Próximo cambio: ${alert.nextDueKm} km", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        
                        if (statusColor != Color.Green) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onMarkAsDone(alert) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Marcar como Realizado ahora", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
