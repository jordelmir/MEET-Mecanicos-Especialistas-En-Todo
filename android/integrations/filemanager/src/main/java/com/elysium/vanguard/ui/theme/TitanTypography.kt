package com.elysium.vanguard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * TITAN TYPOGRAPHY
 * Implements Orbitron/Rajdhani aesthetics for a sharp, futuristic look.
 * Note: Actual .ttf files should be placed in res/font.
 */
val TitanTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, // Fallback to Orbitron in prod
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        letterSpacing = 2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, // Fallback to Rajdhani in prod
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.sp
    )
)
