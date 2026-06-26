package com.elysium369.meet.core.monetization

/**
 * Single switch for APK monetization behavior.
 *
 * Billing, Supabase verification, and entitlement storage stay in the codebase so
 * production monetization can be restored without rebuilding the architecture.
 */
object MonetizationPolicy {
    const val PAYWALLS_ENABLED = false
    val LOCAL_FULL_ACCESS: Boolean = !PAYWALLS_ENABLED
    const val LOCAL_UNLOCK_TOKEN_PREFIX = "local-full-access"
    const val ACCESS_LABEL = "Acceso completo temporal"
    const val ACCESS_MESSAGE = "Todas las funciones PRO estan liberadas en esta APK."

    fun unlocksPremium(serverEntitlement: Boolean = false): Boolean {
        return LOCAL_FULL_ACCESS || serverEntitlement
    }

    fun localUnlockToken(scope: String, id: String): String {
        return "$LOCAL_UNLOCK_TOKEN_PREFIX:$scope:$id"
    }
}
