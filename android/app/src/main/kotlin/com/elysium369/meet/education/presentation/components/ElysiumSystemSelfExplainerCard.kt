package com.elysium369.meet.education.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ElysiumSystemSelfExplainerCard(
    isVisible: Boolean,
    onToggle: () -> Unit,
    onOpenTutor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header clickable toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "💡", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "¿CÓMO FUNCIONA ESTA PLATAFORMA?",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                ),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "GUÍA AUTODIDACTA",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = if (isVisible) "Toca para ocultar los principios de funcionamiento" else "Toca para entender cómo aprender solo y alcanzar Nivel 6",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expandir o colapsar",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ExplainerPillarItem(
                        icon = "🧠",
                        title = "1. Tutor de IA Socrático (Bloom 2-Sigma)",
                        description = "Nunca te dará la respuesta resuelta. Si te equivocas, la IA formula preguntas guía, te da pistas graduales, busca analogías de la vida real y aísla tu error conceptual para que entiendas de verdad.",
                    )

                    ExplainerPillarItem(
                        icon = "🗺️",
                        title = "2. Grafo DAG y Frontera de Aprendizaje (ZDP)",
                        description = "El aprendizaje no es una lista plana de temas. Es un grafo sin ciclos. La app evalúa qué prerrequisitos dominas y te presenta solo los conceptos en tu Zona de Desarrollo Próximo, garantizando que nunca te frustres por falta de bases.",
                    )

                    ExplainerPillarItem(
                        icon = "🌍",
                        title = "3. Estándar OCDE PISA & Prueba Perfecta (Nivel 6)",
                        description = "No evaluamos si memorizaste una fórmula de memoria. PISA evalúa si sabes transferir tu saber a situaciones nuevas y auténticas. Tu maestría se limita al 75% si no demuestras transferencia. Para la perfección (800+ pts) debes superar 5 desafíos inéditos.",
                    )

                    ExplainerPillarItem(
                        icon = "📡",
                        title = "4. Aprendizaje 100% Offline y Soberano",
                        description = "Puedes estudiar en el campo o sin internet. Todo tu progreso, respuestas y reflexiones se registran en una cadena inmutable con hash SHA-256 en tu propio teléfono. Al reconectarte, se sincroniza sin trampas ni pérdida de datos.",
                    )

                    ExplainerPillarItem(
                        icon = "📜",
                        title = "5. Certificación Verificable y Puente Económico",
                        description = "Cada logro culmina en una evidencia criptográfica auditable independiente con QR y SHA-256. Además, conecta las ciencias y matemáticas con oficios técnicos reales (mecánica, electricidad, fontanería) para generar ingresos.",
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onOpenTutor) {
                            Text(
                                text = "🧠 Consultar al Tutor Socrático",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplainerPillarItem(
    icon: String,
    title: String,
    description: String,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(text = icon, fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                    ),
                )
            }
        }
    }
}
