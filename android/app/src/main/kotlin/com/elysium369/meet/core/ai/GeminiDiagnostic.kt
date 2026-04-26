package com.elysium369.meet.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiDiagnostic(
    private var apiKey: String? = null,
    private var customEndpointUrl: String? = null // Allow open source / custom endpoints
) {
    
    // Configurable endpoint (defaults to Google Gemini Flash, but can be a local LLM API)
    private val defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    fun updateConfig(newApiKey: String?, newEndpoint: String?) {
        apiKey = newApiKey
        customEndpointUrl = newEndpoint
    }

    suspend fun analyzeDtc(
        dtcList: List<String>, 
        vehicleInfo: String, 
        liveData: Map<String, String>
    ): String {
        return withContext(Dispatchers.IO) {
            if (dtcList.isEmpty()) return@withContext "No se encontraron códigos de falla."
            
            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            // Si no hay API KEY y tampoco un endpoint custom libre, hacemos Fallback Inmediato
            if (!hasValidKey && !isCustomEndpoint) {
                return@withContext runFallbackDiagnosis(dtcList)
            }

            val endpoint = if (isCustomEndpoint) customEndpointUrl!! else "$defaultEndpoint?key=$apiKey"
            
            val prompt = """
                Eres un mecánico automotriz experto con 20 años de experiencia en Latinoamérica. 
                El vehículo es: $vehicleInfo.
                Códigos DTC activos: ${dtcList.joinToString(", ")}.
                Datos en vivo relevantes: $liveData.
                
                Responde en español con:
                1. Diagnóstico probable para cada código (2-3 líneas)
                2. Urgencia: CRÍTICO / MODERADO / INFORMATIVO
                3. Componentes que revisar primero
                4. Costo estimado de reparación en dólares (rango típico Latinoamérica)
                5. ¿Puede seguir manejando? Sí / No / Con precaución
            """.trimIndent()

            try {
                // Assuming Gemini API format by default, but this could be adapted
                // for OpenAI format (Ollama, LM Studio) if isCustomEndpoint is true.
                val jsonBody = JSONObject().apply {
                    put("contents", listOf(
                        mapOf("parts" to listOf(mapOf("text" to prompt)))
                    ))
                }

                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                if (isCustomEndpoint && hasValidKey) {
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
                    
                    // Simple parsing for Gemini format
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
                    return@withContext "Respuesta recibida pero formato no reconocido: $response"
                } else {
                    // Fail gracefully and use fallback
                    val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    println("API Error: ${connection.responseCode} - $errorStream")
                    return@withContext runFallbackDiagnosis(dtcList)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext runFallbackDiagnosis(dtcList)
            }
        }
    }

    private fun runFallbackDiagnosis(dtcList: List<String>): String {
        val sb = StringBuilder()
        sb.append("⚠️ MODO OFFLINE/FALLBACK ACTIVADO (Sin IA conectada)\n\n")
        
        dtcList.forEach { code ->
            val desc = com.elysium369.meet.core.obd.DtcDecoder.getLocalDescription(code)
            sb.append("🔹 Código $code: $desc\n")
        }
        
        sb.append("\nNota: Para obtener un diagnóstico experto con costos y componentes sugeridos, configura tu API Key en los ajustes.")
        return sb.toString()
    }
}
