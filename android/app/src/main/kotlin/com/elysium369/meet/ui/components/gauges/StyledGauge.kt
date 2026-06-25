package com.elysium369.meet.ui.components.gauges

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.elysium369.meet.ui.components.GaugeWidget
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

/**
 * Router composable that dispatches gauge rendering to the correct style widget.
 * All styles are wrapped in Gauge3DWrapper for premium holographic depth effects.
 * Each style has a unique accent glow color that matches its design language.
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
    customColorScheme: GaugeColorScheme? = null,
    customLabelColor: Color? = null,
    customUnitColor: Color? = null,
    diyConfig: com.elysium369.meet.data.local.entities.SavedGaugeEntity? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    val trigger = GaugeStyleManager.colorSchemeUpdateTrigger
    val colorScheme = remember(style, trigger, customColorScheme, customLabelColor, customUnitColor, diyConfig) {
        val baseScheme = if (style == GaugeStyleSet.CUSTOM_DIY && diyConfig != null) {
            val primaryColor = Color(diyConfig.accentColor)
            GaugeColorScheme(
                bezelColor = Color(0xFF3E3E3E),
                internalColor = primaryColor,
                textColor = primaryColor,
                needleColor = Color(diyConfig.accentColor),
                specialColor = Color(diyConfig.accentColor2),
                labelColor = primaryColor,
                unitColor = primaryColor
            )
        } else {
            customColorScheme ?: gaugeStyleManager.getColorScheme(style)
        }
        baseScheme.copy(
            labelColor = customLabelColor ?: baseScheme.labelColor,
            unitColor = customUnitColor ?: baseScheme.unitColor
        )
    }

    CompositionLocalProvider(LocalGaugeColorScheme provides colorScheme) {
        Gauge3DWrapper(
            modifier = modifier,
            glowColor = colorScheme.specialColor,
            style = style
        ) {
            val contentBlock = @Composable {
                when (style) {
            GaugeStyleSet.CUSTOM_DIY -> GaugeDiyWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                diyConfig = diyConfig,
                modifier = Modifier
            )
            GaugeStyleSet.ELITE -> GaugeWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                customLabelColor = customLabelColor, customUnitColor = customUnitColor,
                modifier = Modifier
            )
            GaugeStyleSet.CLASSIC -> GaugeClassicWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                customLabelColor = customLabelColor, customUnitColor = customUnitColor,
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
            GaugeStyleSet.LIGHTNING -> GaugeLightningWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.RAIN -> GaugeRainWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.SNOW -> GaugeSnowWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.TORNADO -> GaugeTornadoWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.SANDSTORM -> GaugeSandstormWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.VOLCANO -> GaugeVolcanoWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.TSUNAMI -> GaugeTsunamiWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.BLIZZARD -> GaugeBlizzardWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.AURORA_AUTO -> GaugeAuroraAutoWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.SOLAR_FLARE -> GaugeSolarFlareWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.COSMIC_DUST -> GaugeCosmicDustWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.EARTHQUAKE -> GaugeEarthquakeWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.METEOR_SHOWER -> GaugeMeteorShowerWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.HURRICANE -> GaugeHurricaneWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.FOGGY_MIST -> GaugeFoggyMistWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.WILD_FIRE -> GaugeWildFireWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.OCEAN_DEPTH -> GaugeOceanDepthWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.ECLIPSE -> GaugeEclipseWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.RAINBOW_RAIN -> GaugeRainbowRainWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.SAND_GLOW -> GaugeSandGlowWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.THUNDER_CLOUD -> GaugeThunderCloudWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.ICY_FROST -> GaugeIcyFrostWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.CYBER_STORM -> GaugeCyberStormWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.BLACK_HOLE -> GaugeBlackHoleWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.MONSOON -> GaugeMonsoonWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.COMET_TAIL -> GaugeCometTailWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.GALAXY_CORE -> GaugeGalaxyCoreWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.ACID_RAIN -> GaugeAcidRainWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.SUPERNOVA -> GaugeSupernovaWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            GaugeStyleSet.WIND_TUNNEL -> GaugeWindTunnelWidget(
                label = label, value = value, minVal = minVal, maxVal = maxVal,
                unit = unit, warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold, isAnomaly = isAnomaly,
                modifier = Modifier
            )
            }
        }

        val isPremiumStyle = style.ordinal >= GaugeStyleSet.LIGHTNING.ordinal
        if (isPremiumStyle) {
            PremiumGaugeOverlayWrapper(
                label = label,
                value = value,
                minVal = minVal,
                maxVal = maxVal,
                unit = unit,
                warningThreshold = warningThreshold,
                criticalThreshold = criticalThreshold,
                isAnomaly = isAnomaly,
                colorScheme = colorScheme,
                customLabelColor = customLabelColor,
                customUnitColor = customUnitColor,
                modifier = Modifier,
                content = contentBlock
            )
        } else {
            contentBlock()
        }
    }
}
}

private data class PremiumTickInfo(
    val start: Offset,
    val end: Offset,
    val isMajor: Boolean,
    val i: Int,
    val labelTextResult: Pair<androidx.compose.ui.text.TextLayoutResult, Offset>?
)

@Composable
private fun PremiumGaugeOverlayWrapper(
    label: String,
    value: Float,
    minVal: Float,
    maxVal: Float,
    unit: String,
    warningThreshold: Float?,
    criticalThreshold: Float?,
    isAnomaly: Boolean,
    colorScheme: GaugeColorScheme,
    customLabelColor: Color? = null,
    customUnitColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f),
        label = "premiumOverlayAnimation"
    )
    val hasData = value != 0f || label.contains("Temp", true)

    val infiniteTransition = rememberInfiniteTransition(label = "premiumWarningPulse")
    val warningPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warningPulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        // 1. Render the base custom premium gauge
        content()

        // 2. Render the overlay canvas for ticks, numbers, labels, units, and secondary speedometer
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f - 18.dp.toPx()

                    // Geometry used by premium widgets: start = 140f, sweep = 260f
                    val startAngle = 140f
                    val sweepAngle = 260f

                    // Pre-measure texts
                    val minText = if (minVal == minVal.toInt().toFloat()) "${minVal.toInt()}" else String.format("%.0f", minVal)
                    val maxText = if (maxVal >= 1000) "${(maxVal / 1000).toInt()}k" 
                                  else if (maxVal == maxVal.toInt().toFloat()) "${maxVal.toInt()}" 
                                  else String.format("%.0f", maxVal)

                    val minMeasured = textMeasurer.measure(minText, TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
                    val maxMeasured = textMeasurer.measure(maxText, TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
                    
                    val labelMeasured = textMeasurer.measure(
                        label.uppercase(),
                        TextStyle(
                            color = customLabelColor ?: colorScheme.labelColor.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                    )

                    // Placement positions
                    val labelTop = center.y + radius * 0.55f
                    val unitTop = center.y + radius * 0.22f

                    val unitMeasured = textMeasurer.measure(
                        unit.lowercase(),
                        TextStyle(
                            color = customUnitColor ?: colorScheme.unitColor.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // Warning and critical zone fractions
                    val warnFraction = if (warningThreshold != null && maxVal > minVal) 
                        ((warningThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.75f
                    val critFraction = if (criticalThreshold != null && maxVal > minVal) 
                        ((criticalThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.90f

                    // Ticks and numeric scale calculation
                    val tickCount = 30
                    val majorInterval = 5
                    val ticks = List(tickCount + 1) { i ->
                        val angle = startAngle + (i.toFloat() / tickCount) * sweepAngle
                        val angleRad = Math.toRadians(angle.toDouble())
                        val isMajor = i % majorInterval == 0
                        val tickLength = if (isMajor) 10.dp.toPx() else 5.dp.toPx()

                        // Draw ticks outside the arc (r = radius + 2.dp)
                        val innerR = radius + 2.dp.toPx()
                        val outerR = innerR + tickLength

                        val start = Offset(
                            (center.x + innerR * cos(angleRad)).toFloat(),
                            (center.y + innerR * sin(angleRad)).toFloat()
                        )
                        val end = Offset(
                            (center.x + outerR * cos(angleRad)).toFloat(),
                            (center.y + outerR * sin(angleRad)).toFloat()
                        )

                        val labelResult = if (isMajor) {
                            val labelVal = minVal + (i.toFloat() / tickCount) * (maxVal - minVal)
                            val text = if (labelVal >= 1000) "${(labelVal / 1000).toInt()}k"
                                       else if (labelVal == labelVal.toInt().toFloat()) "${labelVal.toInt()}"
                                       else String.format("%.0f", labelVal)
                            
                            val labelR = outerR + 8.dp.toPx()
                            val labelOffset = Offset(
                                (center.x + labelR * cos(angleRad)).toFloat(),
                                (center.y + labelR * sin(angleRad)).toFloat()
                            )
                            val measured = textMeasurer.measure(
                                text = text,
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Pair(measured, labelOffset)
                        } else null

                        PremiumTickInfo(start = start, end = end, isMajor = isMajor, i = i, labelTextResult = labelResult)
                    }

                    // Speedometer dual scale calculation
                    val isSpeedGauge = unit.equals("km/h", ignoreCase = true) || unit.equals("mph", ignoreCase = true)
                    val isKmH = unit.equals("km/h", ignoreCase = true)
                    val secondaryUnit = if (isKmH) "mph" else "km/h"
                    val speedConversion = if (isKmH) 0.621371f else (1f / 0.621371f)
                    val secondaryMax = maxVal * speedConversion

                    val secondaryStep = if (secondaryMax > 200f) 40f else if (secondaryMax > 120f) 20f else 10f
                    val innerTicks = if (isSpeedGauge) {
                        val list = mutableListOf<PremiumTickInfo>()
                        var vSec = 0f
                        while (vSec <= secondaryMax) {
                            val angleFraction = vSec / secondaryMax
                            val angle = startAngle + angleFraction * sweepAngle
                            val angleRad = Math.toRadians(angle.toDouble())

                            // Draw inside the arc
                            val innerR = radius - 12.dp.toPx()
                            val tickLength = 4.dp.toPx()

                            val start = Offset(
                                (center.x + innerR * cos(angleRad)).toFloat(),
                                (center.y + innerR * sin(angleRad)).toFloat()
                            )
                            val end = Offset(
                                (center.x + (innerR - tickLength) * cos(angleRad)).toFloat(),
                                (center.y + (innerR - tickLength) * sin(angleRad)).toFloat()
                            )

                            val labelR = innerR - tickLength - 6.dp.toPx()
                            val labelOffset = Offset(
                                (center.x + labelR * cos(angleRad)).toFloat(),
                                (center.y + labelR * sin(angleRad)).toFloat()
                            )

                            val measured = textMeasurer.measure(
                                text = String.format("%.0f", vSec),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            list.add(PremiumTickInfo(start = start, end = end, isMajor = true, i = vSec.toInt(), labelTextResult = Pair(measured, labelOffset)))
                            vSec += secondaryStep
                        }
                        list
                    } else emptyList()

                    val minAngleRad = Math.toRadians((startAngle - 12).toDouble())
                    val maxAngleRad = Math.toRadians((startAngle + sweepAngle + 12).toDouble())
                    val labelEndR = radius + 6.dp.toPx()
                    val minLabelPos = Offset(
                        (center.x + labelEndR * cos(minAngleRad)).toFloat() - minMeasured.size.width / 2f,
                        (center.y + labelEndR * sin(minAngleRad)).toFloat() - minMeasured.size.height / 2f
                    )
                    val maxLabelPos = Offset(
                        (center.x + labelEndR * cos(maxAngleRad)).toFloat() - maxMeasured.size.width / 2f,
                        (center.y + labelEndR * sin(maxAngleRad)).toFloat() - maxMeasured.size.height / 2f
                    )

                    onDrawBehind {
                        val animVal = animatedValue
                        val activeColor = when {
                            !hasData -> MeetColors.textMuted
                            isAnomaly -> MeetColors.error
                            criticalThreshold != null && animVal >= criticalThreshold -> MeetColors.error
                            warningThreshold != null && animVal >= warningThreshold -> MeetColors.warning
                            else -> colorScheme.internalColor
                        }

                        // ── 0. COCKPIT REFERENCE RINGS ──
                        drawCircle(
                            color = Color.White.copy(alpha = 0.02f),
                            radius = radius * 0.4f,
                            center = center,
                            style = Stroke(1.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.02f),
                            radius = radius * 0.7f,
                            center = center,
                            style = Stroke(1.dp.toPx())
                        )

                        // ── 1. WARNING/CRITICAL ZONE BOUNDARY ARCS ──
                        val zoneR = radius + 2.dp.toPx()
                        val warnStart = startAngle + warnFraction * sweepAngle
                        val warnSweep = (critFraction - warnFraction) * sweepAngle
                        val critStart = startAngle + critFraction * sweepAngle
                        val critSweep = (1f - critFraction) * sweepAngle

                        if (warningThreshold != null) {
                            drawArc(
                                color = Color(0xFFFFB300).copy(alpha = 0.25f), // Transparent Amber
                                startAngle = warnStart,
                                sweepAngle = warnSweep,
                                useCenter = false,
                                style = Stroke(width = 1.5f.dp.toPx()),
                                topLeft = Offset(center.x - zoneR, center.y - zoneR),
                                size = Size(zoneR * 2f, zoneR * 2f)
                            )
                        }
                        if (criticalThreshold != null) {
                            drawArc(
                                color = Color(0xFFFF1744).copy(alpha = 0.35f), // Transparent Red
                                startAngle = critStart,
                                sweepAngle = critSweep,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx()),
                                topLeft = Offset(center.x - zoneR, center.y - zoneR),
                                size = Size(zoneR * 2f, zoneR * 2f)
                            )
                        }

                        // ── 2. HIGH-INTENSITY ALARM PULSE AURA ──
                        if (hasData) {
                            val isWarningBreached = warningThreshold != null && animVal >= warningThreshold
                            val isCriticalBreached = criticalThreshold != null && animVal >= criticalThreshold
                            if (isAnomaly || isWarningBreached || isCriticalBreached) {
                                val alertColor = if (isCriticalBreached || isAnomaly) Color(0xFFFF1744) else Color(0xFFFFB300)
                                drawCircle(
                                    color = alertColor.copy(alpha = 0.08f * warningPulseAlpha),
                                    radius = radius + 20.dp.toPx(),
                                    center = center,
                                    style = Stroke(6.dp.toPx())
                                )
                            }
                        }

                        // ── 3. DRAW TICK MARKS ──
                        val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                        ticks.forEach { tick ->
                            val tickFraction = tick.i.toFloat() / tickCount
                            val tickColor = when {
                                !hasData -> Color.White.copy(alpha = 0.1f)
                                tickFraction <= prog -> activeColor
                                tickFraction >= critFraction -> Color(0xFFFF1744).copy(alpha = 0.3f)
                                tickFraction >= warnFraction -> Color(0xFFFFB300).copy(alpha = 0.25f)
                                else -> Color.White.copy(alpha = 0.15f)
                            }

                            drawLine(
                                color = tickColor.copy(alpha = if (tick.isMajor) 0.8f else 0.3f),
                                start = tick.start,
                                end = tick.end,
                                strokeWidth = if (tick.isMajor) 1.5f.dp.toPx() else 1.dp.toPx()
                            )

                            tick.labelTextResult?.let { (measured, pos) ->
                                drawText(measured, topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f))
                            }
                        }

                        // ── 4. DRAW DUAL SPEEDOMETER INNER SCALE ──
                        if (isSpeedGauge) {
                            val innerR = radius - 12.dp.toPx()
                            drawArc(
                                color = Color.White.copy(alpha = 0.08f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = 1.dp.toPx()),
                                topLeft = Offset(center.x - innerR, center.y - innerR),
                                size = Size(innerR * 2f, innerR * 2f)
                            )

                            innerTicks.forEach { tick ->
                                drawLine(
                                    color = Color.White.copy(alpha = 0.2f),
                                    start = tick.start,
                                    end = tick.end,
                                    strokeWidth = 1.dp.toPx()
                                )
                                tick.labelTextResult?.let { (measured, pos) ->
                                    drawText(measured, topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f))
                                }
                            }
                        }

                        // ── 5. DRAW NAME LABEL ──
                        drawText(
                            textLayoutResult = labelMeasured,
                            topLeft = Offset(center.x - labelMeasured.size.width / 2f, labelTop)
                        )

                        // ── 6. DRAW UNIT LABEL ──
                        drawText(
                            textLayoutResult = unitMeasured,
                            topLeft = Offset(center.x - unitMeasured.size.width / 2f, unitTop)
                        )

                        if (isSpeedGauge && hasData) {
                            val secValText = String.format("%.0f %s", animVal * speedConversion, secondaryUnit)
                            val secMeasured = textMeasurer.measure(
                                text = secValText,
                                style = TextStyle(
                                    color = MeetColors.textSecondary.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            drawText(secMeasured, topLeft = Offset(center.x - secMeasured.size.width / 2f, unitTop + unitMeasured.size.height + 2.dp.toPx()))
                        }

                        // ── 7. DRAW ENDPOINT LABELS ──
                        drawText(minMeasured, topLeft = minLabelPos)
                        drawText(maxMeasured, topLeft = maxLabelPos)

                        // ── 8. DRAW HUD ALARM / WARNING TEXT ──
                        if (hasData) {
                            val isWarningBreached = warningThreshold != null && animVal >= warningThreshold
                            val isCriticalBreached = criticalThreshold != null && animVal >= criticalThreshold
                            val warningText = when {
                                isAnomaly -> "⚠ FALLA"
                                isCriticalBreached -> "⚠ CRÍTICO"
                                isWarningBreached -> "⚠ ALERTA"
                                else -> null
                            }
                            if (warningText != null) {
                                val alertColor = if (isCriticalBreached || isAnomaly) Color(0xFFFF1744) else Color(0xFFFFB300)
                                val bracketedText = "[ $warningText ]"
                                val warningMeasured = textMeasurer.measure(
                                    text = bracketedText,
                                    style = TextStyle(
                                        color = alertColor.copy(alpha = warningPulseAlpha),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                drawText(warningMeasured, topLeft = Offset(center.x - warningMeasured.size.width / 2f, center.y - radius * 0.45f))
                            }
                        }
                    }
                }
        )
    }
}
