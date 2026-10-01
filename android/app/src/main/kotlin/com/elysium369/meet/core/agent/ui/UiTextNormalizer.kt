package com.elysium369.meet.core.agent.ui

import java.text.Normalizer

/**
 * ══════════════════════════════════════════════════════════════════════
 *  U I   T E X T   N O R M A L I Z E R
 *  ──────────────────────────────────────────────────────────────
 *  Normaliza texto hablado y etiquetas de la interfaz para comparación
 *  determinista, insensible a mayúsculas, tildes, signos y espaciado.
 * ══════════════════════════════════════════════════════════════════════
 */
object UiTextNormalizer {

    private val diacriticsRegex = Regex("\\p{M}+")
    private val nonAlphanumericRegex = Regex("[^a-z0-9 ]")
    private val multiWhitespaceRegex = Regex("\\s+")

    fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(diacriticsRegex, "")
            .lowercase()
            .replace(nonAlphanumericRegex, " ")
            .replace(multiWhitespaceRegex, " ")
            .trim()
    }
}
