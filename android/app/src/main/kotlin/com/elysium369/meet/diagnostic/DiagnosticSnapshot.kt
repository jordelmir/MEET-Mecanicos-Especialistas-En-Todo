package com.elysium369.meet.diagnostic

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * Snapshot inmutable del estado diagnóstico del vehículo en un instante.
 *
 * Se crea ANTES de cualquier acción de riesgo:
 * - borrar DTC
 * - service reset
 * - prueba activa
 * - adaptación / coding
 * - exportación de reporte
 *
 * Reglas:
 * - Inmutable (data class con val).
 * - provenance siempre presente.
 * - hashSha256 derivado del contenido en el constructor.
 */
@Serializable
data class DiagnosticSnapshot(
    val id: String,
    val vehicleId: String,
    val sessionId: String? = null,
    val createdAtMs: Long,
    val dtcsActive: List<String> = emptyList(),
    val dtcsPending: List<String> = emptyList(),
    val dtcsPermanent: List<String> = emptyList(),
    val freezeFramePidValues: Map<String, Double> = emptyMap(),
    val livePids: Map<String, DiagnosticValue<Double>> = emptyMap(),
    val readiness: Map<String, Boolean> = emptyMap(),
    val ecuVoltage: Double? = null,
    val rpm: Double? = null,
    val coolantTempC: Double? = null,
    val speedKph: Double? = null,
    val engineLoadPct: Double? = null,
    val fuelTrimStft: Double? = null,
    val fuelTrimLtft: Double? = null,
    val rawFrames: List<String> = emptyList(),
    val provenance: DiagnosticProvenance,
    val notes: String = ""
) {

    init {
        require(id.isNotBlank()) { "Snapshot id cannot be blank" }
        require(vehicleId.isNotBlank()) { "Snapshot vehicleId cannot be blank" }
        require(createdAtMs > 0L) { "Snapshot createdAtMs must be > 0" }
    }

    val hashSha256: String = computeHash(
        vehicleId = vehicleId,
        sessionId = sessionId,
        createdAtMs = createdAtMs,
        dtcsActive = dtcsActive,
        dtcsPending = dtcsPending,
        dtcsPermanent = dtcsPermanent,
        freezeFramePidValues = freezeFramePidValues,
        readiness = readiness,
        ecuVoltage = ecuVoltage,
        rpm = rpm,
        coolantTempC = coolantTempC,
        speedKph = speedKph,
        engineLoadPct = engineLoadPct,
        fuelTrimStft = fuelTrimStft,
        fuelTrimLtft = fuelTrimLtft
    )

    /** Resumen legible del snapshot. */
    val summary: String
        get() = buildString {
            append("DTCs: ")
            append("active=${dtcsActive.size}, pending=${dtcsPending.size}, perm=${dtcsPermanent.size}")
            append(" | RPM=$rpm, ECT=$coolantTempC°C, V=$ecuVoltage")
            append(" | provenance=${provenance.displayLabel}")
        }

    companion object {
        /**
         * Construye un snapshot vacío pero válido (para tests o inicialización).
         */
        fun empty(
            vehicleId: String,
            provenance: DiagnosticProvenance = DiagnosticProvenance.Offline,
            createdAtMs: Long = System.currentTimeMillis()
        ): DiagnosticSnapshot = DiagnosticSnapshot(
            id = "snap-${vehicleId}-${createdAtMs}",
            vehicleId = vehicleId,
            createdAtMs = createdAtMs,
            provenance = provenance
        )

        private fun computeHash(
            vehicleId: String,
            sessionId: String?,
            createdAtMs: Long,
            dtcsActive: List<String>,
            dtcsPending: List<String>,
            dtcsPermanent: List<String>,
            freezeFramePidValues: Map<String, Double>,
            readiness: Map<String, Boolean>,
            ecuVoltage: Double?,
            rpm: Double?,
            coolantTempC: Double?,
            speedKph: Double?,
            engineLoadPct: Double?,
            fuelTrimStft: Double?,
            fuelTrimLtft: Double?
        ): String {
            val canonical = buildString {
                append(vehicleId).append('|')
                append(sessionId ?: "").append('|')
                append(createdAtMs).append('|')
                append(dtcsActive.sorted().joinToString(",")).append('|')
                append(dtcsPending.sorted().joinToString(",")).append('|')
                append(dtcsPermanent.sorted().joinToString(",")).append('|')
                append(freezeFramePidValues.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}"}).append('|')
                append(readiness.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}"}).append('|')
                append(ecuVoltage ?: "null").append('|')
                append(rpm ?: "null").append('|')
                append(coolantTempC ?: "null").append('|')
                append(speedKph ?: "null").append('|')
                append(engineLoadPct ?: "null").append('|')
                append(fuelTrimStft ?: "null").append('|')
                append(fuelTrimLtft ?: "null")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(canonical.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}