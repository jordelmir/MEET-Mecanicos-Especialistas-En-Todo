package com.elysium369.meet.emergency

import com.elysium369.meet.core.domain.VehicleContext

object EmergencyTriageEngine {

    fun initiateSession(vehicleContext: VehicleContext, type: EmergencyType): EmergencySession {
        val initialSteps = when (type) {
            EmergencyType.NO_START -> listOf(
                EmergencyTriageStep(
                    stepId = "STEP_CRANK",
                    question = "¿El motor de arranque gira al girar la llave o presionar Start?",
                    options = listOf("Gira con fuerza pero no enciende", "Gira lento / con dificultad", "Solo hace 'clic' o silencio total")
                ),
                EmergencyTriageStep(
                    stepId = "STEP_LIGHTS",
                    question = "¿Encienden las luces del tablero o faros delanteros?",
                    options = listOf("Sí, con brillo normal", "Están muy tenues o parpadean", "Completamente apagadas")
                )
            )
            EmergencyType.OVERHEATING -> listOf(
                EmergencyTriageStep(
                    stepId = "STEP_STEAM",
                    question = "¿Hay vapor o fuga visible de refrigerante bajo el capó?",
                    options = listOf("Sí, sale vapor visible", "Hay charco/fuga sin vapor", "Solo la aguja de temperatura está al máximo")
                )
            )
            EmergencyType.BATTERY -> listOf(
                EmergencyTriageStep(
                    stepId = "STEP_JUMP",
                    question = "¿Dispone de cables pasa-corriente o arrancador portátil?",
                    options = listOf("Sí, tengo equipo disponible", "No tengo cómo pasar corriente")
                )
            )
            EmergencyType.ACCIDENT -> listOf(
                EmergencyTriageStep(
                    stepId = "STEP_INJURIES",
                    question = "¿Hay personas lesionadas o riesgo inminente en la vía?",
                    options = listOf("NO hay lesionados (Solo daños materiales)", "SÍ hay personas lesionadas (Requiere ambulancia)")
                )
            )
            else -> listOf(
                EmergencyTriageStep(
                    stepId = "STEP_GENERAL",
                    question = "¿El vehículo se encuentra en un lugar seguro fuera del tráfico?",
                    options = listOf("Sí, en zona segura", "No, en carril de circulación / riesgo")
                )
            )
        }

        return EmergencySession(
            sessionId = "EMG_${System.currentTimeMillis()}",
            vehicleId = vehicleContext.vehicleId,
            type = type,
            steps = initialSteps
        )
    }

    fun evaluateTriage(session: EmergencySession): EmergencyResolution {
        return when (session.type) {
            EmergencyType.ACCIDENT -> {
                val injuryStep = session.steps.find { it.stepId == "STEP_INJURIES" }
                if (injuryStep?.selectedOptionIndex == 1) EmergencyResolution.CALL_EMERGENCY_SERVICES
                else EmergencyResolution.REQUEST_TOW
            }
            EmergencyType.OVERHEATING -> EmergencyResolution.REQUEST_TOW
            EmergencyType.BATTERY -> {
                val jumpStep = session.steps.find { it.stepId == "STEP_JUMP" }
                if (jumpStep?.selectedOptionIndex == 0) EmergencyResolution.SELF_HELP_GUIDE
                else EmergencyResolution.REQUEST_MECHANIC
            }
            EmergencyType.NO_START -> {
                val crankStep = session.steps.find { it.stepId == "STEP_CRANK" }
                if (crankStep?.selectedOptionIndex == 2) EmergencyResolution.REQUEST_MECHANIC
                else EmergencyResolution.REQUEST_TOW
            }
            else -> EmergencyResolution.REQUEST_TOW
        }
    }
}
