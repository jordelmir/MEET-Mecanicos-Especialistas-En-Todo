package cr.dragon.calc.ui.document

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.dragon.calc.ui.DragonBlack
import cr.dragon.calc.ui.DragonGray
import cr.dragon.calc.ui.DragonGreen
import cr.dragon.calc.ui.DragonOrange
import cr.dragon.calc.ui.DragonTeal
import cr.dragon.calc.ui.DragonWhite

@Composable
fun PromptCellView(
    cell: Cell,
    activeEngineName: String,
    onValueChange: (Cell) -> Unit,
    onSubmit: (Cell) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(DragonBlack.copy(alpha = 0.5f))
            .border(1.dp, DragonOrange.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Ask AI",
            tint = DragonOrange,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (cell.content.text.isEmpty()) {
                    Text(
                        text = "Ordena una acción al DragonBrain...",
                        color = DragonWhite.copy(alpha = 0.4f),
                        fontSize = 16.sp
                    )
                }

                BasicTextField(
                    value = cell.content,
                    onValueChange = { onValueChange(cell.copy(content = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = DragonWhite, fontSize = 16.sp),
                    cursorBrush = SolidColor(DragonOrange),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { onSubmit(cell) }
                    )
                )
            }

            if (cell.translatedContent.isNotEmpty()) {
                Text(
                    text = "[$activeEngineName] Interpretación: ${cell.translatedContent}",
                    color = DragonOrange.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (cell.aiExplanation?.isNotEmpty() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                DynamicAiCell(cell = cell)
            }
        }

        if (cell.isExecuting) {
            Spacer(modifier = Modifier.width(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = DragonOrange,
                strokeWidth = 2.dp
            )
        }
    }
}
