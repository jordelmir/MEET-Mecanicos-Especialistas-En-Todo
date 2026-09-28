package cr.dragon.calc.ui

import androidx.compose.ui.graphics.Color

/**
 * Chroma System - Paleta Dragon
 *
 * Colores estrictos del Proyecto Dragon basados en la especificacion UI.
 */

// --- Fondos ---
val DragonBlack      = Color(0xFF000000)  // Fondo principal (OLED True Black)
val DragonDarkGray   = Color(0xFF12151A)  // Superficies, contenedores, teclado (Ajustado)
val DragonMidGray    = Color(0xFF1A1F29)  // Hover / estados activos (Ajustado)

// --- Acentos ---
val DragonTeal       = Color(0xFF45A29E)  // Operadores primarios (+, -, *, /)
val DragonCyan       = Color(0xFF66FCF1)  // Funciones avanzadas (sin, cos, etc.)
val DragonOrange     = Color(0xFFFF5722)  // Alertas, errores, AC/DEL

// --- Texto ---
val DragonWhite      = Color(0xFFFFFFFF)  // Digitos, variables, texto principal
val DragonGray       = Color(0xFF8892A0)  // Texto secundario, labels
val DragonDimWhite   = Color(0xFFC5C6C7)  // Texto deshabilitado

// --- Especiales ---
val DragonGreen      = Color(0xFF4CAF50)  // Bordes activos y focus
val DragonGradStart  = Color(0xFF45A29E)  // Gradiente boton = (inicio)
val DragonGradEnd    = Color(0xFF66FCF1)  // Gradiente boton = (fin)

// --- Acentos Premium ---
val DragonGold       = Color(0xFFFFD700)  // Modo Tutor: pasos y resaltados
