package com.elysium369.meet.ui.components.hud

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 10 unique HUD dashboard face layouts inspired by premium OBD2 HUD devices.
 * Each face completely changes the instrument cluster layout and visual style.
 */
enum class HudFaceType(
    val displayName: String,
    val icon: String,
    val description: String
) {
    NEON_DIGITAL(
        displayName = "Neón Digital",
        icon = "💚",
        description = "Arco RPM semicircular verde neón con velocidad digital grande"
    ),
    PREMIUM_COCKPIT(
        displayName = "Cockpit Premium",
        icon = "🔵",
        description = "Velocímetro central azul con arco RPM y datos múltiples"
    ),
    MULTI_GAUGE(
        displayName = "Multi Gauge",
        icon = "🎯",
        description = "Múltiples gauges circulares con barras de temperatura"
    ),
    MINIMAL_HUD(
        displayName = "Minimal HUD",
        icon = "⬜",
        description = "Ultra limpio: solo velocidad y RPM, máxima legibilidad"
    ),
    RACING_F1(
        displayName = "Racing F1",
        icon = "🏎️",
        description = "Barra RPM horizontal estilo F1 con shift lights"
    ),
    DUAL_RING(
        displayName = "Dual Ring",
        icon = "💠",
        description = "Anillos concéntricos RPM/Velocidad con readouts digitales"
    ),
    MILITARY_OPS(
        displayName = "Night Ops",
        icon = "🪖",
        description = "Visión nocturna verde fósforo estilo militar"
    ),
    CYBER_MATRIX(
        displayName = "Cyber Matrix",
        icon = "🟢",
        description = "Estilo cyberpunk con datos flotantes tipo matrix"
    ),
    RETRO_ANALOG(
        displayName = "Retro Analog",
        icon = "⏱️",
        description = "Agujas analógicas clásicas estilo vintage"
    ),
    TESLA_CLEAN(
        displayName = "Tesla Clean",
        icon = "🤍",
        description = "Moderno minimalista con barras horizontales limpias"
    );

    fun next(): HudFaceType {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    fun previous(): HudFaceType {
        val values = entries
        return values[if (ordinal == 0) values.size - 1 else ordinal - 1]
    }
}

/**
 * Manages the currently selected HUD face with SharedPreferences persistence.
 */
class HudFaceManager(context: Context) {
    private val prefs = context.getSharedPreferences("meet_hud_face_prefs", Context.MODE_PRIVATE)
    private val KEY_FACE = "selected_hud_face"

    private val _currentFace = MutableStateFlow(loadFace())
    val currentFace: StateFlow<HudFaceType> = _currentFace.asStateFlow()

    private fun loadFace(): HudFaceType {
        val saved = prefs.getString(KEY_FACE, null)
        return try {
            if (saved != null) HudFaceType.valueOf(saved) else HudFaceType.NEON_DIGITAL
        } catch (e: Exception) {
            HudFaceType.NEON_DIGITAL
        }
    }

    fun selectFace(face: HudFaceType) {
        _currentFace.value = face
        prefs.edit().putString(KEY_FACE, face.name).apply()
    }

    fun cycleNext() { selectFace(_currentFace.value.next()) }
    fun cyclePrevious() { selectFace(_currentFace.value.previous()) }
}
