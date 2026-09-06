package com.elysium369.meet.provider.data.gateway

import com.elysium369.meet.mobility.data.protocol.ProtocolViolation
import com.elysium369.meet.mobility.data.protocol.optionalDouble
import com.elysium369.meet.mobility.data.protocol.optionalInstant
import com.elysium369.meet.mobility.data.protocol.optionalLong
import com.elysium369.meet.mobility.data.protocol.optionalString
import com.elysium369.meet.mobility.data.protocol.optionalUuid
import com.elysium369.meet.mobility.data.protocol.requireDouble
import com.elysium369.meet.mobility.data.protocol.requireInstant
import com.elysium369.meet.mobility.data.protocol.requireLong
import com.elysium369.meet.mobility.data.protocol.requireString
import com.elysium369.meet.mobility.data.protocol.requireUuid
import com.elysium369.meet.provider.domain.gateway.ProviderOperationsGateway
import com.elysium369.meet.provider.domain.models.ProviderBalance
import com.elysium369.meet.provider.domain.models.ProviderCapability
import com.elysium369.meet.provider.domain.models.ProviderContext
import com.elysium369.meet.provider.domain.models.ProviderDashboard
import com.elysium369.meet.provider.domain.models.ProviderNotification
import com.elysium369.meet.provider.domain.models.ProviderNotificationCategory
import com.elysium369.meet.provider.domain.models.ProviderOperationalState
import com.elysium369.meet.provider.domain.models.ProviderPerformance
import com.elysium369.meet.provider.domain.models.ProviderPreferences
import com.elysium369.meet.provider.domain.result.ProviderErrorCode
import com.elysium369.meet.provider.domain.result.ProviderResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class SupabaseProviderOperationsGateway @Inject constructor(
    private val supabase: SupabaseClient,
) : ProviderOperationsGateway {

    override suspend fun goOnline(
        capability: ProviderCapability,
        telemetrySessionId: UUID,
        appVersion: String,
        deviceBatteryPct: Int?,
        networkClass: String?,
    ): ProviderResult<ProviderContext> {
        return try {
            val params = buildJsonObject {
                put("p_capability", capability.name)
                put("p_telemetry_session_id", telemetrySessionId.toString())
                put("p_app_version", appVersion)
                deviceBatteryPct?.let { put("p_device_battery_pct", it) }
                networkClass?.let { put("p_network_class", it) }
            }

            val response = supabase.postgrest.rpc("provider_go_online_v1", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val contextJson = response["context"]?.jsonObject
                    ?: throw ProtocolViolation("context", "missing provider context in response")
                ProviderResult.Success(parseProviderContext(contextJson))
            } else {
                val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                val message = response["message"]?.jsonPrimitive?.content
                ProviderResult.Failure(
                    code = ProviderErrorCode.fromString(errorCodeStr),
                    message = message,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (pv: ProtocolViolation) {
            ProviderResult.Failure(
                code = ProviderErrorCode.PROTOCOL_VIOLATION,
                message = "${pv.field}: ${pv.violation}",
                cause = pv,
            )
        } catch (t: Throwable) {
            ProviderResult.Failure(
                code = ProviderErrorCode.NETWORK_ERROR,
                message = t.message,
                cause = t,
            )
        }
    }

    override suspend fun goOffline(
        reason: String?,
    ): ProviderResult<ProviderContext> {
        return try {
            val params = buildJsonObject {
                reason?.let { put("p_reason", it) }
            }

            val response = supabase.postgrest.rpc("provider_go_offline_v1", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val contextJson = response["context"]?.jsonObject
                    ?: throw ProtocolViolation("context", "missing provider context in response")
                ProviderResult.Success(parseProviderContext(contextJson))
            } else {
                val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                val message = response["message"]?.jsonPrimitive?.content
                ProviderResult.Failure(
                    code = ProviderErrorCode.fromString(errorCodeStr),
                    message = message,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (pv: ProtocolViolation) {
            ProviderResult.Failure(
                code = ProviderErrorCode.PROTOCOL_VIOLATION,
                message = "${pv.field}: ${pv.violation}",
                cause = pv,
            )
        } catch (t: Throwable) {
            ProviderResult.Failure(
                code = ProviderErrorCode.NETWORK_ERROR,
                message = t.message,
                cause = t,
            )
        }
    }

    override suspend fun getConsoleSnapshot(
        capability: ProviderCapability,
    ): ProviderResult<ProviderDashboard> {
        return try {
            val params = buildJsonObject {
                put("p_capability", capability.name)
            }

            val response = supabase.postgrest.rpc("provider_get_console_snapshot_v1", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val snapshot = parseProviderDashboard(response)
                ProviderResult.Success(snapshot)
            } else {
                val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                val message = response["message"]?.jsonPrimitive?.content
                ProviderResult.Failure(
                    code = ProviderErrorCode.fromString(errorCodeStr),
                    message = message,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (pv: ProtocolViolation) {
            ProviderResult.Failure(
                code = ProviderErrorCode.PROTOCOL_VIOLATION,
                message = "${pv.field}: ${pv.violation}",
                cause = pv,
            )
        } catch (t: Throwable) {
            ProviderResult.Failure(
                code = ProviderErrorCode.NETWORK_ERROR,
                message = t.message,
                cause = t,
            )
        }
    }

    private fun parseProviderContext(json: JsonObject): ProviderContext {
        return ProviderContext(
            providerId = json.requireUuid("provider_id"),
            capability = ProviderCapability.fromString(json.requireString("capability")),
            operationalState = ProviderOperationalState.fromString(json.requireString("operational_state")),
            currentWorkId = json.optionalUuid("current_work_id"),
            lastStatusChangeAt = json.requireInstant("last_status_change_at"),
            deviceBatteryPct = json["device_battery_pct"]?.jsonPrimitive?.intOrNull,
            networkClass = json.optionalString("network_class"),
            appVersion = json.requireString("app_version"),
            telemetrySessionId = json.requireUuid("telemetry_session_id"),
        )
    }

    private fun parseProviderDashboard(json: JsonObject): ProviderDashboard {
        val contextJson = json["context"]?.jsonObject
            ?: throw ProtocolViolation("context", "missing context in dashboard")
        val performanceJson = json["performance"]?.jsonObject
            ?: throw ProtocolViolation("performance", "missing performance in dashboard")
        val balanceJson = json["balance"]?.jsonObject
            ?: throw ProtocolViolation("balance", "missing balance in dashboard")
        val notificationsArray = json["notifications"]?.jsonArray

        val context = parseProviderContext(contextJson)
        val performance = ProviderPerformance(
            ratingAverage = performanceJson.requireDouble("rating_average"),
            totalRatingsCount = performanceJson["total_ratings_count"]?.jsonPrimitive?.intOrNull ?: 0,
            acceptanceRatePct = performanceJson.requireDouble("acceptance_rate_pct"),
            cancellationRatePct = performanceJson.requireDouble("cancellation_rate_pct"),
            totalCompletedOrders = performanceJson["total_completed_orders"]?.jsonPrimitive?.intOrNull ?: 0,
            trustTier = performanceJson.requireString("trust_tier"),
        )
        val balance = ProviderBalance(
            currencyCode = balanceJson.requireString("currency_code"),
            withdrawableMinor = balanceJson.requireLong("withdrawable_minor"),
            pendingMinor = balanceJson.requireLong("pending_minor"),
            lockedMinor = balanceJson.requireLong("locked_minor"),
            lastPayoutMinor = balanceJson.optionalLong("last_payout_minor") ?: 0L,
            lastPayoutAt = balanceJson.optionalInstant("last_payout_at"),
            totalSettledMinor = balanceJson.requireLong("total_settled_minor"),
        )
        val notifications = notificationsArray?.map { elem ->
            val n = elem.jsonObject
            ProviderNotification(
                notificationId = n.requireUuid("notification_id"),
                category = ProviderNotificationCategory.fromString(n.requireString("category")),
                title = n.requireString("title"),
                body = n.requireString("body"),
                deepLinkUri = n.optionalString("deep_link_uri"),
                isRead = n["is_read"]?.jsonPrimitive?.booleanOrNull ?: false,
                createdAt = n.requireInstant("created_at"),
            )
        } ?: emptyList()

        return ProviderDashboard(
            context = context,
            performance = performance,
            balance = balance,
            preferences = ProviderPreferences(),
            recentNotifications = notifications,
            snapshotGeneratedAt = json.optionalInstant("server_timestamp") ?: Instant.now(),
        )
    }
}
