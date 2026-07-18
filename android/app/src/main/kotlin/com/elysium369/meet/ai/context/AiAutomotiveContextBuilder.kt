package com.elysium369.meet.ai.context

import com.elysium369.meet.ai.domain.*

object AiAutomotiveContextBuilder {
    fun buildContextPrompt(context: AiContext): String {
        return buildString {
            appendLine("=== CONTEXTO AUTOMOTRIZ REAL DE LA APP ===")
            context.vehicle?.let { v ->
                appendLine("Vehículo: ${v.make} ${v.model} ${v.year} (Motor: ${v.engine}, Tracción/Caja: ${v.transmission}, Combustible: ${v.fuel})")
                v.vin?.takeIf { it.isNotBlank() }?.let { appendLine("VIN: $it") }
                v.odometer?.let { appendLine("Odómetro: $it km") }
            }
            context.obd?.let { o ->
                appendLine("Enlace OBD: ${if (o.connected) "CONECTADO" else "DESCONECTADO"}")
                if (o.connected) {
                    appendLine("Protocolo: ${o.protocol} | Adaptador: ${o.adapterType} | Latencia: ${o.latencyMs}ms")
                    appendLine("Voltaje de batería ECU: ${o.batteryVoltage}V")
                    appendLine("Monitores de preparación: ${o.readinessMonitors.joinToString()}")
                }
            }
            if (context.dtcs.isNotEmpty()) {
                appendLine("DTCs Detectados:")
                context.dtcs.forEach { dtc ->
                    appendLine("- ${dtc.code}: ${dtc.description} (Estado: ${dtc.status})")
                }
            } else {
                appendLine("DTCs Detectados: Ninguno.")
            }
            if (context.livePids.isNotEmpty()) {
                appendLine("Parámetros en Tiempo Real (Live PIDs):")
                context.livePids.forEach { pid ->
                    appendLine("- ${pid.pid} (${pid.name}): ${pid.value} ${pid.unit} ${if (pid.anomalous) "[ANOMALÍA]" else ""} ${if (pid.isStale) "[DESACTUALIZADO]" else ""}")
                }
            }
            context.manualAvailability?.let { m ->
                appendLine("Manuales Disponibles Localmente: Manual=${m.manualLocalAvailable}, Diagrama Eléctrico=${m.electricalDiagramAvailable}, Torques/Specs=${m.torqueSpecsAvailable}")
            }
            appendLine("Rol del Usuario: ${context.userRole.name}")
            appendLine("Módulo de la App: ${context.appModule}")
            appendLine("Idioma/Localización: ${context.locale}")
            appendLine("==========================================")
        }
    }
}
