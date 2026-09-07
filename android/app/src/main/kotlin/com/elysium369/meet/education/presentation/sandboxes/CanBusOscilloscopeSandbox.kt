package com.elysium369.meet.education.presentation.sandboxes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CanBusFault(val displayName: String, val busResistanceOhms: Int, val dtcCode: String?) {
    NORMAL("Normal (Sin Falla)", 60, null),
    MISSING_RESISTOR("Falta Terminación 120Ω (Reflexiones)", 120, "U0001 (High Speed CAN Comm)"),
    SHORT_TO_GROUND("CAN-Low en Corto a Masa (0V)", 0, "U0100 (Lost Comm with ECM)"),
    SHORT_TO_12V("CAN-High en Corto a 12V Batería", 999, "U0073 (Control Module Comm Bus Off)"),
    OPEN_LINE("Línea CAN-High Cortada / Abierta", 999, "U0121 (Lost Comm with ABS)")
}

/**
 * Osciloscopio Táctil Virtual de Red Multiplexada CAN-bus (ISO 11898 / SAE J1939).
 * Permite explorar de forma constructivista:
 * - Par diferencial de voltajes: CAN-High (2.5V a 3.5V) y CAN-Low (2.5V a 1.5V).
 * - Estado recesivo (bit 1, ΔV = 0V) y estado dominante (bit 0, ΔV = 2.0V).
 * - Resistencia de terminación característica (dos resistores de 120Ω en paralelo = 60Ω).
 * - Diagnóstico de fallas físicas de cableado e inyección de códigos DTC U-codes.
 */
@Composable
fun CanBusOscilloscopeSandbox(
    modifier: Modifier = Modifier,
    initialFault: CanBusFault = CanBusFault.NORMAL,
    onEvidenceProduced: ((fault: CanBusFault, resistanceOhms: Int, dtcCode: String?) -> Unit)? = null
) {
    var selectedFault by remember { mutableStateOf(initialFault) }
    var isFreezeFrameActive by remember { mutableStateOf(false) }

    val resistance = selectedFault.busResistanceOhms
    val dtc = selectedFault.dtcCode

    LaunchedEffect(selectedFault) {
        onEvidenceProduced?.invoke(selectedFault, resistance, dtc)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📟 Osciloscopio CAN-bus (ISO 11898)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (selectedFault == CanBusFault.NORMAL) Color(0xFF16A34A) else Color(0xFFDC2626),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (selectedFault == CanBusFault.NORMAL) "BUS OK (60Ω)" else "BUS ERROR",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pantalla de osciloscopio de fósforo digital con cuadrícula
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color(0xFF030712), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                val width = size.width
                val height = size.height

                // Cuadrícula tipo osciloscopio (5V a 0V vertical, divisiones horizontales)
                val gridColor = Color(0xFF1E293B)
                val numDivsY = 5
                val numDivsX = 8

                for (i in 0..numDivsY) {
                    val y = (i.toFloat() / numDivsY) * height
                    drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                }
                for (j in 0..numDivsX) {
                    val x = (j.toFloat() / numDivsX) * width
                    drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
                }

                // Función de conversión: 0V en la base (height), 5V en el tope (0)
                fun voltsToY(v: Float): Float = height - ((v.coerceIn(0f, 5f) / 5f) * height)

                // Simulación de secuencia de bits de trama CAN: 1 0 1 1 0 0 1 0 1 0 0 1
                val bitPattern = listOf(1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1, 0)
                val stepX = width / bitPattern.size

                val pathCanHigh = Path()
                val pathCanLow = Path()

                bitPattern.forEachIndexed { index, bit ->
                    val xStart = index * stepX
                    val xEnd = (index + 1) * stepX

                    val (highV, lowV) = when (selectedFault) {
                        CanBusFault.NORMAL -> if (bit == 0) Pair(3.5f, 1.5f) else Pair(2.5f, 2.5f)
                        CanBusFault.MISSING_RESISTOR -> {
                            // Con reflexiones y ringing inductivo
                            if (bit == 0) Pair(4.1f, 1.1f) else Pair(2.6f, 2.4f)
                        }
                        CanBusFault.SHORT_TO_GROUND -> if (bit == 0) Pair(3.2f, 0.0f) else Pair(1.2f, 0.0f)
                        CanBusFault.SHORT_TO_12V -> Pair(5.0f, 4.8f) // Saturado en el límite del ADC
                        CanBusFault.OPEN_LINE -> if (bit == 0) Pair(0.0f, 1.5f) else Pair(0.0f, 2.5f)
                    }

                    val yH = voltsToY(highV)
                    val yL = voltsToY(lowV)

                    if (index == 0) {
                        pathCanHigh.moveTo(xStart, yH)
                        pathCanLow.moveTo(xStart, yL)
                    } else {
                        pathCanHigh.lineTo(xStart, yH)
                        pathCanLow.lineTo(xStart, yL)
                    }
                    pathCanHigh.lineTo(xEnd, yH)
                    pathCanLow.lineTo(xEnd, yL)
                }

                // Trazado de CAN-High (Amarillo fósforo)
                drawPath(
                    path = pathCanHigh,
                    color = Color(0xFFFACC15),
                    style = Stroke(width = 3f)
                )

                // Trazado de CAN-Low (Azul cian)
                drawPath(
                    path = pathCanLow,
                    color = Color(0xFF38BDF8),
                    style = Stroke(width = 3f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Leyenda de canales del osciloscopio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFFFACC15), shape = RoundedCornerShape(4.dp), modifier = Modifier.size(10.dp)) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CH1: CAN-High (3.5V / 2.5V)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF38BDF8), shape = RoundedCornerShape(4.dp), modifier = Modifier.size(10.dp)) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CH2: CAN-Low (1.5V / 2.5V)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mediciones de bus
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Resistencia Bus", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        if (resistance == 999) "∞ Ω (Abierto)" else "$resistance Ω",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (resistance == 60) Color(0xFF16A34A) else Color(0xFFDC2626)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Voltaje Diferencial", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        if (selectedFault == CanBusFault.NORMAL) "2.0 V (Dominante)" else "Anómalo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Velocidad Red", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        "500 kbps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Alerta DTC si hay falla inyectada
            if (dtc != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFDC2626).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️ DTC REGISTRADO:", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dtc, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Inyector de fallas interactivas
            Text(
                text = "Inyectar Condición de Falla en el Cableado:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            CanBusFault.values().forEach { fault ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    RadioButton(
                        selected = selectedFault == fault,
                        onClick = { selectedFault = fault }
                    )
                    Text(
                        text = fault.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selectedFault == fault) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
