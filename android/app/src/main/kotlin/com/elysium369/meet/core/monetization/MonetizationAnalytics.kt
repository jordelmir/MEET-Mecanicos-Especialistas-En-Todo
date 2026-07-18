package com.elysium369.meet.core.monetization

import android.content.Context
import android.util.Log
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Date
import java.util.UUID

@Serializable
data class AnalyticsEventRow(
    val id: String,
    val event_name: String,
    val anonymous_id: String,
    val user_id: String?,
    val session_id: String,
    val event_timestamp: String,
    val app_version: String,
    val route: String?,
    val properties: JsonObject
)

object MonetizationAnalytics {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var anonymousId: String = ""
    private val sessionId: String = UUID.randomUUID().toString()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("meet_analytics_prefs", Context.MODE_PRIVATE)
        anonymousId = prefs.getString("anon_id", null) ?: UUID.randomUUID().toString().also { newId ->
            prefs.edit().putString("anon_id", newId).apply()
        }
    }

    fun logEvent(
        eventName: String,
        route: String? = null,
        propertiesBuilder: JsonObjectBuilder.() -> Unit = {}
    ) {
        scope.launch {
            try {
                val userId = try {
                    SupabaseModule.client.auth.currentUserOrNull()?.id
                } catch (e: Exception) {
                    null
                }

                val properties = buildJsonObject {
                    val builder = JsonObjectBuilder(this)
                    builder.propertiesBuilder()
                }

                // Security filter: prevent VIN / GPS / OBD leak
                validateProperties(properties)

                val event = AnalyticsEventRow(
                    id = UUID.randomUUID().toString(),
                    event_name = eventName,
                    anonymous_id = anonymousId,
                    user_id = userId,
                    session_id = sessionId,
                    event_timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(Date()),
                    app_version = BuildConfig.VERSION_NAME,
                    route = route,
                    properties = properties
                )

                SupabaseModule.client.postgrest["analytics_events"].insert(event)
                Log.d("MonetizationAnalytics", "Log event: $eventName uploaded.")
            } catch (e: Exception) {
                Log.e("MonetizationAnalytics", "Failed to log event: $eventName", e)
            }
        }
    }

    private fun validateProperties(props: JsonObject) {
        val sensitiveKeys = listOf("vin", "plate", "gps", "location", "chat", "token", "password", "key", "obd")
        for (key in props.keys) {
            if (sensitiveKeys.any { key.lowercase().contains(it) }) {
                throw IllegalArgumentException("Security Block: Sensitive commercial/diagnostic data ($key) detected in analytics payload! Blocked to prevent data leaks.")
            }
        }
    }

    class JsonObjectBuilder(private val builder: kotlinx.serialization.json.JsonObjectBuilder) {
        fun put(key: String, value: String) = builder.put(key, value)
        fun put(key: String, value: Number) = builder.put(key, value)
        fun put(key: String, value: Boolean) = builder.put(key, value)
    }
}
