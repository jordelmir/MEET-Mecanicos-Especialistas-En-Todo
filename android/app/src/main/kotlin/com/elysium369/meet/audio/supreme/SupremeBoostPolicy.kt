package com.elysium369.meet.audio.supreme

import kotlin.math.log10
import kotlin.math.roundToInt

/** Percentage describes requested amplitude, never a measured output level. */
object SupremeBoostPolicy {
    val presets = listOf(100, 125, 150, 175, 200, 225, 250, 275, 300, 400)
    fun clamp(percent: Int) = percent.coerceIn(100, 400)
    fun millibels(percent: Int) = (2000 * log10(clamp(percent) / 100.0)).roundToInt()
}
