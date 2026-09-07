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
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Simulador Táctil de Combustión Estequiométrica & Sensor de Oxígeno Lambda (λ).
 * Permite explorar de forma constructivista:
 * - Relación estequiométrica aire-combustible ideal para gasolina (14.7 : 1).
 * - Curva de transferencia sigmoidal del sensor de O2 de circonio (0.1V a 0.9V).
 * - Corrección de combustible a corto plazo (Short-Term Fuel Trim - STFT).
 * - Generación de códigos DTC de mezcla (P0171 Pobre / P0172 Rica).
 */
@Composable
fun StoichiometricInjectionSandbox(
    modifier: Modifier = Modifier,
    initialMaf: Float = 3.5f,
    initialPulseWidthMs: Float = 2.5f,
    onEvidenceProduced: ((lambda: Float, stftPercent: Int, dtcCode: String?) -> Unit)? = null
) {
    // Flujo de aire MAF en gramos por segundo (g/s)
    var mafGramsPerSec by remember { mutableFloatStateOf(initialMaf) }

    // Ancho de pulso del inyector en milisegundos (ms)
    var pulseWidthMs by remember { mutableFloatStateOf(initialPulseWidthMs) }

    // Fuga de vacío inducida artificialmente (añade aire no medido)
    var hasVacuumLeak by remember { mutableStateOf(false) }

    // Cálculo físico de aire y combustible efectivos
    val effectiveAir = if (hasVacuumLeak) mafGramsPerSec * 1.35f else mafGramsPerSec
    // Suposición de inyector estándar de 200 cc/min (~0.024 g de combustible por ms)
    val fuelDeliveredGrams = (pulseWidthMs * 0.095f).coerceAtLeast(0.01f)

    // Relación Aire-Combustible (AFR = Air/Fuel Ratio)
    val afr = effectiveAir / fuelDeliveredGrams
    // Factor Lambda (λ = AFR actual / 14.7)
    val lambda = (afr / 14.7f).coerceIn(0.65f, 1.45f)

    // Voltaje del sensor de O2 (curva sigmoidal no lineal centrada en λ = 1.0)
    // λ < 1.0 -> Rico (~0.85V-0.92V), λ > 1.0 -> Pobre (~0.08V-0.15V)
    val o2Voltage = (0.90f / (1.0f + exp(28.0f * (lambda - 1.0f))) + 0.05f).coerceIn(0.05f, 0.95f)

    // Corrección de combustible (STFT): El ECU intenta forzar λ = 1.0 inyectando más o menos combustible
    val stftPercent = (((1.0f - lambda) * 100.0f) * 1.5f).roundToInt().coerceIn(-35, 35)

    // Diagnóstico DTC automático por límite de fuel trim (> ±20%)
    val dtcCode = when {
        stftPercent >= 20 -> "P0171 (System Too Lean - Bank 1)"
        stftPercent <= -20 -> "P0172 (System Too Rich - Bank 1)"
        else -> null
    }

    LaunchedEffect(lambda, stftPercent, dtcCode) {
        onEvidenceProduced?.invoke(lambda, stftPercent, dtcCode)
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
                    text = "🧪 Inyección Estequiométrica & Sensor O₂ (λ)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = when {
                        lambda in 0.98f..1.02f -> Color(0xFF2E7D32)
                        lambda < 0.98f -> Color(0xFFE65100)
                        else -> Color(0xFFC62828)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            lambda in 0.98f..1.02f -> "ESTEQUIOMÉTRICA (λ ≈ 1.0)"
                            lambda < 0.98f -> "MEZCLA RICA (Rich)"
                            else -> "MEZCLA POBRE (Lean)"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas de telemetría: Onda del sensor de oxígeno O2 y relación AFR
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                val width = size.width
                val height = size.height

                // Línea de referencia estequiométrica media (0.45V)
                val midY = height * 0.5f
                drawLine(
                    color = Color.Gray.copy(alpha = 0.4f),
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 1.5f
                )

                // Trazado de curva de voltaje simulada
                val path = Path()
                val steps = 40
                val waveHeight = 15f
                for (i in 0..steps) {
                    val x = (i.toFloat() / steps) * width
                    // El voltaje se mapea de 0.95V (arriba) a 0.05V (abajo)
                    val normalizedY = 1.0f - ((o2Voltage - 0.05f) / 0.90f)
                    val jitter = if (lambda in 0.98f..1.02f) kotlin.math.sin(i * 0.6f) * waveHeight else 0f
                    val y = (normalizedY * (height - 30f) + 15f) + jitter
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = when {
                        lambda in 0.98f..1.02f -> Color(0xFF4ADE80)
                        lambda < 0.98f -> Color(0xFFFB923C)
                        else -> Color(0xFFF87171)
                    },
                    style = Stroke(width = 3.5f)
                )

                // Indicador de nivel de voltaje actual
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = Offset(width - 10f, (1.0f - ((o2Voltage - 0.05f) / 0.90f)) * (height - 30f) + 15f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lecturas de telemetría digital en tiempo real
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Voltaje O₂", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        String.format("%.3f V", o2Voltage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Lambda (λ)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        String.format("%.2f", lambda),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("STFT (Trim)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        (if (stftPercent > 0) "+$stftPercent%" else "$stftPercent%"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (stftPercent > 0) Color(0xFF2563EB) else Color(0xFFDC2626)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("AFR Real", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        String.format("%.1f:1", afr),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Alerta DTC si el Fuel Trim se sale de la tolerancia OEM
            if (dtcCode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️ ALERTA OBD-II:", fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C), style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dtcCode, color = Color(0xFFB91C1C), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Controles táctiles
            Text(
                text = "Flujo de Aire MAF: ${String.format("%.1f", mafGramsPerSec)} g/s",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = mafGramsPerSec,
                onValueChange = { mafGramsPerSec = it },
                valueRange = 1.5f..12.0f,
                steps = 21,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Ancho de Pulso Inyector: ${String.format("%.2f", pulseWidthMs)} ms",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = pulseWidthMs,
                onValueChange = { pulseWidthMs = it },
                valueRange = 1.0f..6.0f,
                steps = 25,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Simular Fuga de Vacío en Múltiple (+35% aire no medido)",
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = hasVacuumLeak,
                    onCheckedChange = { hasVacuumLeak = it }
                )
            }
        }
    }
}
