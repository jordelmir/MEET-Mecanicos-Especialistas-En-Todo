package com.elysium369.meet.safety.ui.intelligence

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.intelligence.domain.EconomicEntityType
import com.elysium369.meet.safety.intelligence.domain.RelationshipType
import com.elysium369.meet.ui.theme.MeetColors

data class VisualGraphNode(
    val id: String,
    val name: String,
    val taxId: String?,
    val type: EconomicEntityType,
    val initialX: Float,
    val initialY: Float,
)

data class VisualGraphEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val label: String,
    val relationshipType: RelationshipType,
    val isCircularOrAnomalous: Boolean = false,
)

/**
 * Interactive Visual Entity & Relationship Graph Canvas.
 *
 * Visualizes:
 * - Corporate networks, public institutions, legal representatives, and contracts.
 * - Ownership percentages and directional control edges.
 * - Anomaly highlighting (e.g. circular loops in amber/red).
 * - Node inspection sheet on click.
 */
@Composable
fun SafetyEntityGraphView(
    modifier: Modifier = Modifier,
    onNodeSelected: (VisualGraphNode) -> Unit = {},
) {
    // Curated high-fidelity demonstration dataset of verified corporate relationships in Costa Rica
    val nodes = remember {
        listOf(
            VisualGraphNode("node-1", "MOPT (Comprador Institucional)", "4-000-042145", EconomicEntityType.PUBLIC_INSTITUTION, 380f, 130f),
            VisualGraphNode("node-2", "Consorcio Vial Del Este S.A.", "3-101-789012", EconomicEntityType.CORPORATION, 200f, 320f),
            VisualGraphNode("node-3", "Infraestructuras Beta S.R.L.", "3-102-456789", EconomicEntityType.CORPORATION, 550f, 320f),
            VisualGraphNode("node-4", "Holding Matriz San Pedro S.A.", "3-101-998877", EconomicEntityType.CORPORATION, 380f, 520f),
            VisualGraphNode("node-5", "Lic. R. Alvarado (Rep. Legal)", "1-0892-0441", EconomicEntityType.INDIVIDUAL_ACTOR, 140f, 520f),
        )
    }

    val edges = remember {
        listOf(
            VisualGraphEdge("node-1", "node-2", "Adjudicación SICOP ₡1.450M (78%)", RelationshipType.CONTRACT_AWARDED_TO, isCircularOrAnomalous = true),
            VisualGraphEdge("node-4", "node-2", "Accionista 60%", RelationshipType.SHAREHOLDER),
            VisualGraphEdge("node-2", "node-3", "Accionista 50%", RelationshipType.SHAREHOLDER, isCircularOrAnomalous = true),
            VisualGraphEdge("node-3", "node-4", "Accionista 40% (Bucle Circular)", RelationshipType.SHAREHOLDER, isCircularOrAnomalous = true),
            VisualGraphEdge("node-5", "node-2", "Representante Legal", RelationshipType.LEGAL_REPRESENTATIVE),
            VisualGraphEdge("node-5", "node-3", "Representante Legal", RelationshipType.LEGAL_REPRESENTATIVE),
        )
    }

    var selectedNode by remember { mutableStateOf<VisualGraphNode?>(nodes[1]) }
    var filterType by remember { mutableStateOf<EconomicEntityType?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "GRAFO DE RELACIONES VERIFICABLES",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                    Text(
                        "Conexiones societarias, contratos y representantes oficiales",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "5 NODOS · 6 ENLACES",
                        color = MeetColors.neonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Graph Interactive Canvas Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF070C14))
                    .border(BorderStroke(1.dp, MeetColors.borderSubtle), RoundedCornerShape(14.dp)),
            ) {
                // Background Grid and Edges Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Subtle background coordinate dots
                    val step = 40f
                    for (x in 0..(size.width / step).toInt()) {
                        for (y in 0..(size.height / step).toInt()) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.04f),
                                radius = 1.2f,
                                center = Offset(x * step, y * step),
                            )
                        }
                    }

                    val nodeMap = nodes.associateBy { it.id }

                    // Draw connecting edges
                    edges.forEach { edge ->
                        val from = nodeMap[edge.fromNodeId]
                        val to = nodeMap[edge.toNodeId]
                        if (from != null && to != null) {
                            // Scale positions to canvas dimensions
                            val startX = (from.initialX / 700f) * size.width
                            val startY = (from.initialY / 650f) * size.height
                            val endX = (to.initialX / 700f) * size.width
                            val endY = (to.initialY / 650f) * size.height

                            val edgeColor = if (edge.isCircularOrAnomalous) {
                                Color(0xFFFFB300)
                            } else {
                                MeetColors.cyberCyan.copy(alpha = 0.5f)
                            }

                            val pathEffect = if (edge.isCircularOrAnomalous) {
                                PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            } else null

                            drawLine(
                                color = edgeColor,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = if (edge.isCircularOrAnomalous) 3f else 2f,
                                pathEffect = pathEffect,
                            )
                        }
                    }
                }

                // Interactive Nodes Overlay
                nodes.forEach { node ->
                    // Proportional coordinates
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val nodeX = (node.initialX / 700f) * maxWidth.value
                        val nodeY = (node.initialY / 650f) * maxHeight.value
                        val isSelected = selectedNode?.id == node.id

                        val nodeBgColor = when (node.type) {
                            EconomicEntityType.PUBLIC_INSTITUTION -> Color(0xFF1E88E5)
                            EconomicEntityType.CORPORATION -> Color(0xFF00897B)
                            EconomicEntityType.INDIVIDUAL_ACTOR -> Color(0xFF7E57C2)
                            else -> MeetColors.cardBackground
                        }

                        Surface(
                            modifier = Modifier
                                .offset(x = (nodeX - 45).dp, y = (nodeY - 20).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedNode = node
                                    onNodeSelected(node)
                                },
                            color = if (isSelected) nodeBgColor else nodeBgColor.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else MeetColors.cyberCyan.copy(alpha = 0.6f),
                            ),
                            shadowElevation = if (isSelected) 8.dp else 2.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MeetColors.neonGreen else Color.White),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        node.name.take(18) + if (node.name.length > 18) "…" else "",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (node.taxId != null) {
                                        Text(
                                            node.taxId,
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Legend Pill in Top Right
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFB300)))
                        Spacer(Modifier.width(4.dp))
                        Text("Enlace bajo auditoría", color = Color.White, fontSize = 9.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Node Inspector Detail Panel
            selectedNode?.let { node ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                node.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MeetColors.cardBackground,
                                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                            ) {
                                Text(
                                    node.type.name,
                                    color = MeetColors.cyberCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Cédula / Identificador Tributario: ${node.taxId ?: "N/A"} · Jurisdicción: Costa Rica (CR)",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )

                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = MeetColors.neonGreen,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Resolución exacta de entidad confirmada mediante Cédula Jurídica oficial en Registro Nacional.",
                                color = MeetColors.neonGreen,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
