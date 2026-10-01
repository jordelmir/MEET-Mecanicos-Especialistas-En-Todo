package cr.dragon.calc.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.dragon.calc.ui.*

// =====================================================================
// 1. ESTRUCTURA DE DATOS DEL TECLADO [ELITE V10]
// =====================================================================
data class MathKey(
    val display: String,        // Lo que ve el usuario (ej. "√")
    val latexInsert: String,    // Lo que inyecta (ej. "\\sqrt{}")
    val cursorOffset: Int       // Desplazamiento del cursor (ej. 1 para entrar en {})
)

enum class KeyboardTab { NUMPAD, ALGEBRA, TRIGONOMETRY, CALCULUS }

// =====================================================================
// 2. EL CENTRO DE COMANDO MULTIMODAL [🧠V10 - ELYSIUM EDITION]
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterCommandBar(
    onSendPrompt: (String) -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDrawClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    var isMathKeyboardVisible by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(KeyboardTab.NUMPAD) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DragonBlack)
    ) {
        // Barra de Herramientas Premium
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCameraClick) {
                Icon(Icons.Outlined.CameraAlt, "Escanear", tint = DragonCyan)
            }
            IconButton(onClick = onGalleryClick) {
                Icon(Icons.Outlined.Image, "Galería", tint = DragonCyan)
            }
            IconButton(onClick = onDrawClick) {
                Icon(Icons.Outlined.Edit, "Dibujar", tint = DragonCyan)
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { isMathKeyboardVisible = !isMathKeyboardVisible }) {
                Icon(
                    imageVector = Icons.Outlined.Calculate,
                    contentDescription = "Teclado",
                    tint = if (isMathKeyboardVisible) DragonGreen else DragonGray
                )
            }
        }

        // Input de Texto Imperial
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = { Text("Resuelve algo épico...", color = DragonGray, fontSize = 14.sp) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF151515),
                    unfocusedContainerColor = Color(0xFF151515),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = DragonCyan,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
                onClick = {
                    if (textState.text.isNotBlank()) {
                        onSendPrompt(textState.text)
                        textState = TextFieldValue("")
                    }
                },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if(textState.text.isBlank()) DragonDarkGray else DragonCyan)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Black)
            }
        }

        // TECLADO DE ÉLITE V10 - ELYSIUM IDENTITY
        AnimatedVisibility(
            visible = isMathKeyboardVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090B10))
                    .padding(vertical = 6.dp)
            ) {
                // Selector de Pestañas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    KeyboardTabBtn("123", currentTab == KeyboardTab.NUMPAD) { currentTab = KeyboardTab.NUMPAD }
                    KeyboardTabBtn("Álgebra", currentTab == KeyboardTab.ALGEBRA) { currentTab = KeyboardTab.ALGEBRA }
                    KeyboardTabBtn("Trig", currentTab == KeyboardTab.TRIGONOMETRY) { currentTab = KeyboardTab.TRIGONOMETRY }
                    KeyboardTabBtn("Cálculo", currentTab == KeyboardTab.CALCULUS) { currentTab = KeyboardTab.CALCULUS }
                }

                MathKeyboardGrid(
                    tab = currentTab,
                    onKeyClick = { key ->
                        if (key.latexInsert == "__CLEAR__") {
                            textState = TextFieldValue("")
                        } else if (key.latexInsert == "__DEL__") {
                            val currentText = textState.text
                            val sel = textState.selection.start
                            if (sel > 0) {
                                val newText = currentText.substring(0, sel - 1) + currentText.substring(sel)
                                textState = TextFieldValue(newText, selection = TextRange(sel - 1))
                            }
                        } else {
                            val currentText = textState.text
                            val selectionStart = textState.selection.start
                            val selectionEnd = textState.selection.end
                            val newText = currentText.substring(0, selectionStart) + key.latexInsert + currentText.substring(selectionEnd)
                            val newCursorPos = selectionStart + key.latexInsert.length - key.cursorOffset
                            textState = TextFieldValue(text = newText, selection = TextRange(newCursorPos))
                        }
                    },
                    onBackspace = {
                        val currentText = textState.text
                        val sel = textState.selection.start
                        if (sel > 0) {
                            val newText = currentText.substring(0, sel - 1) + currentText.substring(sel)
                            textState = TextFieldValue(newText, selection = TextRange(sel - 1))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun KeyboardTabBtn(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (isSelected) DragonCyan else Color(0xFF7E8B9B),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(Modifier.height(3.dp))
        if (isSelected) {
            Box(Modifier.width(28.dp).height(2.dp).background(DragonCyan))
        } else {
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
fun MathKeyboardGrid(tab: KeyboardTab, onKeyClick: (MathKey) -> Unit, onBackspace: () -> Unit) {
    val numpadKeys = listOf(
        MathKey("7", "7", 0), MathKey("8", "8", 0), MathKey("9", "9", 0), MathKey("÷", "/", 0), MathKey("DEL", "__DEL__", 0),
        MathKey("4", "4", 0), MathKey("5", "5", 0), MathKey("6", "6", 0), MathKey("×", "*", 0), MathKey("C", "__CLEAR__", 0),
        MathKey("1", "1", 0), MathKey("2", "2", 0), MathKey("3", "3", 0), MathKey("-", "-", 0), MathKey("(", "(", 0),
        MathKey("0", "0", 0), MathKey(".", ".", 0), MathKey("=", "=", 0), MathKey("+", "+", 0), MathKey(")", ")", 0),
        MathKey("x", "x", 0), MathKey("y", "y", 0), MathKey("^", "^{}", 1), MathKey("√", "\\sqrt{}", 1), MathKey("π", "\\pi", 0)
    )

    val algebraKeys = listOf(
        MathKey("x^y", "^{}", 1), MathKey("ⁿ√x", "\\sqrt[{}]{}", 3), MathKey("<", "<", 0), MathKey(">", ">", 0), MathKey("DEL", "__DEL__", 0),
        MathKey("x/y", "\\frac{}{}", 3), MathKey("|x|", "|{}|", 1), MathKey("≤", "\\le", 0), MathKey("≥", "\\ge", 0), MathKey("C", "__CLEAR__", 0),
        MathKey("log_x", "\\log_{}", 1), MathKey("ln", "\\ln()", 1), MathKey("x!", "!", 0), MathKey("i", "i", 0), MathKey("%", "%", 0),
        MathKey("x", "x", 0), MathKey("y", "y", 0), MathKey("z", "z", 0), MathKey("=", "=", 0), MathKey("≠", "\\ne", 0)
    )

    val trigKeys = listOf(
        MathKey("sin", "\\sin()", 1), MathKey("cos", "\\cos()", 1), MathKey("tan", "\\tan()", 1), MathKey("π", "\\pi", 0), MathKey("DEL", "__DEL__", 0),
        MathKey("asin", "\\arcsin()", 1), MathKey("acos", "\\arccos()", 1), MathKey("atan", "\\arctan()", 1), MathKey("x°", "^{\\circ}", 0), MathKey("C", "__CLEAR__", 0),
        MathKey("csc", "\\csc()", 1), MathKey("sec", "\\sec()", 1), MathKey("cot", "\\cot()", 1), MathKey("x²", "^2", 0), MathKey("e", "e", 0),
        MathKey("sinh", "\\sinh()", 1), MathKey("cosh", "\\cosh()", 1), MathKey("tanh", "\\tanh()", 1), MathKey("θ", "\\theta", 0), MathKey("=", "=", 0)
    )

    val calculusKeys = listOf(
        MathKey("d/dx", "\\frac{d}{dx}", 0), MathKey("∫", "\\int ", 0), MathKey("∫_ab", "\\int_{}^{}", 3), MathKey("lim", "\\lim_{x \\to }", 1), MathKey("DEL", "__DEL__", 0),
        MathKey("Σ", "\\sum_{n=1}^{}", 1), MathKey("∏", "\\prod_{n=1}^{}", 1), MathKey("∞", "\\infty", 0), MathKey("√", "\\sqrt{}", 1), MathKey("C", "__CLEAR__", 0),
        MathKey("∂/∂x", "\\frac{\\partial}{\\partial x}", 0), MathKey("∇", "\\nabla", 0), MathKey("log", "\\log_{}", 1), MathKey("ln", "\\ln()", 1), MathKey("e", "e", 0),
        MathKey("C(n,k)", "C(n,k)", 0), MathKey("P(n,k)", "P(n,k)", 0), MathKey("x", "x", 0), MathKey("y", "y", 0), MathKey("=", "=", 0)
    )

    val keys = when(tab) {
        KeyboardTab.NUMPAD -> numpadKeys
        KeyboardTab.ALGEBRA -> algebraKeys
        KeyboardTab.TRIGONOMETRY -> trigKeys
        KeyboardTab.CALCULUS -> calculusKeys
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        userScrollEnabled = false
    ) {
        items(keys) { key ->
            KeyboardBtnV10(key = key, onClick = { onKeyClick(key) })
        }
    }
}

@Composable
fun KeyboardBtnV10(key: MathKey, onClick: () -> Unit) {
    val isAction = key.display in listOf("DEL", "C", "=", "+", "-", "×", "÷", "/")
    val isPrimary = key.display in listOf("=", "DEL", "C")
    val bgColor: Color = when {
        key.display == "=" -> DragonCyan
        key.display in listOf("DEL", "C") -> Color(0xFF251A24)
        isAction -> Color(0xFF131D2A)
        else -> Color(0xFF12141A)
    }
    val textColor: Color = when {
        key.display == "=" -> Color.Black
        key.display in listOf("DEL", "C") -> DragonOrange
        isAction -> DragonCyan
        else -> Color.White
    }
    val borderColor: Color = when {
        key.display == "=" -> DragonCyan
        key.display in listOf("DEL", "C") -> DragonOrange.copy(alpha = 0.4f)
        isAction -> DragonCyan.copy(alpha = 0.35f)
        else -> Color(0xFF222733)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = key.display,
                color = textColor,
                fontSize = if (key.display.length > 3) 11.sp else 14.sp,
                fontWeight = if (isAction || isPrimary) FontWeight.Bold else FontWeight.Medium,
                fontFamily = JetBrainsMono
            )
        }
    }
}
