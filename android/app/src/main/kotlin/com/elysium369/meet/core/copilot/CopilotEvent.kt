package com.elysium369.meet.core.copilot

import com.elysium369.meet.core.alerts.AlertSeverity

enum class CopilotEventType {
    VOLTAJE_BAJO,
    TEMPERATURA_ANORMAL,
    FUEL_TRIM_EXCESIVO,
    MISFIRE,
    CARGA_EXCESIVA,
    MOTOR_FRIO,
    SOBRECALENTAMIENTO,
    STATUS_NORMAL
}

data class CopilotEvent(
    val type: CopilotEventType,
    val severity: AlertSeverity,
    val messageEs: String,
    val messageEn: String,
    val timestamp: Long = System.currentTimeMillis()
)
