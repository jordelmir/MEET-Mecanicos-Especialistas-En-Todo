package com.elysium369.meet.safety.drugimpunity

import java.net.URI
import java.util.Locale

/**
 * Validates the shape of a URL supplied with a user-submitted follow-up.
 * This does not verify the publisher, page contents, publication date or truth.
 */
fun normalizeSafetyUpdateSourceUrl(value: String): Result<String> = runCatching {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "Aporta el enlace a una fuente externa." }
    val uri = URI(normalized)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    require(scheme == "https" || scheme == "http") { "El enlace debe usar HTTP o HTTPS." }
    require(!uri.host.isNullOrBlank()) { "El enlace debe incluir un dominio válido." }
    require(uri.userInfo == null) { "No incluyas credenciales dentro del enlace." }
    normalized
}

fun isValidSafetyUpdateSourceUrl(value: String): Boolean =
    normalizeSafetyUpdateSourceUrl(value).isSuccess
