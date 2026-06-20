package com.elysium369.meet.ui.theme

import androidx.compose.ui.graphics.Color

data class ColorEntry(val color: Color, val name: String)
data class ColorCategory(val title: String, val icon: String, val colors: List<ColorEntry>)

object ThemeColors {
    val FULL_COLOR_PALETTE = listOf(
        ColorCategory("NEÓN PURO", "💡", listOf(
            ColorEntry(Color(0xFF00FFFF), "Cyan"),
            ColorEntry(Color(0xFF00FF00), "Verde"),
            ColorEntry(Color(0xFFFF00FF), "Magenta"),
            ColorEntry(Color(0xFFFFFF00), "Amarillo"),
            ColorEntry(Color(0xFFFF0000), "Rojo"),
            ColorEntry(Color(0xFF0000FF), "Azul"),
            ColorEntry(Color(0xFFFF8000), "Naranja"),
            ColorEntry(Color(0xFFFF0080), "Rosa Neón"),
            ColorEntry(Color(0xFFFFFFFF), "Blanco Puro"),
        )),
        ColorCategory("ELÉCTRICO", "⚡", listOf(
            ColorEntry(Color(0xFF00E5FF), "Cyan Eléctrico"),
            ColorEntry(Color(0xFF00FF7F), "Spring Green"),
            ColorEntry(Color(0xFF7FFF00), "Chartreuse"),
            ColorEntry(Color(0xFFFF1493), "Deep Pink"),
            ColorEntry(Color(0xFF8A2BE2), "Azul Violeta"),
            ColorEntry(Color(0xFFFF4500), "Rojo Naranja"),
            ColorEntry(Color(0xFF00BFFF), "Sky Blue"),
            ColorEntry(Color(0xFFFF69B4), "Rosa"),
            ColorEntry(Color(0xFF32CD32), "Lima"),
        )),
        ColorCategory("FOSFORESCENTE", "☢️", listOf(
            ColorEntry(Color(0xFF76FF03), "Fósforo Verde"),
            ColorEntry(Color(0xFFEEFF41), "Nuclear Amarillo"),
            ColorEntry(Color(0xFFB388FF), "UV Violeta"),
            ColorEntry(Color(0xFF69F0AE), "Radioactivo"),
            ColorEntry(Color(0xFFFF6E40), "Lava Naranja"),
            ColorEntry(Color(0xFF18FFFF), "Fósforo Cyan"),
            ColorEntry(Color(0xFFFFFF8D), "Fósforo Amarillo"),
            ColorEntry(Color(0xFFEA80FC), "Fósforo Púrpura"),
            ColorEntry(Color(0xFF84FFFF), "Hielo Fósforo"),
        )),
        ColorCategory("RACING", "🏎️", listOf(
            ColorEntry(Color(0xFFD50000), "Ferrari Rojo"),
            ColorEntry(Color(0xFFFF6D00), "McLaren Papaya"),
            ColorEntry(Color(0xFFFF9100), "Lambo Naranja"),
            ColorEntry(Color(0xFF00C853), "Racing Verde"),
            ColorEntry(Color(0xFF0091EA), "Racing Azul"),
            ColorEntry(Color(0xFFFFD600), "Racing Amarillo"),
            ColorEntry(Color(0xFFFF1744), "Señal Roja"),
            ColorEntry(Color(0xFFAA00FF), "Racing Violeta"),
            ColorEntry(Color(0xFF304FFE), "Cobalto"),
        )),
        ColorCategory("METÁLICO", "🔩", listOf(
            ColorEntry(Color(0xFFC0C0C0), "Plata"),
            ColorEntry(Color(0xFFFFD700), "Oro"),
            ColorEntry(Color(0xFFE8B4B8), "Oro Rosa"),
            ColorEntry(Color(0xFFB0C4DE), "Acero Azul"),
            ColorEntry(Color(0xFF80D8FF), "Hielo Azul"),
            ColorEntry(Color(0xFFCFD8DC), "Platino"),
            ColorEntry(Color(0xFFFFCC80), "Ámbar Dorado"),
            ColorEntry(Color(0xFF90CAF9), "Acero Ligero"),
            ColorEntry(Color(0xFFCE93D8), "Lavanda Metal"),
        )),
        ColorCategory("FUEGO", "🔥", listOf(
            ColorEntry(Color(0xFFFF3D00), "Brasa"),
            ColorEntry(Color(0xFFDD2C00), "Llama"),
            ColorEntry(Color(0xFFBF360C), "Lava"),
            ColorEntry(Color(0xFFFF6F00), "Infierno"),
            ColorEntry(Color(0xFFFF8F00), "Fuego Dorado"),
            ColorEntry(Color(0xFFFFAB00), "Ámbar Fuego"),
        )),
        ColorCategory("OCÉANO", "🌊", listOf(
            ColorEntry(Color(0xFF006064), "Mar Profundo"),
            ColorEntry(Color(0xFF00838F), "Océano"),
            ColorEntry(Color(0xFF00ACC1), "Marea"),
            ColorEntry(Color(0xFF0097A7), "Marino"),
            ColorEntry(Color(0xFF1DE9B6), "Aurora Teal"),
            ColorEntry(Color(0xFF00E676), "Mar Verde"),
        )),
        ColorCategory("AURORA", "🌌", listOf(
            ColorEntry(Color(0xFF7C4DFF), "Aurora Violeta"),
            ColorEntry(Color(0xFF651FFF), "Aurora Profunda"),
            ColorEntry(Color(0xFFD500F9), "Nebulosa Rosa"),
            ColorEntry(Color(0xFF448AFF), "Cielo Azul"),
            ColorEntry(Color(0xFF536DFE), "Índigo"),
            ColorEntry(Color(0xFF3D5AFE), "Azul Real"),
        )),
        ColorCategory("HIELO", "❄️", listOf(
            ColorEntry(Color(0xFFE0F7FA), "Hielo Blanco"),
            ColorEntry(Color(0xFFB2EBF2), "Escarcha"),
            ColorEntry(Color(0xFF80DEEA), "Glaciar"),
            ColorEntry(Color(0xFF4DD0E1), "Ártico"),
            ColorEntry(Color(0xFF26C6DA), "Polar"),
            ColorEntry(Color(0xFFE1F5FE), "Nieve"),
        )),
    )
}
