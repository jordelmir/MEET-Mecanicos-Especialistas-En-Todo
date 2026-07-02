package com.elysium.vanguard.forge.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.forge.domain.VehicleSystemType
import com.elysium.vanguard.forge.presentation.components.NeonCard
import com.elysium.vanguard.forge.presentation.components.SectionHeader
import com.elysium.vanguard.forge.presentation.components.TechLabel
import com.elysium.vanguard.forge.presentation.components.UiState
import com.elysium.vanguard.forge.presentation.theme.ForgeColors
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeVehicleBuilderEvent
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeVehicleBuilderViewModel

@Composable
fun ForgeVehicleBuilderScreen(
    viewModel: ForgeVehicleBuilderViewModel,
    onBack: () -> Unit = {},
    onSimulate: (vehicleId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val completeness by viewModel.completeness.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is UiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("CARGANDO", color = ForgeColors.Primary, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            is UiState.Empty -> CenterText("Sin vehículo cargado")
            is UiState.Error -> CenterText(state.message, ForgeColors.Error)
            is UiState.Ready -> Content(
                vehicle = state.data,
                completeness = completeness,
                onEvent = viewModel::onEvent,
                onBack = onBack,
                onSimulate = onSimulate
            )
        }
    }
}

@Composable
private fun CenterText(text: String, color: Color = ForgeColors.OnSurface) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun Content(
    vehicle: com.elysium.vanguard.forge.domain.ForgeVehicle,
    completeness: com.elysium.vanguard.forge.domain.CompletenessResult?,
    onEvent: (ForgeVehicleBuilderEvent) -> Unit,
    onBack: () -> Unit,
    onSimulate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForgeColors.SurfaceVariant,
                        contentColor = ForgeColors.OnSurface
                    )
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("BACK")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = vehicle.artifact.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
            CompletenessCard(completeness)
            Spacer(Modifier.height(16.dp))
            SectionHeader("SISTEMAS DEL VEHÍCULO")
        }
        items(count = VehicleSystemType.values().size) { i ->
            val sys = VehicleSystemType.values()[i]
            val installed = vehicle.systems.firstOrNull { it.systemType == sys }
            SystemRow(
                systemType = sys,
                installed = installed != null,
                assemblyId = installed?.assemblyId,
                onToggle = { onEvent(ForgeVehicleBuilderEvent.OnToggleSystem(sys, installed?.assemblyId)) }
            )
        }
        item {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSimulate(vehicle.artifact.id) },
                enabled = completeness?.readyToSimulate == true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (completeness?.readyToSimulate == true) ForgeColors.Primary.copy(alpha = 0.2f) else ForgeColors.SurfaceVariant,
                    contentColor = if (completeness?.readyToSimulate == true) ForgeColors.Primary else ForgeColors.OnSurface.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (completeness?.readyToSimulate == true) "SIMULAR VEHÍCULO" else "FALTAN SISTEMAS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CompletenessCard(completeness: com.elysium.vanguard.forge.domain.CompletenessResult?) {
    val pct = completeness?.overallPercent ?: 0.0
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = if (pct >= 80.0) ForgeColors.Success else if (pct >= 40.0) ForgeColors.Warning else ForgeColors.Error
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${pct.toInt()}%",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("COMPLETITUD", color = ForgeColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (completeness?.readyToSimulate == true) "Listo para simular" else "Faltan sistemas",
                        color = if (completeness?.readyToSimulate == true) ForgeColors.Success else ForgeColors.Warning,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (pct >= 80.0) ForgeColors.Success else if (pct >= 40.0) ForgeColors.Warning else ForgeColors.Error,
                trackColor = ForgeColors.SurfaceVariant
            )
            if (completeness?.missingSystems?.isNotEmpty() == true) {
                Spacer(Modifier.height(8.dp))
                TechLabel("FALTAN: ${completeness.missingSystems.joinToString { it.name }}")
            }
        }
    }
}

@Composable
private fun SystemRow(
    systemType: VehicleSystemType,
    installed: Boolean,
    assemblyId: String?,
    onToggle: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        accentColor = if (installed) ForgeColors.Success else ForgeColors.Warning
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(if (installed) ForgeColors.Success else ForgeColors.SurfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = systemType.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (installed) "→ $assemblyId" else "(no instalado)",
                    color = ForgeColors.OnSurface.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (installed) ForgeColors.Error.copy(alpha = 0.2f) else ForgeColors.Primary.copy(alpha = 0.2f),
                    contentColor = if (installed) ForgeColors.Error else ForgeColors.Primary
                )
            ) {
                Icon(
                    if (installed) Icons.Default.ArrowBack else Icons.Default.Check,
                    contentDescription = null
                )
                Spacer(Modifier.width(4.dp))
                Text(if (installed) "Quitar" else "Agregar", fontSize = 11.sp)
            }
        }
    }
}