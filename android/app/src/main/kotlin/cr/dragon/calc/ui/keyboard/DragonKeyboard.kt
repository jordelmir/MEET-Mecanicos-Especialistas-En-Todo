package cr.dragon.calc.ui.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
    var mode by remember { mutableStateOf(KeyboardMode.NUMERIC) }

    val config = LocalConfiguration.current
    val screenHeightDp = config.screenHeightDp
    val screenWidthDp = config.screenWidthDp

    // Adaptive parameters based on screen size
    // Force 4 columns to preserve the "Calculator" logic (7-8-9 row, etc.)
    // on all screen sizes, as requested for a "World Class" experience.
    val gridColumns = 4

    // Max keyboard height: never exceed 35% of screen on tall screens,
    // 40% on medium, 45% on small
    val maxKeyboardHeightDp = when {
        screenHeightDp > 800 -> (screenHeightDp * 0.32).dp
        screenHeightDp > 600 -> (screenHeightDp * 0.38).dp
        else -> (screenHeightDp * 0.45).dp
    }

    // Button height adapts to screen — shorter on larger displays
    val buttonHeight: Dp = when {
        screenHeightDp > 800 -> 42.dp
        screenHeightDp > 600 -> 46.dp
        else -> 52.dp
    }

    // Tab text size adapts
    val tabFontSize = when {
        screenWidthDp > 600 -> 12.sp
        else -> 14.sp
    }
    val tabVertPadding = when {
        screenHeightDp > 800 -> 4.dp
        else -> 8.dp
    }

    // Button text size adapts
    val baseFontScale = when {
        screenHeightDp > 800 -> 0.85f
        else -> 1f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxKeyboardHeightDp) // Hard cap!
            .background(DragonBlack)
    ) {
        // ── Tab Bar (compact on large screens) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            KeyboardMode.entries.forEach { tabMode ->
                val isSelected = (mode == tabMode)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { mode = tabMode },
                    color = if (isSelected) DragonMidGray else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = tabMode.name.take(3),
                        color = if (isSelected) DragonCyan else DragonGray,
                        fontSize = tabFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = tabVertPadding)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ── Keyboard Grid ──
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label = "keyboard_anim"
        ) { targetMode ->
            val buttons = when (targetMode) {
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
                    .padding(bottom = 4.dp),
                contentPadding = PaddingValues(2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(buttons, key = { it.label }) { btn ->
                    val onClick = {
                        when (btn.label) {
                            "AC" -> onClear()
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

    val bgColor = when (btn.type) {
        BtnType.EQUAL -> DragonTeal
        else -> if (isPressed) DragonMidGray else DragonDarkGray
    }

    val textColor = when (btn.type) {
        BtnType.NUM     -> DragonWhite
        BtnType.OP      -> DragonTeal
        BtnType.FUNC    -> DragonCyan
        BtnType.SPECIAL -> DragonOrange
        BtnType.EQUAL   -> DragonWhite
        BtnType.NAV     -> DragonOrange
    }

    val textSize = when {
        btn.label.length > 2 -> (14 * fontScale).sp
        btn.type == BtnType.EQUAL -> (22 * fontScale).sp
        btn.type == BtnType.NAV -> (16 * fontScale).sp
        btn.type == BtnType.FUNC -> (13 * fontScale).sp
        else -> (18 * fontScale).sp
    }

    Surface(
        modifier = modifier
            .height(buttonHeight)    // Fixed height instead of aspectRatio
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        tonalElevation = if (btn.type == BtnType.EQUAL) 8.dp else 2.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = btn.label,
                color = textColor,
                fontSize = textSize,
                fontWeight = if (btn.type == BtnType.EQUAL) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
