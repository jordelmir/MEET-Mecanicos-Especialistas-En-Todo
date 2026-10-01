package com.elysium369.meet.audio.supreme

import kotlin.math.log10
import kotlin.math.roundToInt

/** Percentage describes requested amplitude, never a measured output level. */
object SupremeBoostPolicy {
    val presets = listOf(100, 125, 150, 175, 200, 225, 250, 275, 300, 400)
    fun clamp(percent: Int) = percent.coerceIn(100, 400)
    fun millibels(percent: Int): Int {
        val p = clamp(percent)
        if (p <= 100) return 0
        val delta = (p - 100).toDouble()
        val linear = delta * 22.0
        val curve = (delta / 300.0) * (delta / 300.0) * 1000.0
        return (linear + curve).roundToInt().coerceIn(0, 4500)
    }
}
