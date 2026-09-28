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

enum class KeyboardTab { ALGEBRA, TRIGONOMETRY, CALCULUS }

// =====================================================================
// 2. EL CENTRO DE COMANDO MULTIMODAL [🧠V10 - IMPERIAL EDITION]
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
    var isMathKeyboardVisible by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(KeyboardTab.ALGEBRA) }

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

        // TECLADO DE ÉLITE V10
        AnimatedVisibility(
            visible = isMathKeyboardVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(top = 8.dp)
            ) {
                // Selector de Pestañas (Design Inspired)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    KeyboardTabBtn("Algebra", currentTab == KeyboardTab.ALGEBRA) { currentTab = KeyboardTab.ALGEBRA }
                    KeyboardTabBtn("Trigonometry", currentTab == KeyboardTab.TRIGONOMETRY) { currentTab = KeyboardTab.TRIGONOMETRY }
                    KeyboardTabBtn("Calculus", currentTab == KeyboardTab.CALCULUS) { currentTab = KeyboardTab.CALCULUS }
                }

                MathKeyboardGrid(
                    tab = currentTab,
                    onKeyClick = { key ->
                        val currentText = textState.text
                        val selectionStart = textState.selection.start
                        val selectionEnd = textState.selection.end
                        val newText = currentText.substring(0, selectionStart) + key.latexInsert + currentText.substring(selectionEnd)
                        val newCursorPos = selectionStart + key.latexInsert.length - key.cursorOffset
                        textState = TextFieldValue(text = newText, selection = TextRange(newCursorPos))
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

                // Bubble Suggestion [Imperial Context]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SuggestionBubble("Linear equations", "6x + 5 = 14") { textState = TextFieldValue("6x + 5 = 14") }
                    SuggestionBubble("Polynomials", "(x + 5)(x + 2)") { textState = TextFieldValue("(x + 5)(x + 2)") }
                    SuggestionBubble("Integrals", "\\int x^2 dx") { textState = TextFieldValue("\\int x^2 dx") }
                }
            }
        }
    }
}

@Composable
fun KeyboardTabBtn(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(4.dp))
        if (isSelected) {
            Box(Modifier.width(24.dp).height(2.dp).background(DragonCyan))
        }
    }
}

@Composable
fun MathKeyboardGrid(tab: KeyboardTab, onKeyClick: (MathKey) -> Unit, onBackspace: () -> Unit) {
    val algebraKeys = listOf(
        MathKey("x^y", "^{}", 1), MathKey("ⁿ√x", "\\sqrt[{}]{}", 3), MathKey("<", "<", 0),
        MathKey("x/y", "\\frac{}{}", 3), MathKey("|x|", "|{}|", 1), MathKey("≤", "\\le", 0),
        MathKey("log_x", "\\log_{}", 1), MathKey("x!", "!", 0), MathKey(">", ">", 0),
        MathKey("i", "i", 0), MathKey("%", "%", 0), MathKey("≥", "\\ge", 0),
        MathKey("x", "x", 0), MathKey("y", "y", 0), MathKey("=", "=", 0)
    )

    val trigKeys = listOf(
        MathKey("sin", "\\sin()", 1), MathKey("cos", "\\cos()", 1), MathKey("tan", "\\tan()", 1),
        MathKey("csc", "\\csc()", 1), MathKey("sec", "\\sec()", 1), MathKey("cot", "\\cot()", 1),
        MathKey("asin", "\\arcsin()", 1), MathKey("acos", "\\arccos()", 1), MathKey("atan", "\\arctan()", 1),
        MathKey("x²", "^2", 0), MathKey("x°", "^{\\circ}", 0), MathKey("π", "\\pi", 0),
        MathKey("x", "x", 0), MathKey("y", "y", 0), MathKey("=", "=", 0)
    )

    val calculusKeys = listOf(
        MathKey("d/dx", "\\frac{d}{d}", 0), MathKey("∞", "\\infty", 0), MathKey("√", "\\sqrt{}", 1),
        MathKey("limX", "\\lim_{ \\to }", 2), MathKey("lim+", "\\lim_{ \\to ^+}", 3), MathKey("lim-", "\\lim_{ \\to ^-}", 3),
        MathKey("log", "\\log_{}", 1), MathKey("C(n,k)", "C(n,k)", 0), MathKey("P(n,k)", "P(n,k)", 0),
        MathKey("Σ", "\\sum_{}^{}", 4), MathKey("∫", "\\int ", 0), MathKey("∫_ab", "\\int_{}^{}", 3),
        MathKey("x", "x", 0), MathKey("y", "y", 0), MathKey("e", "e", 0)
    )

    val keys = when(tab) {
        KeyboardTab.ALGEBRA -> algebraKeys
        KeyboardTab.TRIGONOMETRY -> trigKeys
        KeyboardTab.CALCULUS -> calculusKeys
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        items(keys) { key ->
            KeyboardBtnV10(text = key.display, onClick = { onKeyClick(key) })
        }
    }
}

@Composable
fun KeyboardBtnV10(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        color = Color(0xFF1E1F22),
        shape = RoundedCornerShape(25.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun SuggestionBubble(label: String, example: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray),
        shape = RoundedCornerShape(30.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Text(example, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light)
        }
    }
}
