package com.elysium369.meet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    vehicleId: String? = null
) {
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vehicleId == null) "Registrar Vehículo" else "Editar Vehículo", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            OutlinedTextField(
                value = make,
                onValueChange = { make = it },
                label = { Text("Marca", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF39FF14), focusedTextColor = Color.White, unfocusedTextColor = Color.White, unfocusedBorderColor = Color.DarkGray)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modelo", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF39FF14), focusedTextColor = Color.White, unfocusedTextColor = Color.White, unfocusedBorderColor = Color.DarkGray)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = year,
                onValueChange = { year = it },
                label = { Text("Año", color = Color.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF39FF14), focusedTextColor = Color.White, unfocusedTextColor = Color.White, unfocusedBorderColor = Color.DarkGray)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vin,
                    onValueChange = { vin = it },
                    label = { Text("VIN (Opcional)", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF39FF14), focusedTextColor = Color.White, unfocusedTextColor = Color.White, unfocusedBorderColor = Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val readVin = viewModel.readVin()
                            if (readVin != null) vin = readVin
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(56.dp).border(1.dp, Color(0xFFCC00FF), RoundedCornerShape(8.dp))
                ) {
                    Text("LEER OBD", color = Color(0xFFCC00FF), fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { 
                    viewModel.saveVehicle(make, model, year, vin)
                    navController.popBackStack() 
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                modifier = Modifier.fillMaxWidth().height(50.dp).border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("GUARDAR VEHÍCULO", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
            }
        }
    }
}
