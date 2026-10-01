package cr.dragon.calc.ui.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.dragon.calc.ui.*

/**
 * DragonKeyboard — Responsive Math IME
 *
 * Adapts layout based on screen dimensions:
 *   - Small phones (< 600dp): 4 columns, normal button height
 *   - Medium/tablets (600-840dp): 5 columns, compact button height
 *   - Large/foldables (> 840dp): 6 columns, ultra-compact layout
 *
 * The keyboard is capped to a maximum fraction of screen height
 * to always leave room for document cells and graphs above.
 */
@Composable
fun DragonKeyboard(
    onInput: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onEquals: () -> Unit,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(KeyboardMode.NUMPAD) }

    val config = LocalConfiguration.current
    val screenHeightDp = config.screenHeightDp
    val screenWidthDp = config.screenWidthDp

    // Adaptive grid columns: 4 for clean ergonomic calculator layout
    val gridColumns = 4

    // Max keyboard height dynamically adjusted to screen
    val maxKeyboardHeightDp = when {
        screenHeightDp > 850 -> (screenHeightDp * 0.35).dp
        screenHeightDp > 600 -> (screenHeightDp * 0.40).dp
        else -> (screenHeightDp * 0.48).dp
    }

    val buttonHeight: Dp = when {
        screenHeightDp > 850 -> 44.dp
        screenHeightDp > 600 -> 48.dp
        else -> 52.dp
    }

    val tabFontSize = if (screenWidthDp > 600) 11.sp else 12.sp
    val baseFontScale = if (screenHeightDp > 800) 0.9f else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxKeyboardHeightDp)
            .background(Color(0xFF070B12))
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF00E5FF).copy(alpha = 0.35f), Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(top = 4.dp)
    ) {
        // ── Elysium Cyber Tab Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyboardMode.entries.forEach { tabMode ->
                val isSelected = (mode == tabMode)
                val label = when (tabMode) {
                    KeyboardMode.NUMPAD -> "PAD"
                    KeyboardMode.NUMERIC -> "CALC"
                    KeyboardMode.SCIENTIFIC -> "SCI"
                    KeyboardMode.MATRIX -> "MAT"
                    KeyboardMode.SCRIPT -> "CODE"
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { mode = tabMode },
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color(0xFF0D121D),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF)
                    )
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF7E8B9B),
                        fontSize = tabFontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ── Keyboard Grid ──
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "keyboard_anim"
        ) { targetMode ->
            val buttons = when (targetMode) {
                KeyboardMode.NUMPAD -> DragonKeyboardLayouts.NUMPAD
                KeyboardMode.NUMERIC -> DragonKeyboardLayouts.NUMERIC
                KeyboardMode.SCIENTIFIC -> DragonKeyboardLayouts.SCIENTIFIC
                KeyboardMode.MATRIX -> DragonKeyboardLayouts.MATRIX
                KeyboardMode.SCRIPT -> DragonKeyboardLayouts.SCRIPT
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .padding(bottom = 6.dp),
                contentPadding = PaddingValues(2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(buttons, key = { it.label }) { btn ->
                    val onClick = {
                        when (btn.label) {
                            "AC", "C" -> onClear()
                            "DEL" -> onDelete()
                            "=" -> onEquals()
                            "\u2190" -> onNavigate(-1)
                            "\u2192" -> onNavigate(1)
                            else -> onInput(btn.input)
                        }
                    }
                    CalcButton(
                        btn = btn,
                        onClick = onClick,
                        buttonHeight = buttonHeight,
                        fontScale = baseFontScale
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    btn: CalcBtn,
    onClick: () -> Unit,
    buttonHeight: Dp,
    fontScale: Float,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseBgColor = when (btn.type) {
        BtnType.EQUAL   -> Color(0xFF00B0FF)
        BtnType.SPECIAL -> Color(0xFF221118)
        BtnType.OP      -> Color(0xFF0D1D2B)
        BtnType.FUNC    -> Color(0xFF1A1329)
        BtnType.NAV     -> Color(0xFF1B1B26)
        BtnType.NUM     -> Color(0xFF121722)
    }

    val borderColor = when (btn.type) {
        BtnType.EQUAL   -> Color(0xFF00E5FF)
        BtnType.SPECIAL -> Color(0xFFFF4081).copy(alpha = 0.5f)
        BtnType.OP      -> Color(0xFF00E5FF).copy(alpha = 0.45f)
        BtnType.FUNC    -> Color(0xFFB388FF).copy(alpha = 0.45f)
        BtnType.NAV     -> Color(0xFFFFD700).copy(alpha = 0.45f)
        BtnType.NUM     -> Color(0x33FFFFFF)
    }

    val textColor = when (btn.type) {
        BtnType.NUM     -> Color(0xFFF0F4F8)
        BtnType.OP      -> Color(0xFF00E5FF)
        BtnType.FUNC    -> Color(0xFFC792EA)
        BtnType.SPECIAL -> Color(0xFFFF5252)
        BtnType.EQUAL   -> Color(0xFF000814)
        BtnType.NAV     -> Color(0xFFFFD700)
    }

    val textSize = when {
        btn.label.length > 3 -> (12 * fontScale).sp
        btn.label.length > 2 -> (13 * fontScale).sp
        btn.type == BtnType.EQUAL -> (22 * fontScale).sp
        btn.type == BtnType.NAV -> (16 * fontScale).sp
        btn.type == BtnType.FUNC -> (13 * fontScale).sp
        else -> (18 * fontScale).sp
    }

    Surface(
        modifier = modifier
            .height(buttonHeight)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isPressed) baseBgColor.copy(alpha = 0.7f) else baseBgColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isPressed) 1.5.dp else 1.dp,
            color = if (isPressed) Color.White else borderColor
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = btn.label,
                color = textColor,
                fontSize = textSize,
                fontWeight = if (btn.type == BtnType.EQUAL || btn.type == BtnType.SPECIAL) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
