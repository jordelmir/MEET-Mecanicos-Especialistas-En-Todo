package com.elysium369.meet.safety.evidence

/**
 * Presentation semantics for the evidence upload/verification lifecycle.
 *
 * RECEIVED means the server registered the object. Only VERIFIED means the
 * server byte verifier returned MATCH and persisted its receipt.
 * Neither status asserts that the content is truthful or proves an incident.
 */
enum class SafetyEvidenceStatusTone {
    NEUTRAL,
    PENDING,
    IN_PROGRESS,
    VERIFIED,
    ERROR,
}

data class SafetyEvidencePresentation(
    val label: String,
    val tone: SafetyEvidenceStatusTone,
)

fun safetyEvidencePresentation(
    uploadState: String,
    lastErrorCode: String? = null,
): SafetyEvidencePresentation = when (uploadState) {
    "STAGED" -> SafetyEvidencePresentation("Pendiente de carga", SafetyEvidenceStatusTone.PENDING)
    "UPLOADING" -> SafetyEvidencePresentation("Subiendo archivo", SafetyEvidenceStatusTone.IN_PROGRESS)
    "UPLOADED" -> SafetyEvidencePresentation("Carga transferida · falta recibo", SafetyEvidenceStatusTone.PENDING)
    "RECEIVED" -> if (lastErrorCode == "SERVER_VERIFICATION_PENDING") {
        SafetyEvidencePresentation("Verificación del servidor pendiente", SafetyEvidenceStatusTone.PENDING)
    } else {
        SafetyEvidencePresentation("Recibido · verificación de bytes pendiente", SafetyEvidenceStatusTone.PENDING)
    }
    "RETRY" -> SafetyEvidencePresentation("Reintento de carga pendiente", SafetyEvidenceStatusTone.PENDING)
    "VERIFIED" -> SafetyEvidencePresentation("Bytes verificados por servidor", SafetyEvidenceStatusTone.VERIFIED)
    "QUARANTINED" -> SafetyEvidencePresentation("En cuarentena · revisar integridad", SafetyEvidenceStatusTone.ERROR)
    "FAILED" -> SafetyEvidencePresentation("Error de carga", SafetyEvidenceStatusTone.ERROR)
    else -> SafetyEvidencePresentation("Estado no reconocido: $uploadState", SafetyEvidenceStatusTone.NEUTRAL)
}
