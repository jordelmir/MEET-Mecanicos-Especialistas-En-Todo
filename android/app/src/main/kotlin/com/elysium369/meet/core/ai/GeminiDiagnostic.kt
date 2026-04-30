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

class GeminiDiagnostic(
    private var apiKey: String? = null,
    private var customEndpointUrl: String? = null
) {
    
    private val defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    fun updateConfig(newApiKey: String?, newEndpoint: String?) {
        apiKey = newApiKey
        customEndpointUrl = newEndpoint
    }

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

            val endpoint = if (isCustomEndpoint) customEndpointUrl!! else "$defaultEndpoint?key=$apiKey"
            
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

    suspend fun checkHealth(
        vehicleInfo: String,
        telemetryHistory: Map<String, List<Float>>
    ): List<HealthAnomaly> {
        return withContext(Dispatchers.IO) {
            if (telemetryHistory.isEmpty()) return@withContext emptyList()
            
            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            if (!hasValidKey && !isCustomEndpoint) return@withContext emptyList()

            val endpoint = if (isCustomEndpoint) customEndpointUrl!! else "$defaultEndpoint?key=$apiKey"
            
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
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
            }

            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (isCustom && hasKey) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.doOutput = true
            
            connection.outputStream.use { os ->
                val input = jsonBody.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                
                if (jsonResponse.has("candidates")) {
                    val candidates = jsonResponse.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).getString("text")
                        }
                    }
                }
            }
            null
        }
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

