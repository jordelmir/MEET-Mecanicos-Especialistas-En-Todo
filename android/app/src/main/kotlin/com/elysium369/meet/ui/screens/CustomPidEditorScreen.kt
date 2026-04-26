package com.elysium369.meet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.data.local.entities.CustomPidEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPidEditorScreen(
    customPids: List<CustomPidEntity>,
    onAddCustomPid: (CustomPidEntity) -> Unit,
    onBack: () -> Unit
) {
    var showForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PIDs Custom (Premium)", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", color = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        floatingActionButton = {
            if (!showForm) {
                FloatingActionButton(onClick = { showForm = true }, containerColor = Color(0xFFFF6B35)) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        if (showForm) {
            CustomPidForm(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                onSave = { 
                    onAddCustomPid(it)
                    showForm = false
                },
                onCancel = { showForm = false }
            )
        } else {
            if (customPids.isEmpty()) {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No has configurado PIDs custom.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                    items(customPids) { pid ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("${pid.name} (${pid.mode} ${pid.pid})", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Fórmula: ${pid.formula} | Unidad: ${pid.unit}", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomPidForm(
    modifier: Modifier = Modifier,
    onSave: (CustomPidEntity) -> Unit,
    onCancel: () -> Unit
) {
    var mode by remember { mutableStateOf("01") }
    var pid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var formula by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Modo (Hex)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = pid, onValueChange = { pid = it }, label = { Text("PID (Hex)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre a mostrar", color = Color.Gray) }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unidad (ej: bar, °C)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = formula, onValueChange = { formula = it }, label = { Text("Fórmula", color = Color.Gray) }, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar", color = Color.White) }
            Button(
                onClick = {
                    onSave(CustomPidEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        userId = "local", mode = mode, pid = pid, name = name, unit = unit, formula = formula,
                        minVal = 0f, maxVal = 100f, warningThreshold = null, color = "#FF6B35"
                    ))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
            ) { Text("Guardar", color = Color.White) }
        }
    }
}
