package com.elysium369.meet.core.knowledge.graph

import java.util.Locale

data class DiagnosticKnowledgeQuery(
    val namespace: String,
    val displayCode: String,
    val rawDtcIdentity: String,
    val failureType: Int?,
    val ecuEndpoint: String,
    val vehicleProfile: ActiveVehicleIdentity?,
)

enum class DiagnosticKnowledgeAuthority {
    RAW_IDENTITY_EXACT,
    DISPLAY_CODE_GENERIC_FALLBACK,
    UNAVAILABLE,
}

data class DiagnosticKnowledgeMatch(
    val node: KnowledgeNode?,
    val authority: DiagnosticKnowledgeAuthority,
    val query: DiagnosticKnowledgeQuery,
    val notice: String,
)

/**
 * Resolves diagnostic knowledge without pretending that a display P-code is an exact
 * UDS/OEM identity. Exact nodes are optional; generic knowledge remains useful but is
 * always labelled as a fallback.
 */
class DiagnosticKnowledgeQueryEngine(
    private val repository: AutomotiveKnowledgeGraphRepository,
) {
    fun resolve(query: DiagnosticKnowledgeQuery): DiagnosticKnowledgeMatch {
        val namespace = query.namespace.trim().uppercase(Locale.ROOT)
        val raw = query.rawDtcIdentity.trim().uppercase(Locale.ROOT)
        val display = query.displayCode.trim().uppercase(Locale.ROOT)
        val endpoint = query.ecuEndpoint.trim().uppercase(Locale.ROOT)

        val exactKeys = if (raw.isBlank()) emptyList() else listOf(
            listOf(namespace, raw, query.failureType?.toString(), endpoint)
                .filterNotNull().filter(String::isNotBlank).joinToString(":"),
            listOf(namespace, raw, query.failureType?.toString())
                .filterNotNull().filter(String::isNotBlank).joinToString(":"),
            "$namespace:$raw",
            raw,
        ).distinct()
        val exact = exactKeys.asSequence()
            .flatMap { repository.nodesByCanonicalKey(it).asSequence() }
            .firstOrNull { it.type == KnowledgeNodeType.DTC }
        if (exact != null) {
            return DiagnosticKnowledgeMatch(
                node = exact,
                authority = DiagnosticKnowledgeAuthority.RAW_IDENTITY_EXACT,
                query = query,
                notice = "Conocimiento enlazado a identidad DTC cruda; la aplicabilidad vehicular aún se evalúa por separado.",
            )
        }

        val generic = display.takeIf(String::isNotBlank)?.let(repository::dtc)
        return if (generic != null) {
            DiagnosticKnowledgeMatch(
                node = generic,
                authority = DiagnosticKnowledgeAuthority.DISPLAY_CODE_GENERIC_FALLBACK,
                query = query,
                notice = "GENERIC_FALLBACK: el código visible coincide, pero no existe autoridad exacta para raw DTC/ECU/failure type.",
            )
        } else {
            DiagnosticKnowledgeMatch(
                node = null,
                authority = DiagnosticKnowledgeAuthority.UNAVAILABLE,
                query = query,
                notice = "No existe conocimiento aplicable a la identidad diagnóstica capturada.",
            )
        }
    }
}
