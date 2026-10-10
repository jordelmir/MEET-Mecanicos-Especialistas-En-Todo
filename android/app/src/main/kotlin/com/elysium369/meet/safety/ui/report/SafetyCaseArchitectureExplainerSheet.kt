package com.elysium369.meet.safety.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCaseArchitectureExplainerSheet(
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MeetColors.backgroundDeep,
        contentColor = MeetColors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MeetColors.cyberCyan.copy(alpha = 0.5f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MeetColors.cyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timeline,
                            contentDescription = null,
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "CRONOLOGÍA FORENSE Y SEGUIMIENTO",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = MeetColors.textPrimary,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            "Cómo el sistema y la app construyen casos vivos",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = MeetColors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Banner principal explicativo
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "EL PRINCIPIO DE CASO CONTINUO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MeetColors.neonGreen,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "En criminalística real, los hechos delictivos (narcotráfico, asaltos, crimen organizado) no ocurren en un único instante aislado. Un incidente es un patrón en movimiento.\n\n" +
                        "En MEET, no tienes que crear múltiples reportes dispersos. Entras a 'Mis Reportes' y anexas cada nuevo avistamiento al mismo caso para tejer la línea de tiempo completa.",
                        fontSize = 12.sp,
                        color = MeetColors.textSecondary,
                        lineHeight = 17.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Ejemplo práctico con el caso de uso
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cyberCyan.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🔍 EJEMPLO PRÁCTICO DE CONSTRUCCIÓN:",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = MeetColors.cyberCyan,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TimelineStepSample(
                        stepNumber = "1",
                        dayLabel = "Día 1 — 12:23 PM (Supermercado X)",
                        features = "Sujeto con tenis negras, gorra negra, pantaloneta negra y camisa blanca en actitud de venta.",
                        isLast = false,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TimelineStepSample(
                        stepNumber = "2",
                        dayLabel = "Día 2 — 03:45 PM (Panadería X)",
                        features = "Mismo sujeto: tez morena, pelo negro, mismas tenis negras pero ahora con camisa azul.",
                        isLast = true,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "💡 Resultado: El expediente correlaciona calzado, fisonomía y rutas de movimiento. Vecinos, prensa y autoridades pueden identificar al individuo de inmediato.",
                        fontSize = 11.sp,
                        color = MeetColors.neonGreen,
                        lineHeight = 15.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pilares de la Arquitectura (La APK se explica a sí misma)
            Text(
                "ARQUITECTURA DEL SISTEMA (LA APP SE EXPLICA A SÍ MISMA)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = MeetColors.textMuted,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))

            ArchitecturePillarItem(
                icon = Icons.Filled.Shield,
                iconColor = MeetColors.neonGreen,
                title = "1. Inmutabilidad Append-Only (Nunca se borra el pasado)",
                description = "Cumpliendo con la Constitución de Safety, MEET nunca permite ediciones silenciosas ni altera registros pasados. Cada avistamiento nuevo es un hito complementario con su propia firma criptográfica que se suma a la cadena de custodia.",
            )

            Spacer(modifier = Modifier.height(10.dp))

            ArchitecturePillarItem(
                icon = Icons.Filled.AccessTime,
                iconColor = MeetColors.cyberCyan,
                title = "2. Doble Marca de Tiempo Forense",
                description = "El sistema graba obligatoriamente:\n• Hora de los Hechos (occurredAt): Cuándo sucedió el suceso real que presenciaste.\n• Hora de Grabación (recordedAt): Momento exacto de registro inmutable en la base de datos local y red.",
            )

            Spacer(modifier = Modifier.height(10.dp))

            ArchitecturePillarItem(
                icon = Icons.Filled.Videocam,
                iconColor = Color(0xFFFFB020),
                title = "3. Videos: Exclusivamente Enlaces URL Externos",
                description = "¿Por qué solo URLs? Subir archivos MP4 de video satura el almacenamiento de tu celular y colapsaría la red. MEET almacena enlaces web externos (YouTube, CDN, servidores de almacenamiento, etc.) garantizando streaming inmediato sin ocupar espacio en tu memoria.",
            )

            Spacer(modifier = Modifier.height(10.dp))

            ArchitecturePillarItem(
                icon = Icons.Filled.ElectricBolt,
                iconColor = MeetColors.neonGreen,
                title = "4. Límite Extendido de 100,000 Caracteres",
                description = "La narrativa soporta hasta 100,000 caracteres por entrada para permitir informes de inteligencia, bitácoras ciudadanas detalladas, testimonios completos y actas periciales sin recortes artificiales.",
            )

            Spacer(modifier = Modifier.height(10.dp))

            ArchitecturePillarItem(
                icon = Icons.Filled.Fingerprint,
                iconColor = MeetColors.cyberCyan,
                title = "5. Actualización y Proyección para Todos en el Mapa",
                description = "Cada avistamiento que registras se sincroniza y actualiza la vista para toda la comunidad. Quien consulte el caso en el mapa verá la evolución histórica y geográfica completa del incidente.",
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.cyberCyan,
                    contentColor = MeetColors.backgroundDeep,
                ),
            ) {
                Text("Entendido, Continuar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TimelineStepSample(
    stepNumber: String,
    dayLabel: String,
    features: String,
    isLast: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MeetColors.cyberCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(stepNumber, fontSize = 11.sp, fontWeight = FontWeight.Black, color = MeetColors.cyberCyan)
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(MeetColors.cyberCyan.copy(alpha = 0.3f))
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(dayLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
            Text(features, fontSize = 11.sp, color = MeetColors.textSecondary, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ArchitecturePillarItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MeetColors.cardBackground)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MeetColors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 11.sp, color = MeetColors.textSecondary, lineHeight = 15.sp)
        }
    }
}
