package com.elysium369.meet.diagnostic

import kotlinx.serialization.Serializable

/**
 * Procedencia de un dato diagnóstico.
 *
 * Regla del producto: nunca mostrar un dato como real sin provenance explícito.
 * Cada flujo (Scanner, RepairGuide, PDF Report, Forge) debe envolver sus valores
 * en [DiagnosticValue] para que la UI pueda etiquetar correctamente.
 */
@Serializable
sealed class DiagnosticProvenance {

    /** Dato recibido en vivo del adaptador OBD (frame ISO-TP / UDS real). */
    @Serializable
    object Real : DiagnosticProvenance()

    /** Dato de DB local, caché, seed genérico o manual local. */
    @Serializable
    object Offline : DiagnosticProvenance()

    /** Dato explícitamente falso, inyectado para tutorial o demo. */
    @Serializable
    object Simulated : DiagnosticProvenance()

    /** Sin enlace al adaptador / vehículo. No se puede leer. */
    @Serializable
    object SinEnlace : DiagnosticProvenance()

    /** Requiere hardware específico (osciloscopio Hantek, K+DCAN, breakout box). */
    @Serializable
    data class RequiereHardware(val toolName: String) : DiagnosticProvenance()

    /** Adaptador o vehículo no soporta este PID / dato. */
    @Serializable
    data class NoSoportado(val reason: String) : DiagnosticProvenance()

    /** Dato inferido por el motor de reglas / IA. */
    @Serializable
    data class Inferred(val source: String, val confidence: Double) : DiagnosticProvenance() {
        init {
            require(confidence.isFinite() && confidence in 0.0..1.0) {
                "confidence must be in [0,1]"
            }
        }
    }

    /** Entrada manual del usuario (no validada por hardware). */
    @Serializable
    data class ManualEntry(val authorId: String) : DiagnosticProvenance()

    /** Etiqueta legible para mostrar en UI. */
    val displayLabel: String
        get() = when (this) {
            is Real -> "REAL"
            is Offline -> "OFFLINE"
            is Simulated -> "SIMULADO"
            is SinEnlace -> "SIN ENLACE"
            is RequiereHardware -> "REQUIERE $toolName"
            is NoSoportado -> "NO SOPORTADO: $reason"
            is Inferred -> "INFERIDO ($source, ${(confidence * 100).toInt()}%)"
            is ManualEntry -> "MANUAL"
        }

    /** Bandera que la UI usa para no mostrar este dato como real. */
    val isReliableForDiagnosis: Boolean
        get() = this is Real || this is Offline
}

/**
 * Wrapper universal: cualquier valor diagnóstico se entrega con su provenance.
 * La UI está OBLIGADA a mostrar la provenance junto al valor.
 */
@Serializable
data class DiagnosticValue<T>(
    val value: T,
    val provenance: DiagnosticProvenance,
    val timestampMs: Long,
    val source: String = provenance.displayLabel
) {
    companion object {
        fun <T> real(value: T, timestampMs: Long = System.currentTimeMillis()): DiagnosticValue<T> =
            DiagnosticValue(value, DiagnosticProvenance.Real, timestampMs)

        fun <T> offline(value: T, timestampMs: Long = System.currentTimeMillis()): DiagnosticValue<T> =
            DiagnosticValue(value, DiagnosticProvenance.Offline, timestampMs)

        fun <T> sinEnlace(timestampMs: Long = System.currentTimeMillis()): DiagnosticValue<T?> =
            DiagnosticValue(null, DiagnosticProvenance.SinEnlace, timestampMs)

        fun <T> noSoportado(reason: String, timestampMs: Long = System.currentTimeMillis()): DiagnosticValue<T?> =
            DiagnosticValue(null, DiagnosticProvenance.NoSoportado(reason), timestampMs)
    }
}