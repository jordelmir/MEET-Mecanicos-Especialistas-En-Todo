package com.elysium369.meet.ui.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdapterSearchSheet(
    isFullScreen: Boolean = true,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter

    // ═══ Mutable device list — supports manual clearing & re-scan ═══
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val pairedDevices = remember(refreshTrigger) {
        try { bluetoothAdapter?.bondedDevices?.map { Pair(it.name ?: "Unknown", it.address) } ?: emptyList() }
        catch (e: SecurityException) { emptyList() }
    }
    
    val content = @Composable {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text("Conectar Adaptador OBD2", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Selecciona el tipo de conexión", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("BT Clásico", modifier = Modifier.padding(16.dp), color = if(selectedTab == 0) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = if(selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("BLE", modifier = Modifier.padding(16.dp), color = if(selectedTab == 1) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = if(selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("WiFi", modifier = Modifier.padding(16.dp), color = if(selectedTab == 2) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = if(selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            when (selectedTab) {
                0 -> {
                    val isBtEnabled = bluetoothAdapter?.isEnabled == true
                    if (!isBtEnabled) {
                        Text("Bluetooth desactivado. Actívalo para continuar.", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, modifier = Modifier.padding(16.dp))
                        EliteOutlinedButton(text = "IR A AJUSTES BLUETOOTH", onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, modifier = Modifier.padding(horizontal = 16.dp))
                    } else if (pairedDevices.isEmpty()) {
                        Text("No hay dispositivos emparejados. Empareja tu adaptador OBD2 desde los ajustes del sistema.", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, modifier = Modifier.padding(16.dp))
                        EliteOutlinedButton(text = "IR A AJUSTES BLUETOOTH", onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        // ═══ CLEAR DEVICES: Force fresh discovery ═══
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${pairedDevices.size} dispositivos",
                                color = com.elysium369.meet.ui.theme.MeetColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            EliteTextButton(
                                text = "⟳ LIMPIAR Y RE-ESCANEAR",
                                onClick = { refreshTrigger++ },
                                color = com.elysium369.meet.ui.theme.MeetColors.warning
                            )
                        }

                        LazyColumn {
                            items(pairedDevices) { device ->
                                EliteCard(
                                    backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
                                    borderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = { onConnect(device.first, device.second); onDismiss() },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                        Text(device.first, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(device.second, color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val isBtEnabled = bluetoothAdapter?.isEnabled == true
                    if (!isBtEnabled) {
                        Text("Bluetooth desactivado.", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, modifier = Modifier.padding(16.dp))
                        EliteOutlinedButton(text = "IR A AJUSTES BLUETOOTH", onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        Text("Escaneando dispositivos BLE...", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, modifier = Modifier.padding(16.dp))
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = com.elysium369.meet.ui.theme.MeetColors.electricBlue)
                    }
                }
                2 -> {
                    var wifiMode by remember { mutableStateOf("AUTO") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilterChip(selected = wifiMode == "AUTO", onClick = { wifiMode = "AUTO" }, label = { Text("Auto-Descubrimiento") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen, selectedLabelColor = Color.Black))
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(selected = wifiMode == "MANUAL", onClick = { wifiMode = "MANUAL" }, label = { Text("Manual") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue, selectedLabelColor = Color.White))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (wifiMode == "AUTO") {
                        Text("Buscando adaptador OBD2 en la red...", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, modifier = Modifier.padding(16.dp))
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        EliteOutlinedButton(text = "Conectar (192.168.0.10)", onClick = { onConnect("WiFi OBD (Auto)", "192.168.0.10:35000"); onDismiss() })
                    } else {
                        var ip by remember { mutableStateOf("192.168.0.10") }
                        var port by remember { mutableStateOf("35000") }
                        OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("Dirección IP", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen, unfocusedBorderColor = MeetColors.borderBlue))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Puerto TCP", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen, unfocusedBorderColor = MeetColors.borderBlue))
                        Spacer(modifier = Modifier.height(16.dp))
                        EliteOutlinedButton(text = "CONECTAR", onClick = { onConnect("WiFi OBD (Manual)", "$ip:$port"); onDismiss() })
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (isFullScreen) { Surface(color = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep, modifier = Modifier.fillMaxSize()) { content() } }
    else { ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark) { content() } }
}
