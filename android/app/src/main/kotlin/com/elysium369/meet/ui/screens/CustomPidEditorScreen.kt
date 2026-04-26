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
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                title = { Text("DASHBOARD BUILDER (PRO)", color = Color.White, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", color = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        floatingActionButton = {
            if (!showForm) {
                FloatingActionButton(
                    onClick = { showForm = true }, 
                    containerColor = Color.Black,
                    modifier = Modifier.border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add PID", tint = Color(0xFF00FFCC))
                }
            }
        },
        containerColor = Color.Black
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
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Text("PERSONALIZACIÓN DE TELEMETRÍA AVANZADA", color = Color(0xFF00FFCC).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Agrega direcciones UDS/Hexadecimales OEM para inyectar comandos directamente al bus CAN y crear tu propio dashboard visual.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (customPids.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔧", style = MaterialTheme.typography.displayLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("SIN SENSORES CUSTOM", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text("Toca el + para inyectar un nuevo PID OEM", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(customPids) { pid ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            ) {
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("${pid.name}", color = Color(0xFF00FFCC), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("TX: ${pid.mode} ${pid.pid} | Formula: ${pid.formula}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Surface(color = Color(0xFFCC00FF).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp), modifier = Modifier.border(1.dp, Color(0xFFCC00FF), RoundedCornerShape(4.dp))) {
                                        Text(pid.unit, color = Color(0xFFCC00FF), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
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
        Text("CONFIGURACIÓN DE PID OEM", color = Color(0xFFCC00FF), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Precaución: Escribir parámetros incorrectos en el Bus CAN puede causar códigos de error (U0100).", color = Color(0xFFFF003C), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = name, onValueChange = { name = it }, 
            label = { Text("Nombre del Sensor", color = Color.Gray) }, 
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00FFCC), unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = mode, onValueChange = { mode = it }, 
                label = { Text("Service (Hex)", color = Color.Gray) }, 
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00FFCC), unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray)
            )
            OutlinedTextField(
                value = pid, onValueChange = { pid = it }, 
                label = { Text("PID (Hex)", color = Color.Gray) }, 
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00FFCC), unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = unit, onValueChange = { unit = it }, 
                label = { Text("Unidad (ej: °C)", color = Color.Gray) }, 
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00FFCC), unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray)
            )
            OutlinedTextField(
                value = formula, onValueChange = { formula = it }, 
                label = { Text("Ecuación (A,B...)", color = Color.Gray) }, 
                modifier = Modifier.weight(2f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00FFCC), unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onCancel, 
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("CANCELAR") }
            Button(
                onClick = {
                    onSave(CustomPidEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        userId = "local", mode = mode, pid = pid, name = name, unit = unit, formula = formula,
                        minVal = 0f, maxVal = 100f, warningThreshold = null, color = "#00FFCC"
                    ))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("INYECTAR PID", color = Color.Black, fontWeight = FontWeight.Black) }
        }
    }
}
