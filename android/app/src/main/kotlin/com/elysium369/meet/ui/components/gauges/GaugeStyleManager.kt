package com.elysium369.meet.ui.components.gauges

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All available gauge visual styles for the Scanner dashboard.
 * Each style completely replaces the gauge rendering across the entire dashboard.
 * New styles can be added here — the cycle button will automatically include them.
 */
enum class GaugeStyleSet(
    val displayName: String,
    val icon: String,
    val description: String
) {
    ELITE(
        displayName = "Elite Tactical",
        icon = "🎯",
        description = "Estilo militar táctico con aguja analógica y CRT scanlines"
    ),
    CLASSIC(
        displayName = "Classic V2.5",
        icon = "⚡",
        description = "Arco limpio y minimalista con valor digital grande"
    ),
    CYBER(
        displayName = "Cyber HUD",
        icon = "🔵",
        description = "Display holográfico tipo videojuego con barras neón"
    ),
    RACING(
        displayName = "Racing F1",
        icon = "🏎️",
        description = "Segmentos LED tipo F1 con shift lights"
    ),
    RADIAL(
        displayName = "Radial Smartwatch",
        icon = "💎",
        description = "Anillo grueso con gradiente estilo smartwatch"
    ),
    THERMO(
        displayName = "Industrial",
        icon = "🌡️",
        description = "Barra vertical tipo termómetro industrial"
    ),
    HOLOGRAM(
        displayName = "Holographic",
        icon = "🔮",
        description = "Anillos concéntricos holográficos estilo Sci-Fi"
    ),
    NEON_RETRO(
        displayName = "Neon Retrowave",
        icon = "🌆",
        description = "Estética synthwave 80s con glow extremo"
    ),
    LAMBO(
        displayName = "Lamborghini",
        icon = "🔶",
        description = "Cockpit angular estilo Reventón con naranja eléctrico"
    ),
    PLASMA(
        displayName = "Plasma Energy",
        icon = "⚡",
        description = "Campo de plasma azul/violeta con arcos de energía"
    ),
    AURORA(
        displayName = "Midnight Aurora",
        icon = "🌌",
        description = "Aurora boreal púrpura/teal con partículas flotantes"
    ),
    FERRARI(
        displayName = "Ferrari Rosso",
        icon = "🏁",
        description = "Rosso Corsa con shift lights y aguja roja italiana"
    ),
    TOKYO(
        displayName = "Tokyo Drift",
        icon = "🗼",
        description = "JDM midnight blue con rosa sakura y kanji"
    ),
    MILITARY(
        displayName = "Night Ops",
        icon = "🪖",
        description = "Visión nocturna verde fósforo con radar táctico"
    ),
    DIAMOND(
        displayName = "Diamond Luxury",
        icon = "💠",
        description = "Bugatti chrome/plata con azul hielo y joya dorada"
    ),
    COCKPIT(
        displayName = "Cockpit Avión",
        icon = "✈️",
        description = "Panel de avión con ámbar/verde y aguja triangular"
    );

    fun next(): GaugeStyleSet {
        val values = entries
        val nextIndex = (ordinal + 1) % values.size
        return values[nextIndex]
    }

    fun previous(): GaugeStyleSet {
        val values = entries
        val prevIndex = if (ordinal == 0) values.size - 1 else ordinal - 1
        return values[prevIndex]
    }
}

/**
 * Manages the currently selected gauge style with SharedPreferences persistence.
 * Injected as a singleton via Hilt so all screens share the same style state.
 */
class GaugeStyleManager(context: Context) {

    private val prefs = context.getSharedPreferences("meet_gauge_prefs", Context.MODE_PRIVATE)
    private val KEY_STYLE = "selected_gauge_style"

    private val _currentStyle = MutableStateFlow(loadStyle())
    val currentStyle: StateFlow<GaugeStyleSet> = _currentStyle.asStateFlow()

    private fun loadStyle(): GaugeStyleSet {
        val saved = prefs.getString(KEY_STYLE, null)
        return try {
            if (saved != null) GaugeStyleSet.valueOf(saved) else GaugeStyleSet.ELITE
        } catch (e: Exception) {
            GaugeStyleSet.ELITE
        }
    }

    fun selectStyle(style: GaugeStyleSet) {
        _currentStyle.value = style
        prefs.edit().putString(KEY_STYLE, style.name).apply()
    }

    fun cycleNext() {
        selectStyle(_currentStyle.value.next())
    }

    fun cyclePrevious() {
        selectStyle(_currentStyle.value.previous())
    }
}
