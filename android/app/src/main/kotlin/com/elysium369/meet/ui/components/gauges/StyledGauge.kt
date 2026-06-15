package com.elysium369.meet.ui.components.gauges

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.components.GaugeWidget

/**
 * Router composable that dispatches gauge rendering to the correct style widget.
 * All styles are wrapped in Gauge3DWrapper for premium holographic depth effects.
 * Each style has a unique accent glow color that matches its design language.
 *
 * To add a new style:
 *   1. Add the enum value in GaugeStyleManager
 *   2. Add the accent color entry below
 *   3. Add the when branch for the gauge widget
 */
@Composable
fun StyledGauge(
    style: GaugeStyleSet,
    label: String,
    value: Float,
    minVal: Float = 0f,
    maxVal: Float = 100f,
    unit: String,
    warningThreshold: Float? = null,
    criticalThreshold: Float? = null,
    isAnomaly: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Per-style accent glow color for the 3D wrapper
    val accentColor = when (style) {
        GaugeStyleSet.ELITE      -> Color(0xFF00E5FF) // Cyan
        GaugeStyleSet.CLASSIC    -> Color(0xFF4CAF50) // Green
        GaugeStyleSet.CYBER      -> Color(0xFF00FFFF) // Electric cyan
        GaugeStyleSet.RACING     -> Color(0xFFFF1744) // Racing red
        GaugeStyleSet.RADIAL     -> Color(0xFF448AFF) // Blue
        GaugeStyleSet.THERMO     -> Color(0xFFFF6D00) // Orange
        GaugeStyleSet.HOLOGRAM   -> Color(0xFF00E5FF) // Holo cyan
        GaugeStyleSet.NEON_RETRO -> Color(0xFFFF00FF) // Magenta
        GaugeStyleSet.LAMBO      -> Color(0xFFFF9100) // Lambo orange
        GaugeStyleSet.PLASMA     -> Color(0xFF7C4DFF) // Violet
        GaugeStyleSet.AURORA     -> Color(0xFF1DE9B6) // Teal
        GaugeStyleSet.FERRARI    -> Color(0xFFD50000) // Rosso Corsa
        GaugeStyleSet.TOKYO      -> Color(0xFFFF4081) // Hot pink
        GaugeStyleSet.MILITARY   -> Color(0xFF76FF03) // Phosphor green
        GaugeStyleSet.DIAMOND    -> Color(0xFF80D8FF) // Ice blue
        GaugeStyleSet.COCKPIT    -> Color(0xFFFFAB00) // Amber
    }

    Gauge3DWrapper(
        modifier = modifier,
        glowColor = accentColor,
        style = style
    ) {
        when (style) {
            GaugeStyleSet.ELITE -> GaugeWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.CLASSIC -> GaugeClassicWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.CYBER -> GaugeCyberWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.RACING -> GaugeRacingWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.RADIAL -> GaugeRadialWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.THERMO -> GaugeThermoWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.HOLOGRAM -> GaugeHologramWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.NEON_RETRO -> GaugeNeonRetroWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.LAMBO -> GaugeLamboWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.PLASMA -> GaugePlasmaWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.AURORA -> GaugeAuroraWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.FERRARI -> GaugeFerrariWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.TOKYO -> GaugeTokyoWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.MILITARY -> GaugeMilitaryWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.DIAMOND -> GaugeDiamondWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.COCKPIT -> GaugeCockpitWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
        }
    }
}
