package com.elysium369.meet.core.agentstore.data

import com.elysium369.meet.core.agentstore.domain.AgentEntitlementGateway
import com.elysium369.meet.core.agentstore.domain.EntitlementSnapshot
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class UserEntitlementDto(
    @SerialName("entitlement_id")
    val entitlementId: String,
    val status: String = "ACTIVE",
)

/**
 * Authoritative Supabase implementation of AgentEntitlementGateway.
 * Strictly queries server-side `user_entitlements` table.
 * Fail-closed: unauthenticated or unreachable backend returns failure.
 */
@Singleton
class SupabaseAgentEntitlementGateway @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : AgentEntitlementGateway {

    override fun currentPrincipalId(): String? = supabaseClient.auth.currentUserOrNull()?.id

    private val logger = LoggerFactory.getLogger(SupabaseAgentEntitlementGateway::class.java)

    override suspend fun fetchAuthoritativeEntitlements(): Result<EntitlementSnapshot> = withContext(Dispatchers.IO) {
        try {
            val user = supabaseClient.auth.currentUserOrNull()
            if (user == null) {
                logger.warn("AgentEntitlementGateway: no authenticated user found. Denying premium entitlements.")
                return@withContext Result.failure(IllegalStateException("UNAUTHENTICATED"))
            }

            val records = supabaseClient.postgrest["user_entitlements"]
                .select {
                    filter {
                        eq("user_id", user.id)
                        eq("status", "ACTIVE")
                    }
                }
                .decodeList<UserEntitlementDto>()

            val entitlements = records.map { it.entitlementId }.toSet()
            check(currentPrincipalId() == user.id) { "ACCOUNT_CHANGED" }

            Result.success(
                EntitlementSnapshot(
                    principalId = user.id,
                    entitlements = entitlements,
                    asOfEpochMs = System.currentTimeMillis(),
                    revision = System.currentTimeMillis(),
                )
            )
        } catch (e: Exception) {
            logger.warn("AgentEntitlementGateway: authoritative fetch unavailable")
            Result.failure(e)
        }
    }
}
