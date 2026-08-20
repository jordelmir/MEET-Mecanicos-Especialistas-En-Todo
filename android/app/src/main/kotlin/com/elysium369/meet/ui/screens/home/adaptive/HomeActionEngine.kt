package com.elysium369.meet.ui.screens.home.adaptive

import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.navigation.MeetDestinations

object HomeActionEngine {

    fun derivePrioritizedActions(
        hasVehicle: Boolean,
        vehicleId: String?,
        obdState: ObdState,
        activeDtcs: List<String>,
        healthScore: Int,
        monitorsReady: Int,
        monitorsTotal: Int,
        maxActions: Int = 3
    ): List<HomeAction> {
        val actions = mutableListOf<HomeAction>()

        // 1. Vehicle Selection Rule
        if (!hasVehicle) {
            actions.add(
                HomeAction(
                    id = "ACT_NO_VEHICLE",
                    priority = HomeActionPriority.CRITICAL,
                    category = HomeActionCategory.SYSTEM_NOTICE,
                    title = "Sin vehículo seleccionado",
                    subtitle = "Selecciona o registra un automóvil en tu garaje para activar diagnósticos y telemetría.",
                    destination = MeetDestinations.GARAGE,
                    buttonLabel = "ABRIR GARAJE",
                    glyph = "🚗"
                )
            )
            return actions.take(maxActions)
        }

        // 2. Critical DTC Faults (Deduplicated single high-priority action)
        if (activeDtcs.isNotEmpty()) {
            val primaryDtc = activeDtcs.first()
            val totalFaults = activeDtcs.size
            val faultDescription = if (totalFaults == 1) {
                "Código $primaryDtc activo detectado en la ECU."
            } else {
                "Código $primaryDtc y ${totalFaults - 1} falla(s) adicional(es) detectadas."
            }

            actions.add(
                HomeAction(
                    id = "ACT_DTC_FAULT",
                    priority = HomeActionPriority.CRITICAL,
                    category = HomeActionCategory.DIAGNOSTIC_FAULT,
                    title = "$totalFaults Códigos de Falla (DTC)",
                    subtitle = "$faultDescription Requiere diagnóstico y revisión física.",
                    destination = MeetDestinations.DTCS,
                    buttonLabel = "INSPECCIONAR FALLAS",
                    glyph = "⚠️",
                    evidenceRefs = activeDtcs
                )
            )
        }

        // 3. OBD Connection Rule
        if (obdState != ObdState.CONNECTED) {
            actions.add(
                HomeAction(
                    id = "ACT_CONNECT_OBD",
                    priority = if (activeDtcs.isNotEmpty()) HomeActionPriority.HIGH else HomeActionPriority.NORMAL,
                    category = HomeActionCategory.OBD_CONNECTION,
                    title = if (obdState == ObdState.CONNECTING) "Estableciendo enlace OBD..." else "Enlazar Escáner OBD",
                    subtitle = if (obdState == ObdState.ERROR) "Error de conexión previo. Reintenta el emparejamiento Bluetooth/K-Line." else "Conecta tu adaptador ELM327 / OBDLink para lectura en vivo.",
                    destination = MeetDestinations.SCANNER,
                    buttonLabel = if (obdState == ObdState.CONNECTING) "VER ESTADO" else "CONECTAR OBD",
                    glyph = "🔌"
                )
            )
        }

        // 4. Low Health Score / Maintenance Check
        if (healthScore in 1..70) {
            actions.add(
                HomeAction(
                    id = "ACT_HEALTH_DEGRADED",
                    priority = HomeActionPriority.HIGH,
                    category = HomeActionCategory.MAINTENANCE_DUE,
                    title = "Salud Vehicular Reducida ($healthScore%)",
                    subtitle = "Sistemas mecánicos o sensores reportan lecturas fuera de rango óptimo.",
                    destination = MeetDestinations.HEALTH_SCORE,
                    buttonLabel = "REVISAR SALUD",
                    glyph = "🩺"
                )
            )
        }

        // 5. Readiness Monitors Pending
        if (monitorsTotal > 0 && monitorsReady < monitorsTotal && obdState == ObdState.CONNECTED) {
            val pendingCount = monitorsTotal - monitorsReady
            actions.add(
                HomeAction(
                    id = "ACT_MONITORS_PENDING",
                    priority = HomeActionPriority.NORMAL,
                    category = HomeActionCategory.INSPECTION_PENDING,
                    title = "Monitores I/M Incompletos ($monitorsReady/$monitorsTotal)",
                    subtitle = "$pendingCount monitor(es) pendientes de ciclo de conducción para verificación técnica.",
                    destination = MeetDestinations.SCANNER,
                    buttonLabel = "VER MONITORES",
                    glyph = "📊"
                )
            )
        }

        // 6. Routine Health Check fallback if clean
        if (actions.isEmpty()) {
            actions.add(
                HomeAction(
                    id = "ACT_ALL_CLEAR",
                    priority = HomeActionPriority.LOW,
                    category = HomeActionCategory.SYSTEM_NOTICE,
                    title = "Sistemas en Estado Nominal",
                    subtitle = "No hay fallas activas registradas. Puedes realizar un escaneo preventivo o consultar la IA.",
                    destination = MeetDestinations.SCANNER,
                    buttonLabel = "INICIAR ESCANEO",
                    glyph = "✅"
                )
            )
        }

        return actions
            .sortedBy { it.priority.level }
            .take(maxActions)
    }
}
