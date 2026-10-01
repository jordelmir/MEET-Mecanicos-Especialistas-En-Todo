package com.elysium.vanguard.recordshield

import java.security.MessageDigest

/** The host supplies the current authenticated principal; the module never guesses ownership. */
object RecordShieldIdentity {
    @Volatile private var provider: (() -> String?)? = null

    fun install(provider: () -> String?) { this.provider = provider }

    fun principalId(): String? = runCatching { provider?.invoke() }.getOrNull()?.takeIf(String::isNotBlank)

    fun storageScope(): String {
        val principal = principalId() ?: "local_sovereign_principal"
        return MessageDigest.getInstance("SHA-256")
            .digest(principal.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }
}
