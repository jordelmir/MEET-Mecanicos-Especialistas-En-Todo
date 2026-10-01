package com.elysium.vanguard.recordshield.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ============================================================================
 * Neo-Futuristic Color System — Elysium Vanguard Record Shield
 * ============================================================================
 *
 * Design Philosophy:
 *   - Deep Black/Dark Grey base for immersive, high-contrast UI
 *   - Neon accents for action elements (recording button, active states)
 *   - Glassmorphism: semi-transparent surfaces with backdrop blur
 *   - Matrix-inspired green for primary brand identity
 *   - Electric blue and deep purple for secondary/tertiary accents
 * ============================================================================
 */

// ============================================================================
// BASE — Hyper-Deep Space Background
// ============================================================================
val DeepBlack = Color(0xFF050508)
val DarkSurface = Color(0xFF0A0A0F)
val CardSurface = Color(0xFF101018)
val ElevatedSurface = Color(0xFF161622)
val SubtleBorder = Color(0xFF1C1C2B)

// ============================================================================
// NEON ACCENTS — Matrix / Cyberpunk Palette
// ============================================================================
val MatrixGreen = Color(0xFF00FF41)          // Primary — Matrix Phosphorus
val MatrixGreenDim = Color(0xFF003B00)       // Background variant
val MatrixGreenGlow = Color(0x9900FF41)      // High-intensity glow
val MatrixGreenSubtle = Color(0x3300FF41)    // Medium wash

val ElectricBlue = Color(0xFF00F2FF)         // Secondary — Cyber Neon Blue
val ElectricBlueDim = Color(0xFF001F26)      // Background variant
val ElectricBlueGlow = Color(0x9900F2FF)     // High-intensity glow

val DeepPurple = Color(0xFFD300FF)           // Tertiary — Hyper Magenta
val DeepPurpleDim = Color(0xFF1F0026)        // Background variant
val DeepPurpleGlow = Color(0x99D300FF)       // High-intensity glow

// ============================================================================
// STATUS COLORS — Overdrive Edition
// ============================================================================
val RecordingRed = Color(0xFFFF003C)         // High-vis recording red
val RecordingRedGlow = Color(0xCCFF003C)     // Pulsing red glow
val WarningAmber = Color(0xFFFFD700)         // Gold warning
val SuccessGreen = Color(0xFF00FF9D)         // Mint success

// ============================================================================
// TEXT
// ============================================================================
val TextPrimary = Color(0xFFFFFFFF)          // Pure white
val TextSecondary = Color(0xFFB0B0C0)        // Technical grey
val TextTertiary = Color(0xFF505060)         // Muted dark

// ============================================================================
// GLASSMORPHISM — Extreme Clarity
// ============================================================================
val GlassSurface = Color(0x26FFFFFF)         // 15% white overlay
val GlassBorder = Color(0x4DFFFFFF)          // 30% white border
val GlassHighlight = Color(0x1AFFFFFF)       // 10% white inner highlight
