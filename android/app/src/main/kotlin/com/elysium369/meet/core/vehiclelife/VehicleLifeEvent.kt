package com.elysium369.meet.core.vehiclelife

import com.elysium369.meet.core.domain.EntityRef
import com.elysium369.meet.core.domain.SourceAuthority

enum class VehicleLifeEventType(val displayName: String, val glyph: String) {
    DIAGNOSTIC("Sesión de Diagnóstico", "📊"),
    FINDING("Hallazgo / Código DTC", "⚠️"),
    MAINTENANCE("Mantenimiento Preventivo", "🔧"),
    REPAIR("Reparación Mecánica", "🛠️"),
    PART_INSTALLED("Pieza / Repuesto Instalado", "📦"),
    PART_REMOVED("Pieza Retirada", "🗑️"),
    INSPECTION("Inspección Técnica / Peritaje", "🔍"),
    ACCIDENT("Incidente / Colisión", "💥"),
    DOCUMENT("Documento / Póliza / Registro", "📄"),
    OWNERSHIP("Cambio de Titularidad", "🤝"),
    TRIP("Bitácora de Viaje", "🛣️"),
    ACCESS("Emisión de Llave / Acceso", "🗝️"),
    COST("Gasto Financiero / Factura", "💰"),
    RECALL("Campaña de Seguridad / Recall", "📢"),
    WARRANTY("Garantía Aplicada", "🛡️"),
    SERVICE("Servicio Mecánico / Asistencia", "🧰"),
    BATTERY("Diagnóstico de Batería / Energía", "⚡"),
    TIRE("Servicio de Neumáticos", "🛞")
}

data class VehicleLifeActor(
    val principalId: String,
    val roleTitle: String,
    val displayName: String
)

/**
 * MEET Vehicle Life OS — Universal Vehicle Life Event.
 * Feeds the Universal Timeline, Vehicle Passport, Proof Graph, and Buyer/Sale Mode projections.
 */
data class VehicleLifeEvent(
    val eventId: String,
    val vehicleId: String,
    val ownerPrincipalId: String,
    val type: VehicleLifeEventType,
    val occurredAtUtc: Long,
    val recordedAtUtc: Long = System.currentTimeMillis(),
    val actor: VehicleLifeActor,
    val title: String,
    val summary: String,
    val source: SourceAuthority,
    val evidenceRefs: List<EntityRef.EvidenceRef> = emptyList(),
    val relatedEntityRefs: List<EntityRef> = emptyList(),
    val isVerified: Boolean = false
)
