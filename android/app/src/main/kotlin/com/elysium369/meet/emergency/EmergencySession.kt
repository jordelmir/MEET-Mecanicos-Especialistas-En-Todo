package com.elysium369.meet.emergency

import com.elysium369.meet.core.domain.EntityRef

enum class EmergencyType(val displayName: String, val glyph: String) {
    NO_START("No Enciende / Sin Arranque", "🛑"),
    OVERHEATING("Sobrecalentamiento de Motor", "🌡️"),
    BATTERY("Batería Descargada / Eléctrico", "⚡"),
    FLAT_TIRE("Neumático Ponchado / Presión", "🛞"),
    ACCIDENT("Colisión / Incidente Vial", "💥"),
    STRANDED("Varado en Carretera", "🛣️"),
    ACCESS_PROBLEM("Problema de Llave / Acceso", "🗝️"),
    NO_FUEL("Sin Combustible / Inyección", "⛽"),
    OTHER("Otra Emergencia Mecánica", "🆘")
}

enum class EmergencyResolution(val label: String) {
    SELF_HELP_GUIDE("Guía de Solución en el Sitio"),
    REQUEST_MECHANIC("Solicitar Mecánico a Domicilio"),
    REQUEST_TOW("Solicitar Grúa de Auxilio"),
    CALL_EMERGENCY_SERVICES("Llamar Servicios de Emergencia (911)")
}

data class EmergencyTriageStep(
    val stepId: String,
    val question: String,
    val options: List<String>,
    val selectedOptionIndex: Int? = null,
    val diagnosticData: Map<String, String> = emptyMap()
)

/**
 * Authoritative Emergency Session Root.
 *
 * Laws:
 * - One EmergencySession root for the entire product (no competing CircleEmergency or PttEmergency).
 * - Emergency priority affects floor arbitration, but NEVER grants remote microphone activation.
 * - Local device remains sole microphone authority.
 */
data class EmergencySession(
    val sessionId: String,
    val vehicleId: String,
    val type: EmergencyType,
    val startedAtUtc: Long = System.currentTimeMillis(),
    val steps: List<EmergencyTriageStep> = emptyList(),
    val recommendedResolution: EmergencyResolution? = null,
    val evidenceRefs: List<EntityRef.EvidenceRef> = emptyList(),
    val isResolved: Boolean = false,
    val journeyRef: String? = null,
    val circleRef: String? = null,
    val conversationRef: String? = null,
    val pttChannelRef: String? = null,
    val presenceEvidence: String? = null,
)
