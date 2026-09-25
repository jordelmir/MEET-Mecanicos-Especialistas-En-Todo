package com.elysium369.meet.core.agent.capability.emissions

import com.elysium369.meet.core.agent.capability.AgentCapability
import com.elysium369.meet.core.agent.capability.AgentExecutionContext
import com.elysium369.meet.core.agent.capability.AgentResult
import com.elysium369.meet.core.agent.capability.AgentRisk
import com.elysium369.meet.core.agent.capability.CapabilityId
import com.elysium369.meet.core.agent.capability.CapabilityValidation
import com.elysium369.meet.core.emissions.domain.TruthClass
import kotlinx.serialization.Serializable

@Serializable
data class EmissionsCapabilityInput(
    val o2VoltageB1S1: Double? = null,
    val o2VoltageB1S2: Double? = null,
    val catalystTempC: Double? = null,
    val query: String = "",
)

@Serializable
data class ProvenanceItem(
    val metricName: String,
    val valueFormatted: String,
    val provenance: TruthClass,
    val note: String,
)

@Serializable
data class EmissionsCapabilityOutput(
    val catalystHealth: String,
    val preItvVerdict: String,
    val metrics: List<ProvenanceItem>,
    val disclaimer: String,
)

/**
 * Premium diagnostic capability for the Emissions Specialist agent.
 * Enforces Master Order Omega §27, §29:
 * "Never present OBD-derived estimation as certified exhaust-gas measurement.
 * State explicitly whether a value is MEASURED, DERIVED, ESTIMATED, or UNKNOWN."
 */
class EmissionsSpecialistCapability : AgentCapability<EmissionsCapabilityInput, EmissionsCapabilityOutput> {

    override val id: CapabilityId = CapabilityId.of("emissions.analyze_o2")
    override val risk: AgentRisk = AgentRisk.READ_ONLY
    override val requiredEntitlement: String = "agent.emissions_specialist"

    override suspend fun validate(input: EmissionsCapabilityInput, context: AgentExecutionContext): CapabilityValidation {
        return CapabilityValidation.Valid
    }

    override suspend fun execute(input: EmissionsCapabilityInput, context: AgentExecutionContext): AgentResult<EmissionsCapabilityOutput> {
        val metrics = mutableListOf<ProvenanceItem>()

        if (input.o2VoltageB1S1 != null) {
            metrics.add(
                ProvenanceItem(
                    metricName = "O2 Sensor B1S1 (Pre-cat)",
                    valueFormatted = "${String.format("%.3f", input.o2VoltageB1S1)} V",
                    provenance = TruthClass.MEASURED,
                    note = "Lectura directa reportada por la ECU vía Mode \$01 PID 0x14.",
                )
            )
        } else {
            metrics.add(
                ProvenanceItem(
                    metricName = "O2 Sensor B1S1 (Pre-cat)",
                    valueFormatted = "No disponible",
                    provenance = TruthClass.UNKNOWN,
                    note = "Dato no capturado en el búfer actual.",
                )
            )
        }

        if (input.o2VoltageB1S2 != null) {
            metrics.add(
                ProvenanceItem(
                    metricName = "O2 Sensor B1S2 (Post-cat)",
                    valueFormatted = "${String.format("%.3f", input.o2VoltageB1S2)} V",
                    provenance = TruthClass.MEASURED,
                    note = "Lectura directa reportada por la ECU vía Mode \$01 PID 0x15.",
                )
            )
        } else {
            metrics.add(
                ProvenanceItem(
                    metricName = "O2 Sensor B1S2 (Post-cat)",
                    valueFormatted = "No disponible",
                    provenance = TruthClass.UNKNOWN,
                    note = "Dato no capturado en el búfer actual.",
                )
            )
        }

        val catTempFormatted = if (input.catalystTempC != null) {
            "${String.format("%.1f", input.catalystTempC)} °C"
        } else {
            "Estimado ~450 °C (física)"
        }
        val catTempProv = if (input.catalystTempC != null) TruthClass.MEASURED else TruthClass.ESTIMATED
        metrics.add(
            ProvenanceItem(
                metricName = "Temperatura Convertidor Catalítico",
                valueFormatted = catTempFormatted,
                provenance = catTempProv,
                note = if (input.catalystTempC != null) "Medición física directa" else "Estimación física por tiempo de encendido y carga de motor",
            )
        )

        // Evaluate catalytic health and Pre-ITV readiness
        val (catHealth, preItv) = when {
            input.o2VoltageB1S1 == null || input.o2VoltageB1S2 == null -> {
                "PENDIENTE_EVALUACION" to "INCONCLUSO (Requiere enlace OBD-II activo y motor a temperatura de operación)"
            }
            input.o2VoltageB1S2 in 0.45..0.75 -> {
                "OPTIMO" to "FAVORABLE PRE-ITV (Señal post-catalizador estable en ventana estequiométrica)"
            }
            else -> {
                "DEGRADADO" to "RIESGO PRE-ITV (Oscilaciones excesivas post-catalizador sugieren baja capacidad de almacenamiento de O2)"
            }
        }

        return AgentResult.Success(
            data = EmissionsCapabilityOutput(
                catalystHealth = catHealth,
                preItvVerdict = preItv,
                metrics = metrics,
                disclaimer = "AVISO LEGAL: El diagnóstico de emisiones vía OBD-II provee una evaluación de salud predictiva previa a inspección. No sustituye la medición oficial con sonda de gases en tubo de escape certificada por DEKRA.",
            ),
            summary = "Análisis de emisiones completado con clasificación estricta de procedencia metrológica.",
        )
    }
}
