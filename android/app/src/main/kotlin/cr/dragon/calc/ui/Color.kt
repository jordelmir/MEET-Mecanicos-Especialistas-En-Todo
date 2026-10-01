package cr.dragon.calc.ui

import androidx.compose.ui.graphics.Color

/**
 * Chroma System - Paleta Dragon
 *
 * Colores estrictos del Proyecto Dragon basados en la especificacion UI.
 */

// --- Fondos ---
val DragonBlack: Color get() = if (com.elysium369.meet.ui.theme.MeetColors.isWhiteTheme) Color.White else Color(0xFF000000)
val DragonDarkGray: Color get() = if (com.elysium369.meet.ui.theme.MeetColors.isWhiteTheme) Color(0xFFF5F7FA) else Color(0xFF12151A)
val DragonMidGray: Color get() = if (com.elysium369.meet.ui.theme.MeetColors.isWhiteTheme) Color(0xFFE5E9F0) else Color(0xFF1A1F29)

// --- Acentos ---
val DragonTeal       = Color(0xFF45A29E)  // Operadores primarios (+, -, *, /)
val DragonCyan       = Color(0xFF66FCF1)  // Funciones avanzadas (sin, cos, etc.)
val DragonOrange     = Color(0xFFFF5722)  // Alertas, errores, AC/DEL

// --- Texto ---
val DragonWhite: Color get() = if (com.elysium369.meet.ui.theme.MeetColors.isWhiteTheme) Color(0xFF0F172A) else Color(0xFFFFFFFF)
val DragonGray: Color get() = if (com.elysium369.meet.ui.theme.MeetColors.isWhiteTheme) Color(0xFF475569) else Color(0xFF8892A0)
val DragonDimWhite: Color get() = if (com.elysium369.meet.ui.theme.MeetColors.isWhiteTheme) Color(0xFF64748B) else Color(0xFFC5C6C7)

// --- Especiales ---
val DragonGreen      = Color(0xFF4CAF50)  // Bordes activos y focus
val DragonGradStart  = Color(0xFF45A29E)  // Gradiente boton = (inicio)
val DragonGradEnd    = Color(0xFF66FCF1)  // Gradiente boton = (fin)

// --- Acentos Premium ---
val DragonGold       = Color(0xFFFFD700)  // Modo Tutor: pasos y resaltados
