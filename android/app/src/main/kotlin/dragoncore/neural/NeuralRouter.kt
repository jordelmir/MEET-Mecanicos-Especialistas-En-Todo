package dragoncore.neural

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 🐉 NeuralRouter V7 [STABILIZED EDITION]
 *
 * Resuelve el error 500 ajustando la temperatura a 0.15f y usando una cascada estable.
 * Implementa Omniscience V7 para orquestación de módulos.
 */
data class AIResponse(
    val moduleType: String,
    val dragonCode: String,
    val explanation: String,
    val sourceEngine: String
)

class NeuralRouter @javax.inject.Inject constructor(
    private val context: android.content.Context,
    private val vault: dragoncore.security.NeuralVault
) {
    private val TAG = "DragonNeuralRouter"

    // 💥 CASCADA REALISTA DE SUPERVIVENCIA V7 💥
    private val geminiCascade = listOf("gemini-2.0-flash", "gemini-1.5-pro-latest", "gemini-1.5-flash-latest")
    private val openaiCascade = listOf("gpt-4o-mini", "gpt-4o")
    private val anthropicCascade = listOf("claude-3-5-sonnet-latest")

    suspend fun processIntent(intent: String, cellTypeStr: String): Result<AIResponse> = withContext(Dispatchers.IO) {
        val geminiKey = vault.getApiKey("gemini")
        val openAiKey = vault.getApiKey("openai")
        val anthropicKey = vault.getApiKey("anthropic")

        if (geminiKey.isNullOrEmpty() && openAiKey.isNullOrEmpty() && anthropicKey.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("Ingresa al menos una API Key en la configuración."))
        }

        val systemInstructionText = PhrontMaster.getSystemInstruction()
        var lastException: Exception? = null

        // 1. INTENTO CON GEMINI (Prioridad: Nativo)
        if (!geminiKey.isNullOrEmpty()) {
            for (modelName in geminiCascade) {
                try {
                    Log.i(TAG, "Intentando ignición con modelo: [$modelName]")

                    // 💥 1. HACK DE COMPATIBILIDAD GEMINI 2.0+ 💥
                    // Eliminamos el parámetro 'systemInstruction' que causa el crash.
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = geminiKey,
                        // systemInstruction = content { text(systemInstructionText) }, <-- ¡ELIMINADO!
                        generationConfig = generationConfig {
                            temperature = 0.1f
                            topK = 1
                        }
                    )

                    // 💥 2. FUSIÓN DE CONTEXTO (PROMPT PREPENDING) 💥
                    // Engañamos al modelo pasándole las reglas dentro de su misma entrada de texto.
                    val hackerPrompt = """
                        [INSTRUCCIONES ESTRICTAS DEL SISTEMA]
                        $systemInstructionText

                        [CONTEXTO DEL DOCUMENTO Y PETICIÓN]
                        $intent
                    """.trimIndent()

                    val response = model.generateContent(hackerPrompt)
                    val rawText = response.text ?: throw Exception("El servidor devolvió vacío.")

                    Log.i(TAG, "Éxito con [$modelName]")
                    return@withContext Result.success(parseSafeXml(rawText, modelName))
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "Proveedor de IA no disponible")
                    if (e.message?.contains("api key", ignoreCase = true) == true) break
                }
            }
        }

        // 2. FALLBACK A ANTHROPIC (Prioridad: Razonamiento)
        if (!anthropicKey.isNullOrEmpty()) {
            for (modelName in anthropicCascade) {
                try {
                    Log.i(TAG, "Fallback: Intentando Anthropic con [$modelName]")
                    val responseText = callAnthropic(anthropicKey, modelName, systemInstructionText, intent)
                    Log.i(TAG, "Éxito con Anthropic [$modelName]")
                    return@withContext Result.success(parseSafeXml(responseText, modelName))
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "Proveedor de IA no disponible")
                }
            }
        }

        // 3. FALLBACK A OPENAI (Prioridad: Disponibilidad)
        if (!openAiKey.isNullOrEmpty()) {
            for (modelName in openaiCascade) {
                try {
                    Log.i(TAG, "Fallback: Intentando OpenAI con [$modelName]")
                    val responseText = callOpenAi(openAiKey, modelName, systemInstructionText, intent)
                    Log.i(TAG, "Éxito con OpenAI [$modelName]")
                    return@withContext Result.success(parseSafeXml(responseText, modelName))
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "Proveedor de IA no disponible")
                }
            }
        }

        return@withContext Result.failure(Exception(
            "Los proveedores de IA no están disponibles en este momento."
        ))
    }

    private fun callOpenAi(key: String, model: String, system: String, prompt: String): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = """
            {
                "model": "$model",
                "messages": [
                    {"role": "system", "content": ${escapeJson(system)}},
                    {"role": "user", "content": ${escapeJson(prompt)}}
                ],
                "temperature": 0.1
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)
        val request = okhttp3.Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("OpenAI Error: ${response.code}")
            val bodyStr = response.body?.string() ?: throw Exception("OpenAI Empty Response")
            // Parsing mínimo para extraer el contenido (JSON simple)
            return bodyStr.split("\"content\": \"")[1].split("\"")[0].replace("\\n", "\n").replace("\\\"", "\"")
        }
    }

    private fun callAnthropic(key: String, model: String, system: String, prompt: String): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = """
            {
                "model": "$model",
                "max_tokens": 1024,
                "system": ${escapeJson(system)},
                "messages": [
                    {"role": "user", "content": ${escapeJson(prompt)}}
                ]
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)
        val request = okhttp3.Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Anthropic Error: ${response.code}")
            val bodyStr = response.body?.string() ?: throw Exception("Anthropic Empty Response")
            return bodyStr.split("\"text\": \"")[1].split("\"")[0].replace("\\n", "\n").replace("\\\"", "\"")
        }
    }

    private fun escapeJson(s: String): String {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
    }

    private fun parseSafeXml(rawText: String, engineUsed: String): AIResponse {
        val cleanText = rawText.replace("```xml", "").replace("```", "").trim()

        val typeMatch = "<MODULE_TYPE>(.*?)</MODULE_TYPE>".toRegex(RegexOption.DOT_MATCHES_ALL).find(cleanText)
        val codeMatch = "<DRAGON_CODE>(.*?)</DRAGON_CODE>".toRegex(RegexOption.DOT_MATCHES_ALL).find(cleanText)
        val expMatch = "<EXPLANATION>(.*?)</EXPLANATION>".toRegex(RegexOption.DOT_MATCHES_ALL).find(cleanText)

        val moduleType = typeMatch?.groups?.get(1)?.value?.trim()?.uppercase() ?: "MATH"
        val code = codeMatch?.groups?.get(1)?.value?.trim() ?: ""
        val explanation = expMatch?.groups?.get(1)?.value?.trim() ?: cleanText

        // Formato bonito para la UI
        val cleanEngine = engineUsed.split("-").joinToString(" ") {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(java.util.Locale.getDefault()) else char.toString() }
        }

        return AIResponse(moduleType, code, explanation, cleanEngine)
    }

    // Validación de API Key [🔑]
    suspend fun validateGeminiKey(key: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash-latest",
                apiKey = key,
                generationConfig = generationConfig {
                    temperature = 0.0f
                }
            )
            val response = model.generateContent("ping")
            if (response.text?.isNotBlank() == true) Result.success("gemini-1.5-flash")
            else Result.failure(Exception("Servidor no respondió. Verifica tu conexión."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Legacy method for compatibility during transition
    suspend fun processMathIntent(intent: String): Result<String> = withContext(Dispatchers.IO) {
        val res = processIntent(intent, "MATH")
        if (res.isSuccess) Result.success(res.getOrThrow().dragonCode)
        else Result.failure(res.exceptionOrNull() ?: Exception("Unknown Error"))
    }
}
