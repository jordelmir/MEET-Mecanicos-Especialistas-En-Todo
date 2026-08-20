package com.elysium369.meet.vehiclelife.battery

import com.elysium369.meet.core.domain.ClaimNature
import com.elysium369.meet.core.domain.ExplainableClaim
import com.elysium369.meet.core.domain.SourceAuthority
import com.elysium369.meet.core.domain.EntityRef

enum class BatteryPowertrainStrategy(val displayName: String) {
    ICE_12V_STANDARD("Batería de Plomo-Ácido 12V (Combustión Convencional)"),
    ICE_12V_AGM_EFB("Batería 12V AGM / EFB (Start-Stop Avanzado)"),
    HEV_HYBRID_HIGH_VOLTAGE("Batería de Alta Tensión NiMH / Li-Ion (Híbrido)"),
    BEV_FULL_ELECTRIC("Batería de Tracción HV Li-Ion (Eléctrico Puro)")
}

enum class PowerCondition(val label: String, val glyph: String) {
    EXCELLENT("Óptimo / Carga y Alternador Normal", "🟢"),
    WEAK_CHARGE("Voltaje de Carga Bajo / Revisar Alternador", "🟡"),
    DISCHARGED("Batería Descargada / Requiere Carga", "🟠"),
    CRITICAL_FAILURE("Falla Crítica de Acumulador o Sistema de Carga", "🔴"),
    UNMEASURED("Sin Medición de Voltaje Disponible", "⚪")
}

data class BatteryAssessment(
    val strategy: BatteryPowertrainStrategy,
    val condition: PowerCondition,
    val measuredRestingVoltage: Float?,
    val measuredChargingVoltage: Float?,
    val crankingVoltageDrop: Float?,
    val isChargingNormal: Boolean?,
    val recommendation: String,
    override val evidenceRefs: List<EntityRef.EvidenceRef> = emptyList()
) : ExplainableClaim {
    override val claimId: String get() = "CLAIM_BATTERY_${strategy.name}"
    override val claimTitle: String get() = "Estado del Sistema de Energía"
    override val claimStatement: String get() = "${condition.glyph} ${condition.label}"
    override val nature: ClaimNature get() = if (measuredRestingVoltage != null) ClaimNature.OBSERVED else ClaimNature.DERIVED
    override val authority: SourceAuthority get() = SourceAuthority.VEHICLE_ECU
    override val confidencePercent: Int? get() = if (measuredRestingVoltage != null) 95 else null
    override val derivationSummary: String get() = "Reposo: ${measuredRestingVoltage?.let { "%.2fV".format(it) } ?: "N/D"}, Carga: ${measuredChargingVoltage?.let { "%.2fV".format(it) } ?: "N/D"}"
    override val timestampUtc: Long = System.currentTimeMillis()
}

object BatteryEnergyManager {

    fun evaluate12vIceBattery(
        voltageFloat: Float?,
        isEngineRunning: Boolean
    ): BatteryAssessment {
        if (voltageFloat == null || voltageFloat <= 0f) {
            return BatteryAssessment(
                strategy = BatteryPowertrainStrategy.ICE_12V_STANDARD,
                condition = PowerCondition.UNMEASURED,
                measuredRestingVoltage = null,
                measuredChargingVoltage = null,
                crankingVoltageDrop = null,
                isChargingNormal = null,
                recommendation = "Conecte el escáner OBD2 con switch en ON para registrar telemetría de voltaje."
            )
        }

        return if (isEngineRunning) {
            when {
                voltageFloat >= 13.8f && voltageFloat <= 14.8f -> BatteryAssessment(
                    strategy = BatteryPowertrainStrategy.ICE_12V_STANDARD,
                    condition = PowerCondition.EXCELLENT,
                    measuredRestingVoltage = null,
                    measuredChargingVoltage = voltageFloat,
                    crankingVoltageDrop = null,
                    isChargingNormal = true,
                    recommendation = "Alternador y regulador de voltaje operando dentro del rango óptimo (13.8V - 14.8V)."
                )
                voltageFloat < 13.2f -> BatteryAssessment(
                    strategy = BatteryPowertrainStrategy.ICE_12V_STANDARD,
                    condition = PowerCondition.WEAK_CHARGE,
                    measuredRestingVoltage = null,
                    measuredChargingVoltage = voltageFloat,
                    crankingVoltageDrop = null,
                    isChargingNormal = false,
                    recommendation = "Voltaje con motor en marcha insuficiente (<13.2V). Verifique tensión de banda de accesorios y alternador."
                )
                else -> BatteryAssessment(
                    strategy = BatteryPowertrainStrategy.ICE_12V_STANDARD,
                    condition = PowerCondition.CRITICAL_FAILURE,
                    measuredRestingVoltage = null,
                    measuredChargingVoltage = voltageFloat,
                    crankingVoltageDrop = null,
                    isChargingNormal = false,
                    recommendation = "Sobrevoltaje detectado (>14.8V). Riesgo de sobrecarga de batería por falla en regulador."
                )
            }
        } else {
            when {
                voltageFloat >= 12.5f -> BatteryAssessment(
                    strategy = BatteryPowertrainStrategy.ICE_12V_STANDARD,
                    condition = PowerCondition.EXCELLENT,
                    measuredRestingVoltage = voltageFloat,
                    measuredChargingVoltage = null,
                    crankingVoltageDrop = null,
                    isChargingNormal = null,
                    recommendation = "Batería en estado óptimo de reposo (100% - 85% de carga nominal)."
                )
                voltageFloat in 12.0f..12.49f -> BatteryAssessment(
                    strategy = BatteryPowertrainStrategy.ICE_12V_STANDARD,
                    condition = PowerCondition.DISCHARGED,
                    measuredRestingVoltage = voltageFloat,
                    measuredChargingVoltage = null,
                    crankingVoltageDrop = null,
                    isChargingNormal = null,
                    recommendation = "Batería parcialmente descargada (50% - 25%). Recomendado realizar recarga o ciclo de manejo prolongado."
                )
                else -> BatteryAssessment(
                    strategy = BatteryPowertrainStrategy.ICE_12V_STANDARD,
                    condition = PowerCondition.CRITICAL_FAILURE,
                    measuredRestingVoltage = voltageFloat,
                    measuredChargingVoltage = null,
                    crankingVoltageDrop = null,
                    isChargingNormal = null,
                    recommendation = "Voltaje en reposo crítico (<12.0V). Riesgo inminente de no-arranque. Requiere prueba de celda."
                )
            }
        }
    }
}
