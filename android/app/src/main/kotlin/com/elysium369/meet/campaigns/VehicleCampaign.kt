package com.elysium369.meet.campaigns

import com.elysium369.meet.core.domain.SourceAuthority

enum class CampaignType(val displayName: String, val glyph: String) {
    SAFETY_RECALL("Campaña de Retiro por Seguridad (Recall)", "🚨"),
    SERVICE_CAMPAIGN("Campaña de Servicio / Actualización OEM", "🔧"),
    TECHNICAL_SERVICE_BULLETIN_TSB("Boletín Técnico de Servicio (TSB)", "📋"),
    MEET_ADVISORY("Aviso Técnico Preventivo MEET", "💡")
}

enum class CampaignRemedyStatus(val label: String) {
    REMEDY_AVAILABLE("Reparación Disponible en Concesionario"),
    REMEDY_NOT_AVAILABLE("Reparación en Desarrollo por el Fabricante"),
    ALREADY_COMPLETED("Campaña Ya Ejecutada en este Vehículo"),
    INSPECTION_REQUIRED("Requiere Verificación Física Previa")
}

data class VehicleCampaign(
    val campaignId: String,
    val campaignCode: String, // e.g. NHTSA Campaign ID or OEM Code
    val type: CampaignType,
    val title: String,
    val summary: String,
    val affectedSystem: String,
    val remedyDescription: String,
    val remedyStatus: CampaignRemedyStatus,
    val authority: SourceAuthority,
    val announcedDateUtc: Long?,
    val isSafetyCritical: Boolean = true
)
