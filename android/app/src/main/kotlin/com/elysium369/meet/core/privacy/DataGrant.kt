package com.elysium369.meet.core.privacy

import kotlinx.coroutines.flow.StateFlow

enum class GrantScope(val displayName: String, val description: String) {
    DIAGNOSTIC_AND_REPAIRS("Diagnóstico y Reparaciones", "Acceso al historial de fallas y órdenes de trabajo para el taller."),
    PASSPORT_REDACTED_BUYER("Pasaporte Redactado (Comprador)", "Historial técnico sin facturas personales ni datos de contacto."),
    INCIDENT_EVIDENCE_ONLY("Evidencia de Incidente (Aseguradora)", "Telemetría, fotos y reporte del siniestro específico."),
    FULL_MAINTENANCE_HISTORY("Mantenimiento Completo", "Registros de cambio de fluidos, filtros y servicios periódicos."),
    LIVE_TELEMETRY_STREAM("Telemetría en Vivo", "Transmisión remota temporal durante sesión Live Link.")
}

data class DataGrant(
    val grantId: String,
    val ownerPrincipalId: String,
    val recipientPrincipalId: String,
    val recipientName: String,
    val vehicleId: String,
    val scope: GrantScope,
    val validFromUtc: Long,
    val validUntilUtc: Long,
    val reason: String,
    val revocable: Boolean = true,
    val isRevoked: Boolean = false
) {
    val isActive: Boolean
        get() = !isRevoked && System.currentTimeMillis() in validFromUtc..validUntilUtc
}

interface DataGrantRepository {
    val activeGrants: StateFlow<List<DataGrant>>
    suspend fun createGrant(grant: DataGrant)
    suspend fun revokeGrant(grantId: String)
    suspend fun getGrantsForVehicle(vehicleId: String): List<DataGrant>
}
