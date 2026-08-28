package com.elysium369.meet.identity

import android.content.Context

/**
 * Records that this installation previously completed authenticated bootstrap.
 * It is not a credential and never authorizes network writes.
 */
object PrincipalProvisioningStore {
    private const val PREFERENCES = "meet_principal_provisioning"
    private const val PRINCIPAL_ID = "provisioned_principal_id"

    fun principalId(context: Context): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(PRINCIPAL_ID, null)
            ?.takeIf(String::isNotBlank)

    fun recordAuthenticated(context: Context, principalId: String) {
        require(principalId.isNotBlank())
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(PRINCIPAL_ID, principalId)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(PRINCIPAL_ID)
            .apply()
    }
}
