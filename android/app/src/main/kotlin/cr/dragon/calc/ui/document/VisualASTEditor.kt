package cr.dragon.calc.ui.document

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import cr.dragon.calc.ui.*
import dragoncore.parser.Expr

/**
 * VisualASTEditor: Un editor estructural premium que permite manipular la lógica
 * de una fórmula mediante bloques visuales (Cards).
 */
@Composable
fun VisualASTEditor(
    expr: Expr,
    onExprChange: (Expr) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(DragonMidGray, RoundedCornerShape(12.dp))
            .border(1.dp, DragonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "ESTRUCTURA AST // NÚCLEO CAS",
            color = DragonCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMono,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ASTNodeView(expr, onExprChange)
    }
}

@Composable
fun ASTNodeView(
    expr: Expr,
    onExprChange: (Expr) -> Unit
) {
    when (expr) {
        is Expr.BinaryExpr -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ASTNodeBox(expr.left, { onExprChange(expr.copy(left = it)) })

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DragonTeal.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(expr.operator.value, color = DragonCyan, fontWeight = FontWeight.Bold)
                }

                ASTNodeBox(expr.right, { onExprChange(expr.copy(right = it)) })
            }
        }
        is Expr.NumberExpr -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(expr.value.toString(), color = DragonGreen)
            }
        }
        is Expr.VariableExpr -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DragonTeal.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(expr.name, color = DragonCyan, fontWeight = FontWeight.Bold)
            }
        }
        is Expr.FunctionExpr -> {
            Column {
                Text(expr.name.value, color = DragonOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(expr.arguments) { arg ->
                        ASTNodeBox(arg, {}) // Simplificado para visualización
                    }
                }
            }
        }
        else -> Text(expr.toString(), color = DragonWhite)
    }
}

@Composable
fun ASTNodeBox(expr: Expr, onExprChange: (Expr) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                // En el futuro: Abrir menú de edición de nodo
            }
            .padding(4.dp)
    ) {
        ASTNodeView(expr, onExprChange)
    }
}
