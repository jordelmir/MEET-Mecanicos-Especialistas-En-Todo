package com.elysium.vanguard.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * PROJECT ELYSIUM: PHOSPHORESCENT NEON PALETTE
 * Ultra-vivid colors engineered for OLED maximum impact.
 */
object TitanColors {
    // ── VOID BASE ──
    // ── VOID BASE ──
    val AbsoluteBlack = Color(0xFF000000)
    val AbsoluteWhite = Color(0xFFFFFFFF)
    val CarbonGray = Color(0xFF080808)
    val DeepVoid = Color(0xFF020205)

    // ── NEON CORE (MASCULINE) ──
    val NeonCyan = Color(0xFF00FFFF)
    val NeonRed = Color(0xFFFF073A) // Sci-Fi Red
    val RadioactiveGreen = Color(0xFF39FF14)
    val ElectricBlue = Color(0xFF007FFF)

    // ── PHOSPHORESCENT EXPANSION ──
    val NeonOrange = Color(0xFFFF3F00) // Aggressive Orange
    val NeonYellow = Color(0xFFD4FF00) // Acid Yellow/Lime
    val UltraViolet = Color(0xFF6F00FF) // Indigo
    val ElectricPurple = Color(0xFFBF00FF) // Vivid Purple
    val HolographicBlue = Color(0xFF0044FF)

    // Remapped for Masculine Theme (Pink -> Purple/Violet)
    val PlasmaPurple = Color(0xFF9400D3)
    val QuantumPink = Color(0xFFB026FF) // Neon Purple
    val AcidLime = Color(0xFFCCFF00)

    val NeonGreen = RadioactiveGreen // Alias

    val GhostWhite = Color(0xFFE0E0E0)

    // ── ALL COLORS (FOR MULTICOLOR MODE) ──
    val All = listOf(
        NeonCyan, RadioactiveGreen, QuantumPink, NeonOrange, NeonYellow, ElectricBlue, PlasmaPurple, NeonRed
    )

    // ── GRADIENT FACTORIES ──
    val CyanToViolet = Brush.linearGradient(listOf(NeonCyan, UltraViolet))
    val PinkToOrange = Brush.linearGradient(listOf(QuantumPink, NeonOrange)) // Now Purple to Orange
    val GreenToYellow = Brush.linearGradient(listOf(RadioactiveGreen, NeonYellow))
    val RedToViolet = Brush.linearGradient(listOf(NeonRed, PlasmaPurple))
    val DeepVoidGradient = Brush.verticalGradient(listOf(CarbonGray, AbsoluteBlack, DeepVoid))

    fun phosphorescentBrush(primary: Color, secondary: Color = primary.copy(alpha = 0.3f)): Brush {
        return Brush.radialGradient(
            listOf(primary, secondary, Color.Transparent)
        )
    }

    fun neonSweepBrush(colors: List<Color>): Brush {
        return Brush.sweepGradient(colors)
    }
}
