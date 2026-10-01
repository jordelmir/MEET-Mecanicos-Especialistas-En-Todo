package cr.dragon.calc.ui.document

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.dragon.calc.ui.*

// Colores Core (Alineados con Color.kt)
val PhysicsPink = Color(0xFFFF0055)
val ChemOrange = Color(0xFFFF9800)

/**
 * 🐉 DynamicAiCell [🧠V10]
 *
 * Orquestador visual de respuestas de IA.
 * Detecta el módulo (Física, Química, Mates) y aplica el puente visual y renderizado LaTeX.
 */
@Composable
fun DynamicAiCell(cell: Cell) {
    val explanation = cell.aiExplanation ?: ""
    val moduleType = cell.aiModuleType ?: "MATH"
    val dragonCode = cell.aiDragonCode ?: ""
    val engine = cell.sourceEngine ?: "Gemini"

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DragonDarkGray),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // 1. EL CEREBRO VISUAL (Puentes de Ciencia)
            when (moduleType.uppercase()) {
                "GRAPH_2D" -> ScienceBridge(Icons.Default.ShowChart, "MOTOR GRÁFICO 2D", dragonCode, DragonCyan)
                "PHYSICS" -> ScienceBridge(Icons.Default.RocketLaunch, "LABORATORIO DE FÍSICA", dragonCode, PhysicsPink)
                "CHEMISTRY" -> ScienceBridge(Icons.Default.Science, "QUÍMICA CUÁNTICA", dragonCode, ChemOrange)
                "STATS" -> ScienceBridge(Icons.Default.BarChart, "ANALÍTICA DE DATOS", dragonCode, Color.Yellow)
                "MATH" -> {
                    if (dragonCode.isNotEmpty() && dragonCode != "ERROR_PARSE" && !dragonCode.contains("ERROR")) {
                        ScienceBridge(Icons.Default.Functions, "NÚCLEO MATEMÁTICO", dragonCode, DragonCyan)
                    }
                }
            }

            if (moduleType != "ERROR" && dragonCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2. RENDERIZADOR LATEX DE ÉLITE (KaTeX)
            if (explanation.isNotEmpty()) {
                LaTeXRenderer(latexText = explanation)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = DragonGray.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // 3. FIRMA DEL MOTOR
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = DragonGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Procesado por: $engine",
                        color = DragonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = DragonGray.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = moduleType,
                        color = DragonGray,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 🏗️ EL PUENTE VISUAL UNIVERSAL 🏗️
 */
@Composable
fun ScienceBridge(icon: ImageVector, title: String, code: String, themeColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DragonBlack)
            .border(1.dp, themeColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title, tint = themeColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = themeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
            if (code.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = code,
                    color = DragonWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
