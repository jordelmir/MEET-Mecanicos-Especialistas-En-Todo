package com.elysium369.meet.ride.map

import java.util.Locale

object RideManeuverFormatter {

    fun formatManeuverSymbol(type: String, modifier: String?): String {
        val normalizedType = type.trim().lowercase(Locale.ROOT)
        val normalizedModifier = modifier?.trim()?.lowercase(Locale.ROOT)

        return when (normalizedType) {
            "depart" -> "▲"
            "arrive" -> "🏁"
            "roundabout", "rotary" -> "↺"
            "merge" -> "⮀"
            "fork" -> "⌥"
            "on ramp", "off ramp", "ramp" -> "⤤"
            "end of road" -> when (normalizedModifier) {
                "left" -> "↰"
                "right" -> "↱"
                else -> "↑"
            }
            "turn", "continue", "new name" -> when (normalizedModifier) {
                "sharp left" -> "⮢"
                "left" -> "↰"
                "slight left" -> "↖"
                "sharp right" -> "⮣"
                "right" -> "↱"
                "slight right" -> "↗"
                "straight" -> "↑"
                "uturn" -> "↷"
                else -> "↑"
            }
            else -> when (normalizedModifier) {
                "left" -> "↰"
                "right" -> "↱"
                "uturn" -> "↷"
                else -> "↑"
            }
        }
    }

    fun formatInstruction(type: String, modifier: String?, streetName: String?): String {
        val normalizedType = type.trim().lowercase(Locale.ROOT)
        val normalizedModifier = modifier?.trim()?.lowercase(Locale.ROOT)
        val street = streetName?.takeIf { it.isNotBlank() }

        val action = when (normalizedType) {
            "depart" -> "Inicia la ruta"
            "arrive" -> "Llegada al punto"
            "roundabout", "rotary" -> "En la rotonda toma la salida"
            "merge" -> "Incorpórate a la vía"
            "fork" -> when (normalizedModifier) {
                "left", "slight left" -> "Toma la bifurcación izquierda"
                "right", "slight right" -> "Toma la bifurcación derecha"
                else -> "Toma la bifurcación"
            }
            "on ramp", "ramp" -> "Toma la rampa de acceso"
            "off ramp" -> "Toma la rampa de salida"
            "end of road" -> when (normalizedModifier) {
                "left" -> "Al final de la calle gira a la izquierda"
                "right" -> "Al final de la calle gira a la derecha"
                else -> "Fin de la calle"
            }
            else -> when (normalizedModifier) {
                "sharp left" -> "Gira pronunciado a la izquierda"
                "left" -> "Gira a la izquierda"
                "slight left" -> "Mantente a la izquierda"
                "sharp right" -> "Gira pronunciado a la derecha"
                "right" -> "Gira a la derecha"
                "slight right" -> "Mantente a la derecha"
                "straight" -> "Continúa recto"
                "uturn" -> "Haz un cambio de sentido"
                else -> "Continúa por la ruta"
            }
        }

        return if (street != null) {
            "$action hacia $street"
        } else {
            action
        }
    }

    fun formatDistance(distanceMeters: Long): String {
        if (distanceMeters < 0) return "0 m"
        return if (distanceMeters < 1000) {
            "$distanceMeters m"
        } else {
            String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
        }
    }

    fun formatDuration(durationSeconds: Long): String {
        if (durationSeconds < 60) return "< 1 min"
        val totalMinutes = (durationSeconds + 59) / 60
        return if (totalMinutes < 60) {
            "$totalMinutes min"
        } else {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            if (minutes > 0) "$hours h $minutes min" else "$hours h"
        }
    }
}
