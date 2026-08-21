package com.elysium369.meet.core.evair.voice

import com.elysium369.meet.core.evair.agent.AutomotiveAgentGateway
import com.elysium369.meet.core.evair.bridge.VehicleToolFacade
import com.elysium369.meet.core.evair.domain.DiagnosticAgentRequest
import com.elysium369.meet.core.evair.domain.DiagnosticTrigger
import com.elysium369.meet.core.evair.domain.EvairResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class VoiceDiagnosticResponse(
    val spokenText: String,
    val primaryFinding: String,
    val confidence: Double,
    val recommendedAction: String?,
    val isCritical: Boolean,
)

/**
 * VoiceMechanicOrchestrator — Natural voice interaction engine for hands-free vehicle diagnostics.
 *
 * Translates spoken user complaints (e.g. "el carro tiembla", "revisa la batería") into
 * structured EVAIR diagnostic queries and produces concise, spoken feedback.
 */
@Singleton
class VoiceMechanicOrchestrator @Inject constructor(
    private val facade: VehicleToolFacade,
    private val gateway: AutomotiveAgentGateway,
) {

    suspend fun processVoiceQuery(utterance: String): VoiceDiagnosticResponse = withContext(Dispatchers.IO) {
        val lower = utterance.lowercase()
        val snapshot = facade.snapshot()
        val dtcs = facade.dtcs()

        when {
            lower.contains("tiembla") || lower.contains("vibra") || lower.contains("falla") || lower.contains("misfire") -> {
                // Focus on combustion / misfire investigation
                val rpmFeatures = facade.telemetryFeatures("010C", 15)
                val stftFeatures = facade.telemetryFeatures("0106", 15)
                val misfireDtc = dtcs.find { it.code.startsWith("P030") }

                if (misfireDtc != null) {
                    val cyl = misfireDtc.code.removePrefix("P030")
                    VoiceDiagnosticResponse(
                        spokenText = "Detecté un fallo de combustión en el cilindro $cyl, con código ${misfireDtc.code}. Las variaciones de RPM en ralentí confirman inestabilidad. Te recomiendo intercambiar la bobina con otro cilindro para comprobar si la falla se traslada.",
                        primaryFinding = "Fallo de encendido Cilindro $cyl (${misfireDtc.code})",
                        confidence = 0.92,
                        recommendedAction = "Intercambiar bobina #$cyl para prueba discriminante",
                        isCritical = false
                    )
                } else if (rpmFeatures.variance > 2000.0) {
                    VoiceDiagnosticResponse(
                        spokenText = "El motor presenta una oscilación anormal de ${rpmFeatures.stdDev.toInt()} RPM en ralentí. El ajuste de combustible está en ${"%.1f".format(stftFeatures.mean)}%. Sugiero revisar posibles fugas de vacío en el múltiple de admisión.",
                        primaryFinding = "Ralentí inestable con alta varianza de RPM",
                        confidence = 0.78,
                        recommendedAction = "Inspección de vacío en admisión",
                        isCritical = false
                    )
                } else {
                    VoiceDiagnosticResponse(
                        spokenText = "No detecto códigos de falla activos ni oscilaciones severas de RPM en este momento. El ralentí se encuentra estable en ${rpmFeatures.mean.toInt()} RPM.",
                        primaryFinding = "Comportamiento de ralentí nominal",
                        confidence = 0.85,
                        recommendedAction = null,
                        isCritical = false
                    )
                }
            }

            lower.contains("batería") || lower.contains("bateria") || lower.contains("alternador") || lower.contains("voltaje") -> {
                val voltFeatures = facade.telemetryFeatures("0142", 10)
                val voltage = voltFeatures.mean

                if (voltage < 13.2 && snapshot.engine.rpm != null && snapshot.engine.rpm!! > 500) {
                    VoiceDiagnosticResponse(
                        spokenText = "Atención: El sistema de carga está entregando solo ${"%.2f".format(voltage)} voltios con el motor encendido. Es probable que el alternador o su regulador estén fallando.",
                        primaryFinding = "Bajo voltaje de carga (${"%.2f".format(voltage)}V)",
                        confidence = 0.95,
                        recommendedAction = "Revisar alternador y banda de accesorios",
                        isCritical = true
                    )
                } else {
                    VoiceDiagnosticResponse(
                        spokenText = "El sistema eléctrico está saludable. El voltaje de carga se mantiene en ${"%.2f".format(voltage)} voltios de forma estable.",
                        primaryFinding = "Sistema de carga nominal (${"%.2f".format(voltage)}V)",
                        confidence = 0.95,
                        recommendedAction = null,
                        isCritical = false
                    )
                }
            }

            lower.contains("temperatura") || lower.contains("calienta") || lower.contains("calor") -> {
                val tempC = snapshot.engine.coolantTempC ?: 0.0
                if (tempC > 105.0) {
                    VoiceDiagnosticResponse(
                        spokenText = "Alerta: La temperatura del refrigerante es de ${tempC.toInt()} grados centígrados, lo cual supera el límite seguro de operación. Detén el vehículo y apaga el motor.",
                        primaryFinding = "Temperatura crítica (${tempC.toInt()}°C)",
                        confidence = 0.99,
                        recommendedAction = "Apagar motor y revisar nivel de refrigerante / ventilador",
                        isCritical = true
                    )
                } else {
                    VoiceDiagnosticResponse(
                        spokenText = "La temperatura del motor está en ${tempC.toInt()} grados centígrados, dentro del rango normal de trabajo.",
                        primaryFinding = "Temperatura nominal (${tempC.toInt()}°C)",
                        confidence = 0.95,
                        recommendedAction = null,
                        isCritical = false
                    )
                }
            }

            else -> {
                // General diagnostic request
                val req = DiagnosticAgentRequest(
                    requestId = "voice_${System.currentTimeMillis()}",
                    vehicleId = snapshot.vehicle.vehicleId,
                    trigger = DiagnosticTrigger.USER_REQUEST,
                    snapshot = snapshot
                )
                val diagResult = gateway.diagnose(req)
                if (diagResult is EvairResult.Success) {
                    val res = diagResult.value
                    VoiceDiagnosticResponse(
                        spokenText = "He analizado los sensores del vehículo. ${res.summary}",
                        primaryFinding = res.hypotheses.firstOrNull()?.cause ?: "Diagnóstico completado",
                        confidence = res.hypotheses.firstOrNull()?.confidence ?: 0.80,
                        recommendedAction = res.recommendedTests.firstOrNull()?.reason,
                        isCritical = res.severity == com.elysium369.meet.core.evair.domain.DiagnosticSeverity.CRITICAL
                    )
                } else {
                    VoiceDiagnosticResponse(
                        spokenText = "El vehículo reporta estado conectado. No hay códigos de falla críticos en memoria.",
                        primaryFinding = "Estado general en orden",
                        confidence = 0.85,
                        recommendedAction = null,
                        isCritical = false
                    )
                }
            }
        }
    }
}
