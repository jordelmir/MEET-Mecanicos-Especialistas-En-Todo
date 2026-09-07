package com.elysium369.meet.education.presentation.sandboxes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class CircuitNode(val label: String, val description: String) {
    BATT_POS("N1 (BATT+)", "Borne Positivo de Batería"),
    POST_FUSE("N2 (FUSE_OUT)", "Salida del Fusible"),
    POST_SWITCH("N3 (RELAY_IN)", "Entrada de Carga / Relé"),
    POST_LOAD("N4 (LOAD_OUT)", "Salida de Carga hacia Masa"),
    GROUND("N5 (GND)", "Masa de Chasis (0V)")
}

/**
 * Simulador Táctil de Circuitos Eléctricos y Multímetro Virtual (MEP 9.º Año / Electricidad Automotriz).
 * Permite explorar de forma interactiva:
 * - Ley de Ohm: V = I * R
 * - Medición de caídas de tensión y diagnóstico de circuito abierto / fusible quemado.
 * - Conexión directa con códigos de falla OBD (P0230, P0562).
 */
@Composable
fun ElectricalCircuitSandbox(
    modifier: Modifier = Modifier,
    initialVoltage: Float = 12.6f,
    initialResistance: Float = 4.0f,
    onMeasurementTaken: ((vDrop: Float, current: Float, isFuseBlown: Boolean) -> Unit)? = null
) {
    var batteryVoltage by remember { mutableFloatStateOf(initialVoltage) }
    var resistance by remember { mutableFloatStateOf(initialResistance) }
    var isSwitchClosed by remember { mutableStateOf(true) }
    var isFuseBlown by remember { mutableStateOf(false) }

    // Sondas del multímetro
    var redProbeNode by remember { mutableStateOf(CircuitNode.BATT_POS) }
    var blackProbeNode by remember { mutableStateOf(CircuitNode.GROUND) }

    // Cálculo eléctrico
    val maxFuseCurrent = 10.0f
    val calculatedCurrent = if (isSwitchClosed && !isFuseBlown && resistance > 0.1f) {
        batteryVoltage / resistance
    } else 0f

    // Check fusible
    LaunchedEffect(calculatedCurrent) {
        if (calculatedCurrent > maxFuseCurrent) {
            isFuseBlown = true
        }
    }

    // Función de potencial eléctrico de cada nodo respecto a masa
    fun getNodePotential(node: CircuitNode): Float {
        if (isFuseBlown) {
            return when (node) {
                CircuitNode.BATT_POS -> batteryVoltage
                else -> 0f
            }
        }
        if (!isSwitchClosed) {
            return when (node) {
                CircuitNode.BATT_POS, CircuitNode.POST_FUSE -> batteryVoltage
                else -> 0f
            }
        }
        return when (node) {
            CircuitNode.BATT_POS, CircuitNode.POST_FUSE, CircuitNode.POST_SWITCH -> batteryVoltage
            CircuitNode.POST_LOAD, CircuitNode.GROUND -> 0f
        }
    }

    val redPotential = getNodePotential(redProbeNode)
    val blackPotential = getNodePotential(blackProbeNode)
    val measuredVoltageDrop = redPotential - blackPotential

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ LABORATORIO ELÉCTRICO Y MULTÍMETRO",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                if (isFuseBlown) {
                    AssistChip(
                        onClick = { isFuseBlown = false },
                        label = { Text("💥 FUSIBLE QUEMADO (Resetear)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    )
                } else {
                    AssistChip(
                        onClick = { isSwitchClosed = !isSwitchClosed },
                        label = {
                            Text(
                                text = if (isSwitchClosed) "🟢 INTERRUPTOR CERRADO" else "⚪ CIRCUITO ABIERTO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pantalla LCD del Multímetro Digital
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E261E),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF3B4D3B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MULTÍMETRO DIGITAL (DC V)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF7CB342),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val vText = (measuredVoltageDrop * 100).roundToInt() / 100f
                        Text(
                            text = "${if (vText >= 0) "+" else ""}$vText V",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = Color(0xFFC6FF00),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CORRIENTE LEY DE OHM",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF7CB342))
                        )
                        val iText = (calculatedCurrent * 100).roundToInt() / 100f
                        Text(
                            text = "$iText A",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = if (calculatedCurrent > 8f) Color(0xFFFF5252) else Color(0xFFC6FF00),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de Puntos de Prueba (Sondas)
            Text(
                text = "📍 CONECTAR SONDAS DE PRUEBA:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sonda Roja (+)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔴 Sonda Roja (+)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFE53935),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CircuitNode.values().forEach { node ->
                        val isSelected = redProbeNode == node
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE53935)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    redProbeNode = node
                                    onMeasurementTaken?.invoke(measuredVoltageDrop, calculatedCurrent, isFuseBlown)
                                }
                        ) {
                            Text(
                                text = node.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Sonda Negra (-)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚫ Sonda Negra (COM)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CircuitNode.values().forEach { node ->
                        val isSelected = blackProbeNode == node
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    blackProbeNode = node
                                    onMeasurementTaken?.invoke(measuredVoltageDrop, calculatedCurrent, isFuseBlown)
                                }
                        ) {
                            Text(
                                text = node.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Controles de Parámetros: Batería y Resistencia
            Text(
                text = "Batería: ${(batteryVoltage * 10).roundToInt() / 10f} V | Carga: ${(resistance * 10).roundToInt() / 10f} Ω",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Slider(
                    value = batteryVoltage,
                    onValueChange = { batteryVoltage = (it * 10).roundToInt() / 10f },
                    valueRange = 8f..15f,
                    modifier = Modifier.weight(1f)
                )
                Slider(
                    value = resistance,
                    onValueChange = { resistance = (it * 10).roundToInt() / 10f },
                    valueRange = 1f..15f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
