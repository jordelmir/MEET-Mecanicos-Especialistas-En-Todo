package com.elysium369.meet.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class HealthAnomaly(
    val pid: String,
    val insight: String,
    val riskLevel: Float = 0.5f, // 0.0 to 1.0
    val severity: String = "MODERADO" // INFORMATIVO, MODERADO, CRÍTICO
)

data class DiagnosticResult(
    val analysisText: String,
    val anomalousPids: List<String> = emptyList(),
    val confidence: Float = 1.0f
)

data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String
)

class GeminiDiagnostic(
    private var apiKey: String? = null,
    private var customEndpointUrl: String? = null,
    private var provider: String = "gemini" // gemini, openai, anthropic, ollama, custom
) {
    
    private val defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    fun updateConfig(newApiKey: String?, newEndpoint: String?) {
        apiKey = newApiKey
        customEndpointUrl = newEndpoint
    }

    fun updateConfig(newApiKey: String?, newEndpoint: String?, newProvider: String) {
        apiKey = newApiKey
        customEndpointUrl = newEndpoint
        provider = newProvider
    }

    /** Whether this provider uses OpenAI-compatible request/response format */
    private fun isOpenAiFormat(): Boolean = provider in listOf("openai", "anthropic", "ollama", "custom")

    suspend fun analyzeDtc(
        dtcList: List<String>, 
        vehicleInfo: String, 
        liveData: Map<String, String>,
        telemetryHistory: Map<String, List<Float>> = emptyMap()
    ): DiagnosticResult {
        return withContext(Dispatchers.IO) {
            if (dtcList.isEmpty() && telemetryHistory.isEmpty()) {
                return@withContext DiagnosticResult("No se encontraron datos para analizar.")
            }
            
            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            if (!hasValidKey && !isCustomEndpoint) {
                return@withContext DiagnosticResult(runFallbackDiagnosis(dtcList))
            }

            val endpoint = if (isCustomEndpoint) (customEndpointUrl ?: return@withContext DiagnosticResult(runFallbackDiagnosis(dtcList))) else "$defaultEndpoint?key=$apiKey"
            
            val telemetrySummary = telemetryHistory.map { (pid, values) ->
                val stats = if (values.isNotEmpty()) {
                    "Min: ${values.minOrNull()}, Max: ${values.maxOrNull()}, Tendencia: ${if (values.last() > values.first()) "Ascendente" else "Descendente"}"
                } else "Sin datos"
                "Sensor $pid: $stats (Muestra: ${values.takeLast(10).joinToString(", ")})"
            }.joinToString("\n")

            val prompt = """
                Eres un mecánico automotriz ELITE con 20 años de experiencia, experto en diagnóstico de alta tecnología y análisis de osciloscopio.
                
                VEHÍCULO: $vehicleInfo.
                CÓDIGOS DTC: ${if (dtcList.isEmpty()) "Ninguno" else dtcList.joinToString(", ")}.
                DATOS EN VIVO: $liveData.
                
                TELEMETRÍA ( waveforms / patrones de sensores ):
                ${telemetrySummary}
                
                TU MISIÓN:
                1. Analiza los DTCs en conjunto con la telemetría. ¿Hay una correlación? (Ej: Voltaje bajo en MAF + DTC P0101).
                2. Detecta anomalías en las gráficas: ¿Ves picos erráticos, caídas de voltaje o falta de respuesta (flatline)?
                3. Proporciona un Diagnóstico Maestro:
                   - Causas probables (foco en componentes físicos vs sensores dañados).
                   - Urgencia: CRÍTICO / MODERADO / INFORMATIVO.
                   - Plan de Acción: "Primero revisa X, luego mide Y".
                4. Costo Estimado (Latinoamérica).
                5. Seguridad: ¿Puede el usuario conducir al taller?
                
                Responde con un tono profesional, tecnológico y directo. Usa formato Markdown con emojis.
                
                IMPORTANTE: Al final de tu respuesta, DEBES incluir un bloque JSON con este formato exacto:
                ```json
                {
                  "anomalous_pids": ["010C", "0105"],
                  "confidence": 0.95
                }
                ```
                (Si no hay anomalías, devuelve una lista vacía).
            """.trimIndent()

            try {
                val response = callGemini(endpoint, prompt, isCustomEndpoint, hasValidKey)
                return@withContext parseDiagnosticResponse(response ?: runFallbackDiagnosis(dtcList))
            } catch (e: Exception) {
                return@withContext DiagnosticResult(runFallbackDiagnosis(dtcList))
            }
        }
    }
    suspend fun analyzeQuick(snapshot: String): String {
        return withContext(Dispatchers.IO) {
            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            if (!hasValidKey && !isCustomEndpoint) return@withContext "SISTEMA STANDBY"

            val endpoint = if (isCustomEndpoint) (customEndpointUrl ?: return@withContext "SISTEMA STANDBY") else "$defaultEndpoint?key=$apiKey"
            val prompt = "Analiza estos datos OBD2 y da una conclusión técnica de MÁXIMO 10 PALABRAS en español: $snapshot"
            
            try {
                val response = callGemini(endpoint, prompt, isCustomEndpoint, hasValidKey) ?: "ESTADO NOMINAL"
                return@withContext response.trim().replace(".", "").uppercase()
            } catch (e: Exception) {
                "MONITOREANDO FLUJO"
            }
        }
    }

    suspend fun chat(
        history: List<ChatMessage>,
        vehicleInfo: String,
        liveData: Map<String, String>
    ): String {
        return withContext(Dispatchers.IO) {
            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            if (!hasValidKey && !isCustomEndpoint) {
                return@withContext "Error: API Key no configurada."
            }

            val endpoint = if (isCustomEndpoint) (customEndpointUrl ?: return@withContext "Error: Endpoint no configurado.") else "$defaultEndpoint?key=$apiKey"
            
            val systemPrompt = """
                Eres el Sistema de Asistencia MEET AI (Mecánicos Especialistas En Todo). 
                Eres un ingeniero mecánico automotriz ELITE con 30 años de experiencia, especializado en telemática, redes CAN-bus y diagnóstico avanzado.
                
                CONTEXTO DEL VEHÍCULO:
                - Modelo: $vehicleInfo
                - Datos en Tiempo Real (OBD2): $liveData
                
                TUS REGLAS DE ORO:
                1. Responde siempre en español con un tono profesional, técnico pero accesible, y altamente autoritario.
                2. Si el usuario pregunta por un problema, analiza los datos en vivo proporcionados para encontrar anomalías.
                3. Usa Markdown para dar estructura a tus respuestas (listas, negritas, emojis técnicos).
                4. Mantén la seguridad como prioridad: si algo es peligroso, adviértelo con claridad.
                5. Eres parte del ecosistema MEET, por lo que conoces las funciones de la app (Scanner, DTCs, Topología, Pruebas Activas).
                
                Tu objetivo es ser el copiloto técnico definitivo para el usuario.
            """.trimIndent()

            try {
                val isCustomEndpointActive = !customEndpointUrl.isNullOrEmpty()
                val useOpenAi = isOpenAiFormat() && isCustomEndpointActive

                if (useOpenAi) {
                    // OpenAI-compatible chat format
                    val messages = JSONArray()
                    messages.put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    history.forEach { msg ->
                        messages.put(JSONObject().apply {
                            put("role", if (msg.role == "user") "user" else "assistant")
                            put("content", msg.content)
                        })
                    }
                    val body = JSONObject().apply {
                        put("messages", messages)
                        put("max_tokens", 4096)
                        put("temperature", 0.7)
                    }
                    val response = callApiRaw(endpoint, body)
                    return@withContext parseOpenAiResponse(response) ?: "Lo siento, hubo un error al procesar tu mensaje."
                } else {
                    // Gemini native chat format
                    val contents = JSONArray()
                    if (history.isEmpty()) {
                        contents.put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", "Instrucciones de Sistema: $systemPrompt\n\nUsuario: Hola, necesito ayuda.")))
                        })
                    } else {
                        contents.put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", "Instrucciones de Sistema: $systemPrompt")))
                        })
                        contents.put(JSONObject().apply {
                            put("role", "model")
                            put("parts", JSONArray().put(JSONObject().put("text", "Entendido. Soy el asistente MEET AI. ¿En qué puedo ayudarte con el $vehicleInfo hoy?")))
                        })
                        history.forEach { msg ->
                            contents.put(JSONObject().apply {
                                put("role", if (msg.role == "user") "user" else "model")
                                put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                            })
                        }
                    }
                    val body = JSONObject().apply { put("contents", contents) }
                    val response = callApiRaw(endpoint, body)
                    return@withContext parseGeminiResponse(response) ?: "Lo siento, hubo un error al procesar tu mensaje."
                }
            } catch (e: Exception) {
                return@withContext "Error de conexión: ${e.message}"
            }
        }
    }

    /** Generic HTTP POST that returns the parsed JSON response */
    private suspend fun callApiRaw(endpoint: String, body: JSONObject): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                // Auth headers
                val hasKey = !apiKey.isNullOrEmpty()
                val isCustom = !customEndpointUrl.isNullOrEmpty()
                if (isCustom && hasKey) {
                    if (provider == "anthropic") {
                        connection.setRequestProperty("x-api-key", apiKey)
                        connection.setRequestProperty("anthropic-version", "2023-06-01")
                    } else {
                        connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                }
                connection.doOutput = true
                
                connection.outputStream.use { os ->
                    val input = body.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                if (connection.responseCode in 200..299) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    return@withContext JSONObject(response)
                }
                JSONObject()
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun checkHealth(
        vehicleInfo: String,
        telemetryHistory: Map<String, List<Float>>
    ): List<HealthAnomaly> {
        return withContext(Dispatchers.IO) {
            if (telemetryHistory.isEmpty()) return@withContext emptyList()
            
            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            if (!hasValidKey && !isCustomEndpoint) return@withContext emptyList()

            val endpoint = if (isCustomEndpoint) (customEndpointUrl ?: return@withContext emptyList()) else "$defaultEndpoint?key=$apiKey"
            
            val telemetrySummary = telemetryHistory.map { (pid, values) ->
                val stats = if (values.isNotEmpty()) {
                    "Min: ${values.minOrNull()}, Max: ${values.maxOrNull()}, Avg: ${values.average()}"
                } else "Sin datos"
                "Sensor $pid: $stats"
            }.joinToString("\n")

            val prompt = """
                ANÁLISIS DE SALUD PREVENTIVO (MODO MAESTRO MECÁNICO)
                VEHÍCULO: $vehicleInfo
                TELEMETRÍA:
                $telemetrySummary
                
                TU TAREA:
                Analiza las tendencias y patrones de los sensores. Busca comportamientos que indiquen desgaste prematuro, fugas de vacío, fallas de encendido incipientes o degradación de componentes ANTES de que se genere un DTC.
                
                Responde ÚNICAMENTE con un bloque JSON estrictamente formateado dentro de triple comillas invertidas:
                ```json
                {
                  "anomalias": [
                    {
                      "pid": "010C",
                      "insight": "Fluctuación inestable en ralentí, posible fuga de vacío",
                      "riskLevel": 0.85,
                      "severity": "CRÍTICO"
                    }
                  ]
                }
                ```
                Si no hay anomalías, responde: {"anomalias": []}
            """.trimIndent()

            try {
                val response = callGemini(endpoint, prompt, isCustomEndpoint, hasValidKey) ?: return@withContext emptyList()
                val jsonStr = extractJsonFromText(response) ?: return@withContext emptyList()
                
                val resultObj = JSONObject(jsonStr)
                val anomaliesArr = resultObj.getJSONArray("anomalias")
                val results = mutableListOf<HealthAnomaly>()
                for (i in 0 until anomaliesArr.length()) {
                    val item = anomaliesArr.getJSONObject(i)
                    results.add(HealthAnomaly(
                        pid = item.getString("pid"),
                        insight = item.getString("insight"),
                        riskLevel = item.optDouble("riskLevel", 0.5).toFloat(),
                        severity = item.optString("severity", "MODERADO")
                    ))
                }
                return@withContext results
            } catch (e: Exception) {
                // Silent failure for background check
            }
            emptyList<HealthAnomaly>()
        }
    }

    private suspend fun callGemini(endpoint: String, prompt: String, isCustom: Boolean, hasKey: Boolean): String? {
        return withContext(Dispatchers.IO) {
            val useOpenAiFormat = isOpenAiFormat() && isCustom
            
            val jsonBody = if (useOpenAiFormat) {
                // OpenAI-compatible format (works with OpenAI, Ollama, LM Studio, vLLM, etc.)
                JSONObject().apply {
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("max_tokens", 4096)
                    put("temperature", 0.7)
                }
            } else {
                // Gemini native format
                JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    ))
                }
            }

            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                if (isCustom && hasKey) {
                    if (provider == "anthropic") {
                        connection.setRequestProperty("x-api-key", apiKey)
                        connection.setRequestProperty("anthropic-version", "2023-06-01")
                    } else {
                        connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                }
                connection.doOutput = true
                
                connection.outputStream.use { os ->
                    val input = jsonBody.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                if (connection.responseCode in 200..299) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    
                    return@withContext if (useOpenAiFormat) {
                        parseOpenAiResponse(jsonResponse)
                    } else {
                        parseGeminiResponse(jsonResponse)
                    }
                }
                null
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Parse Gemini native response format: candidates[0].content.parts[0].text */
    private fun parseGeminiResponse(json: JSONObject): String? {
        if (json.has("candidates")) {
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
        }
        return null
    }

    /** Parse OpenAI-compatible response format: choices[0].message.content */
    private fun parseOpenAiResponse(json: JSONObject): String? {
        // Standard OpenAI format
        if (json.has("choices")) {
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                return choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }
        // Anthropic format fallback
        if (json.has("content")) {
            val content = json.getJSONArray("content")
            if (content.length() > 0) {
                return content.getJSONObject(0).getString("text")
            }
        }
        return null
    }

    private fun parseDiagnosticResponse(rawText: String): DiagnosticResult {
        val jsonStr = extractJsonFromText(rawText)
        var anomalousPids = emptyList<String>()
        var confidence = 1.0f
        
        if (jsonStr != null) {
            try {
                val obj = JSONObject(jsonStr)
                val pidsArr = obj.optJSONArray("anomalous_pids")
                if (pidsArr != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until pidsArr.length()) {
                        list.add(pidsArr.getString(i))
                    }
                    anomalousPids = list
                }
                confidence = obj.optDouble("confidence", 1.0).toFloat()
            } catch (_: Exception) {}
        }
        
        // Remove the JSON block and any triple backticks from the final display text
        val cleanText = rawText.replace(Regex("```json[\\s\\S]*?```"), "").trim()
        
        return DiagnosticResult(cleanText, anomalousPids, confidence)
    }

    private fun extractJsonFromText(text: String): String? {
        val pattern = Pattern.compile("```json([\\s\\S]*?)```")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        // Fallback to finding first { and last }
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1)
        }
        return null
    }

    private fun runFallbackDiagnosis(dtcList: List<String>): String {
        val sb = StringBuilder()
        sb.append("⚠️ MODO OFFLINE/FALLBACK ACTIVADO\n\n")
        dtcList.forEach { code ->
            val desc = com.elysium369.meet.core.obd.DtcDecoder.getLocalDescription(code)
            sb.append("🔹 Código $code: $desc\n")
        }
        return sb.toString()
    }
}

