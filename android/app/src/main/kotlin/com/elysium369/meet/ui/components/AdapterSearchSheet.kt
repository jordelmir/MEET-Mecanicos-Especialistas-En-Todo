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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val pairedDevices = remember {
        try { bluetoothAdapter?.bondedDevices?.map { Pair(it.name ?: "Unknown", it.address) } ?: emptyList() }
        catch (e: SecurityException) { emptyList() }
    }
    
    val content = @Composable {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text("Conectar Adaptador OBD2", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Selecciona el tipo de conexión", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = Color(0xFF00FFCC)) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("BT Clásico", modifier = Modifier.padding(16.dp), color = if(selectedTab == 0) Color(0xFF00FFCC) else Color.Gray, fontWeight = if(selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("BLE", modifier = Modifier.padding(16.dp), color = if(selectedTab == 1) Color(0xFF00FFCC) else Color.Gray, fontWeight = if(selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("WiFi", modifier = Modifier.padding(16.dp), color = if(selectedTab == 2) Color(0xFF00FFCC) else Color.Gray, fontWeight = if(selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            when (selectedTab) {
                0 -> {
                    val isBtEnabled = bluetoothAdapter?.isEnabled == true
                    if (!isBtEnabled) {
                        Text("Bluetooth desactivado. Actívalo para continuar.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        Button(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) { Text("IR A AJUSTES BLUETOOTH", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold) }
                    } else if (pairedDevices.isEmpty()) {
                        Text("No hay dispositivos emparejados. Empareja tu adaptador OBD2 desde los ajustes del sistema.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        Button(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) { Text("IR A AJUSTES BLUETOOTH", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold) }
                    } else {
                        LazyColumn {
                            items(pairedDevices) { device ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f), RoundedCornerShape(8.dp)).clickable { onConnect(device.first, device.second); onDismiss() }) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(device.first, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(device.second, color = Color(0xFF00FFCC).copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val isBtEnabled = bluetoothAdapter?.isEnabled == true
                    if (!isBtEnabled) {
                        Text("Bluetooth desactivado.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        Button(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) { Text("IR A AJUSTES BLUETOOTH", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold) }
                    } else {
                        Text("Escaneando dispositivos BLE...", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = Color(0xFFCC00FF))
                    }
                }
                2 -> {
                    var wifiMode by remember { mutableStateOf("AUTO") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilterChip(selected = wifiMode == "AUTO", onClick = { wifiMode = "AUTO" }, label = { Text("Auto-Descubrimiento") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00FFCC), selectedLabelColor = Color.Black))
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(selected = wifiMode == "MANUAL", onClick = { wifiMode = "MANUAL" }, label = { Text("Manual") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFCC00FF), selectedLabelColor = Color.White))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (wifiMode == "AUTO") {
                        Text("Buscando adaptador OBD2 en la red...", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = Color(0xFF00FFCC))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { onConnect("WiFi OBD (Auto)", "192.168.0.10:35000"); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) { Text("Conectar (192.168.0.10)", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold) }
                    } else {
                        var ip by remember { mutableStateOf("192.168.0.10") }
                        var port by remember { mutableStateOf("35000") }
                        OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("Dirección IP", color = Color.Gray) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF00FFCC), unfocusedBorderColor = Color.DarkGray))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Puerto TCP", color = Color.Gray) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF00FFCC), unfocusedBorderColor = Color.DarkGray))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { onConnect("WiFi OBD (Manual)", "$ip:$port"); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) { Text("CONECTAR", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (isFullScreen) { Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) { content() } }
    else { ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF0A0A0A)) { content() } }
}
