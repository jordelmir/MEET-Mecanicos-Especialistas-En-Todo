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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.roundToInt

enum class SurfaceType(val displayName: String, val muStatic: Float, val color: Color) {
    DRY_ASPHALT("Asfalto Seco", 0.82f, Color(0xFF334155)),
    WET_ASPHALT("Asfalto Mojado (Lluvia)", 0.42f, Color(0xFF1E3A8A)),
    GRAVEL("Lastre / Grava Suelta", 0.35f, Color(0xFF78350F)),
    ICE("Hielo / Asfalto Congelado", 0.12f, Color(0xFF0284C7))
}

/**
 * Simulador Táctil de Dinámica Vehicular de Frenado & Sistema Antibloqueo (ABS).
 * Permite explorar de forma constructivista:
 * - Leyes de Newton y fricción en la calzada: a = μ · g
 * - Teorema del trabajo y la energía en la frenada: d = v² / (2 · μ · g)
 * - Diferencia crítica entre fricción estática (ABS modulado a 15 Hz) y cinética (derrape con rueda bloqueada).
 * - Mantenimiento de control direccional de la dirección.
 */
@Composable
fun BrakingDynamicsAbsSandbox(
    modifier: Modifier = Modifier,
    initialSpeedKmH: Float = 80f,
    onEvidenceProduced: ((distanceM: Float, decelerationG: Float, absActive: Boolean) -> Unit)? = null
) {
    // Velocidad inicial en km/h
    var speedKmH by remember { mutableFloatStateOf(initialSpeedKmH) }

    // Tipo de superficie seleccionada
    var selectedSurface by remember { mutableStateOf(SurfaceType.DRY_ASPHALT) }

    // Estado del sistema ABS
    var isAbsEnabled by remember { mutableStateOf(true) }

    // Conversión a m/s (v = km/h / 3.6)
    val speedMs = speedKmH / 3.6f
    val g = 9.81f

    // Si ABS está activo, aprovecha la fricción estática máxima (rueda girando a punto de deslizar).
    // Si ABS está apagado y la rueda se bloquea, cae a fricción cinética (~70% de la estática).
    val effectiveMu = if (isAbsEnabled) {
        selectedSurface.muStatic
    } else {
        selectedSurface.muStatic * 0.70f
    }

    // Desaceleración a = μ · g (en m/s²)
    val decelerationMs2 = effectiveMu * g
    val decelerationG = effectiveMu

    // Distancia teórica de frenado: d = v² / (2 · a)
    val stoppingDistanceM = (speedMs.pow(2) / (2f * decelerationMs2)).coerceAtLeast(1f)

    // Tiempo de detención: t = v / a
    val stoppingTimeSec = speedMs / decelerationMs2

    // Control direccional (gobernabilidad del volante)
    val steerabilityPercent = if (isAbsEnabled) 100 else 15

    LaunchedEffect(stoppingDistanceM, decelerationG, isAbsEnabled) {
        onEvidenceProduced?.invoke(stoppingDistanceM, decelerationG, isAbsEnabled)
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
                    text = "⚡ Dinámica de Frenado & Sistema ABS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (isAbsEnabled) Color(0xFF16A34A) else Color(0xFFDC2626),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isAbsEnabled) "ABS ACTIVO (15 Hz)" else "ABS DESACTIVADO (Bloqueo)",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas de trayectoria de frenado
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                val width = size.width
                val height = size.height
                val trackY = height * 0.55f

                // Asfalto
                drawLine(
                    color = selectedSurface.color,
                    start = Offset(0f, trackY + 8f),
                    end = Offset(width, trackY + 8f),
                    strokeWidth = 24f
                )

                // Huella de frenado: continua si bloqueó (ABS off), pulsada/intermitente si ABS activo
                val pathEffect = if (isAbsEnabled) {
                    PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                } else {
                    null
                }

                // Longitud visual de frenada proporcional a la distancia máxima en pantalla (tope 150m)
                val visualDistWidth = (stoppingDistanceM / 150f * (width - 60f)).coerceIn(20f, width - 60f)

                drawLine(
                    color = if (isAbsEnabled) Color(0xFF22C55E) else Color(0xFFEF4444),
                    start = Offset(20f, trackY),
                    end = Offset(20f + visualDistWidth, trackY),
                    strokeWidth = 6f,
                    pathEffect = pathEffect
                )

                // Vehículo final detenido
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(20f + visualDistWidth, trackY)
                )

                // Punto inicial de frenado
                drawCircle(
                    color = Color.Yellow,
                    radius = 5f,
                    center = Offset(20f, trackY)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Métricas de telemetría física
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Distancia Parada", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        String.format("%.1f m", stoppingDistanceM),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (stoppingDistanceM < 50f) Color(0xFF16A34A) else Color(0xFFDC2626)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Desaceleración", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        String.format("%.2f g", decelerationG),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tiempo Parada", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        String.format("%.2f s", stoppingTimeSec),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Maniobrabilidad", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        "$steerabilityPercent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (steerabilityPercent > 50) Color(0xFF16A34A) else Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de superficie
            Text(
                text = "Superficie de Calzada (Coeficiente μ):",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SurfaceType.values().forEach { surface ->
                    FilterChip(
                        selected = selectedSurface == surface,
                        onClick = { selectedSurface = surface },
                        label = {
                            Text(
                                text = "${surface.displayName.split(" ")[0]} (μ=${surface.muStatic})",
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Control de velocidad
            Text(
                text = "Velocidad Inicial: ${speedKmH.roundToInt()} km/h (${String.format("%.1f", speedMs)} m/s)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = speedKmH,
                onValueChange = { speedKmH = it },
                valueRange = 30f..130f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )

            // Switch ABS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sistema Electrónico ABS Activado (Impide bloqueo de rueda)",
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = isAbsEnabled,
                    onCheckedChange = { isAbsEnabled = it }
                )
            }
        }
    }
}
