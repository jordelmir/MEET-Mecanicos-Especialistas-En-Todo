package com.elysium369.meet.timeline

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import kotlinx.serialization.Serializable

/**
 * Tipos de eventos que se registran en la línea de tiempo del vehículo.
 * Ningún evento importante puede ocurrir sin generar entrada aquí.
 */
@Serializable
enum class TimelineEventType {
    DTC_DETECTED,
    DTC_CLEARED,
    FREEZE_FRAME_CAPTURED,
    LIVE_SESSION_STARTED,
    LIVE_SESSION_ENDED,
    REPAIR_GUIDE_OPENED,
    MANUAL_OPENED,
    PART_REQUESTED,
    TOW_REQUESTED,
    MECHANIC_REQUESTED,
    SERVICE_RESET,
    BIDIRECTIONAL_TEST,
    REPORT_GENERATED,
    MAINTENANCE_CREATED,
    MAINTENANCE_COMPLETED,
    OSCILLOSCOPE_CAPTURE,
    HUD_SESSION,
    DVIR_INSPECTION,
    SNAPSHOT_CREATED,
    COMPARISON_RAN,
    DIAGNOSIS_GENERATED,
    REPAIR_COMPLETED,
    PART_REPLACED,
    FORGE_ARTIFACT_CREATED
}

@Serializable
enum class TimelineSource { OBD, USER, AI, SYSTEM, PROVIDER }

/**
 * Evento append-only de la línea de tiempo del vehículo.
 *
 * Regla crítica: NADA se pierde. Cada acción importante debe generar un evento.
 */
@Serializable
data class VehicleTimelineEvent(
    val id: String,
    val vehicleId: String,
    val eventType: TimelineEventType,
    val title: String,
    val description: String = "",
    val severity: Int = 0,
    val source: TimelineSource,
    val provenance: DiagnosticProvenance,
    val payloadJson: String? = null,
    val createdAtMs: Long
) {
    init {
        require(id.isNotBlank()) { "Timeline event id cannot be blank" }
        require(vehicleId.isNotBlank()) { "Timeline event vehicleId cannot be blank" }
        require(title.isNotBlank()) { "Timeline event title cannot be blank" }
        require(severity in 0..4) { "severity must be in [0,4]" }
        require(createdAtMs > 0L) { "createdAtMs must be > 0" }
    }

    /** Etiqueta legible del tipo de evento. */
    val typeLabel: String
        get() = when (eventType) {
            TimelineEventType.DTC_DETECTED -> "DTC detectado"
            TimelineEventType.DTC_CLEARED -> "DTC borrado"
            TimelineEventType.FREEZE_FRAME_CAPTURED -> "Freeze frame capturado"
            TimelineEventType.LIVE_SESSION_STARTED -> "Sesión Live iniciada"
            TimelineEventType.LIVE_SESSION_ENDED -> "Sesión Live cerrada"
            TimelineEventType.REPAIR_GUIDE_OPENED -> "Guía de reparación abierta"
            TimelineEventType.MANUAL_OPENED -> "Manual abierto"
            TimelineEventType.PART_REQUESTED -> "Repuesto solicitado"
            TimelineEventType.TOW_REQUESTED -> "Grúa solicitada"
            TimelineEventType.MECHANIC_REQUESTED -> "Mecánico solicitado"
            TimelineEventType.SERVICE_RESET -> "Service reset ejecutado"
            TimelineEventType.BIDIRECTIONAL_TEST -> "Prueba activa ejecutada"
            TimelineEventType.REPORT_GENERATED -> "Reporte generado"
            TimelineEventType.MAINTENANCE_CREATED -> "Mantenimiento creado"
            TimelineEventType.MAINTENANCE_COMPLETED -> "Mantenimiento completado"
            TimelineEventType.OSCILLOSCOPE_CAPTURE -> "Captura osciloscopio"
            TimelineEventType.HUD_SESSION -> "Sesión HUD"
            TimelineEventType.DVIR_INSPECTION -> "Inspección DVIR"
            TimelineEventType.SNAPSHOT_CREATED -> "Snapshot creado"
            TimelineEventType.COMPARISON_RAN -> "Comparación antes/después"
            TimelineEventType.DIAGNOSIS_GENERATED -> "Diagnóstico generado"
            TimelineEventType.REPAIR_COMPLETED -> "Reparación completada"
            TimelineEventType.PART_REPLACED -> "Pieza reemplazada"
            TimelineEventType.FORGE_ARTIFACT_CREATED -> "Artefacto Forge creado"
        }
}