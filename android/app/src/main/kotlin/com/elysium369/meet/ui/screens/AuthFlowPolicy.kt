package com.elysium369.meet.ui.screens

import java.net.URI

enum class AuthFlowMode {
    LOGIN,
    SIGN_UP,
    RECOVERY_REQUEST,
    PASSWORD_UPDATE,
}

object AuthFormPolicy {
    const val MIN_PASSWORD_LENGTH = 12

    fun normalizeEmail(value: String): String = value.trim().lowercase()

    fun validateEmail(value: String): String? {
        val normalized = normalizeEmail(value)
        val at = normalized.indexOf('@')
        return if (
            normalized.isBlank() ||
            at <= 0 ||
            at == normalized.lastIndex ||
            normalized.substring(at + 1).indexOf('.') <= 0 ||
            normalized.any(Char::isWhitespace)
        ) {
            "Escribe un correo electrónico válido."
        } else {
            null
        }
    }

    fun validatePassword(value: String): String? = when {
        value.length < MIN_PASSWORD_LENGTH ->
            "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
        value.none(Char::isLowerCase) || value.none(Char::isUpperCase) ||
            value.none(Char::isDigit) || value.none { !it.isLetterOrDigit() } ->
            "Incluye mayúscula, minúscula, número y símbolo."
        else -> null
    }

    fun validateConfirmation(password: String, confirmation: String): String? =
        if (password == confirmation) null else "Las contraseñas no coinciden."
}

/**
 * Prevents a stale authenticated session from being mistaken for the account
 * whose credentials were just submitted. Account data may only be rendered
 * after the requested email and the authenticated session agree.
 */
object AuthSessionIdentityPolicy {
    fun matchesRequestedAccount(
        requestedEmail: String,
        authenticatedEmail: String?,
    ): Boolean {
        val normalizedAuthenticatedEmail = authenticatedEmail
            ?.let(AuthFormPolicy::normalizeEmail)
            ?.takeIf(String::isNotBlank)
            ?: return false
        return AuthFormPolicy.normalizeEmail(requestedEmail) == normalizedAuthenticatedEmail
    }

    fun mustInvalidateExistingSession(
        requestedEmail: String,
        authenticatedEmail: String?,
    ): Boolean = authenticatedEmail != null &&
        !matchesRequestedAccount(requestedEmail, authenticatedEmail)
}

enum class AuthFailureCode {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    WEAK_PASSWORD,
    RATE_LIMITED,
    NETWORK,
    UNKNOWN,
}

object AuthFailureTranslator {
    fun classify(rawMessage: String?): AuthFailureCode {
        val message = rawMessage.orEmpty().lowercase()
        return when {
            "invalid login credentials" in message || "invalid_credentials" in message ||
                "invalid_grant" in message || "user not found" in message ->
                AuthFailureCode.INVALID_CREDENTIALS
            "email not confirmed" in message || "email_not_confirmed" in message ->
                AuthFailureCode.EMAIL_NOT_CONFIRMED
            "weak_password" in message || "password should be at least" in message ->
                AuthFailureCode.WEAK_PASSWORD
            "rate limit" in message || "over_email_send_rate_limit" in message ||
                "429" in message -> AuthFailureCode.RATE_LIMITED
            "unable to resolve host" in message || "failed to connect" in message ||
                "connectexception" in message || "sockettimeout" in message ||
                "network is unreachable" in message || "no address associated" in message ||
                "timed out" in message -> AuthFailureCode.NETWORK
            else -> AuthFailureCode.UNKNOWN
        }
    }

    fun userMessage(mode: AuthFlowMode, rawMessage: String?): String {
        if (mode == AuthFlowMode.RECOVERY_REQUEST) return recoveryRequestConfirmation()
        return when (classify(rawMessage)) {
            AuthFailureCode.INVALID_CREDENTIALS -> "Correo o contraseña incorrectos."
            AuthFailureCode.EMAIL_NOT_CONFIRMED ->
                "Confirma tu correo electrónico antes de iniciar sesión."
            AuthFailureCode.WEAK_PASSWORD ->
                "La contraseña no cumple la política de seguridad de MEET."
            AuthFailureCode.RATE_LIMITED ->
                "Espera unos minutos antes de volver a intentarlo."
            AuthFailureCode.NETWORK ->
                "No se pudo contactar el servicio de cuentas. Revisa tu conexión."
            AuthFailureCode.UNKNOWN -> when (mode) {
                AuthFlowMode.LOGIN ->
                    "No se pudo iniciar sesión. Revisa los datos o intenta más tarde."
                AuthFlowMode.SIGN_UP ->
                    "No se pudo crear la cuenta. Intenta más tarde."
                AuthFlowMode.PASSWORD_UPDATE ->
                    "No se pudo actualizar la contraseña. Solicita un enlace nuevo."
                AuthFlowMode.RECOVERY_REQUEST -> recoveryRequestConfirmation()
            }
        }
    }

    fun recoveryRequestConfirmation(): String =
        "Si existe una cuenta con ese correo, recibirás instrucciones para recuperar el acceso."
}

object AuthRedirectPolicy {
    const val SCHEME = "meet"
    const val HOST = "auth"
    const val RECOVERY_PATH = "/password-recovery"
    const val RECOVERY_REDIRECT_URL = "$SCHEME://$HOST$RECOVERY_PATH"

    fun isPasswordRecoveryLink(rawValue: String?): Boolean {
        if (rawValue.isNullOrBlank()) return false
        val uri = runCatching { URI(rawValue) }.getOrNull() ?: return false
        if (
            uri.scheme != SCHEME ||
            uri.host != HOST ||
            uri.path != RECOVERY_PATH
        ) {
            return false
        }
        val fields = listOfNotNull(uri.query, uri.fragment)
            .flatMap { part -> part.split('&') }
            .mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
            }
        val isRecovery = fields.any { (key, value) ->
            key.equals("type", ignoreCase = true) && value.equals("recovery", ignoreCase = true)
        }
        val hasNonEmptyProof = fields.any { (key, value) ->
            (key == "access_token" || key == "code") && value.isNotBlank()
        }
        return isRecovery && hasNonEmptyProof
    }
}
