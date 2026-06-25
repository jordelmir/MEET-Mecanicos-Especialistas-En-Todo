package com.elysium369.meet.core.billing

import android.content.Context
import com.elysium369.meet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class VerifiedEntitlement(
    val status: String,
    val entitlementKey: String,
    val expiresAt: String?
)

class GooglePlayPurchaseVerifier(
    private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verify(
        productId: String,
        productType: String,
        purchaseToken: String
    ): Result<VerifiedEntitlement> = withContext(Dispatchers.IO) {
        runCatching {
            val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
            val supabaseKey = BuildConfig.SUPABASE_KEY
            require(supabaseUrl.isNotBlank() && supabaseKey.isNotBlank()) {
                "Supabase no está configurado para verificar compras."
            }

            val anonymousId = context.getSharedPreferences("meet_billing", Context.MODE_PRIVATE)
                .getString("anonymous_billing_id", null)
                ?: java.util.UUID.randomUUID().toString().also { generated ->
                    context.getSharedPreferences("meet_billing", Context.MODE_PRIVATE)
                        .edit()
                        .putString("anonymous_billing_id", generated)
                        .apply()
                }

            val body = """
                {
                  "productId": ${productId.quoteJson()},
                  "productType": ${productType.quoteJson()},
                  "purchaseToken": ${purchaseToken.quoteJson()},
                  "anonymousId": ${anonymousId.quoteJson()}
                }
            """.trimIndent()

            val connection = (URL("$supabaseUrl/functions/v1/verify-google-play-purchase").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", supabaseKey)
                setRequestProperty("Authorization", "Bearer $supabaseKey")
            }

            OutputStreamWriter(connection.outputStream).use { it.write(body) }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Verificación Google Play falló (${connection.responseCode}): $errorBody")
            }

            val obj = json.parseToJsonElement(responseText).jsonObject
            VerifiedEntitlement(
                status = obj["status"]?.jsonPrimitive?.content.orEmpty(),
                entitlementKey = obj["entitlement_key"]?.jsonPrimitive?.content.orEmpty(),
                expiresAt = obj["expires_at"]?.jsonPrimitive?.content
            )
        }
    }

    private fun String.quoteJson(): String {
        return buildString {
            append('"')
            this@quoteJson.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }
}

