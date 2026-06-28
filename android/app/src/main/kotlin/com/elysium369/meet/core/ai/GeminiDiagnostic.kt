package com.elysium369.meet.core.ai

import android.util.Log
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
                # SISTEMA DE DIAGNÓSTICO MAESTRO ELYSIUM VANGUARD AI (NIVEL ELITE)
                
                Eres un Ingeniero de Diagnóstico Automotriz con Certificación Master L1 (ASE) y 25 años de experiencia liderando talleres de alta gama. 
                Tu conocimiento abarca desde mecánica clásica hasta sistemas híbridos/eléctricos complejos y protocolos de red CAN-FD.
                
                ## CONTEXTO TÉCNICO:
                - **VEHÍCULO:** $vehicleInfo.
                - **CÓDIGOS DTC DETECTADOS:** ${if (dtcList.isEmpty()) "NINGUNO (Análisis Preventivo)" else dtcList.joinToString(", ")}.
                - **DATOS EN VIVO (Snapshots):** $liveData.
                
                ## ANÁLISIS DE TELEMETRÍA (WAVEFORMS/TENDENCIAS):
                ${if (telemetrySummary.isBlank()) "No se proporcionó historial de telemetría." else telemetrySummary}
                
                ## TU MISIÓN CRÍTICA:
                Realiza un diagnóstico profundo e integral siguiendo estos pilares:
                
                1. **Correlación de Datos:** No analices los DTCs de forma aislada. Cruza los DTCs con los datos en vivo y la telemetría. (Ej: Si hay P0171, busca valores de Fuel Trim y presión de combustible).
                2. **Detección de Anomalías en Señal:** Identifica patrones anormales en las gráficas (ruido eléctrico, falta de respuesta, voltajes fuera de rango teórico).
                3. **DIAGNÓSTICO MAESTRO:** Explica la "Causa Raíz" más probable. Diferencia claramente entre un componente fallido, un problema de cableado/red, o una falla mecánica interna.
                
                ## FORMATO DE RESPUESTA REQUERIDO (WOW FACTOR):
                Responde en **ESPAÑOL** con estructura Markdown impecable:
                
                - **RESUMEN EJECUTIVO:** (Usa emojis técnicos: 🛡️, ⚙️, ⚡).
                - **ANÁLISIS TÉCNICO:** Detalle de por qué los datos indican esa falla.
                - **GUÍA PASO A PASO (PROCEDIMIENTO):**
                    1. Acción inmediata (Seguridad).
                    2. Prueba específica con multímetro/osciloscopio.
                    3. Inspección física recomendada.
                - **NIVEL DE RIESGO:** (CRÍTICO / MODERADO / INFORMATIVO).
                - **ESTIMACIÓN DE REPARACIÓN:** (Rango de costo en USD y complejidad).
                - **RECOMENDACIÓN FINAL:** ¿Es seguro conducir?
                
                ---
                **IMPORTANTE:** Al final de tu respuesta, DEBES incluir un bloque JSON con este formato exacto para que el sistema Elysium Vanguard pueda procesar los datos:
                ```json
                {
                  "anomalous_pids": ["010C", "0105"],
                  "confidence": 0.98,
                  "urgency": "CRITICAL"
                }
                ```
                (Si no hay anomalías, devuelve una lista vacía en "anomalous_pids").
            """.trimIndent()

            try {
                val response = callGemini(endpoint, prompt, isCustomEndpoint, hasValidKey)
                if (response == null) {
                    return@withContext DiagnosticResult(runFallbackDiagnosis(dtcList))
                }
                return@withContext parseDiagnosticResponse(response)
            } catch (e: Exception) {
                Log.e("GeminiDiag", "Error in AI Analysis", e)
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
                Eres el Sistema de Asistencia Elysium Vanguard AI (Motor de Diagnóstico Inteligente). 
                Eres un ingeniero mecánico automotriz ELITE con 30 años de experiencia, especializado en telemática, redes CAN-bus y diagnóstico avanzado.
                
                CONTEXTO DEL VEHÍCULO:
                - Modelo: $vehicleInfo
                - Datos en Tiempo Real (OBD2): $liveData
                
                TUS REGLAS DE ORO:
                1. Responde siempre en español con un tono profesional, técnico pero accesible, y altamente autoritario.
                2. Si el usuario pregunta por un problema, analiza los datos en vivo proporcionados para encontrar anomalías.
                3. Usa Markdown para dar estructura a tus respuestas (listas, negritas, emojis técnicos).
                4. Mantén la seguridad como prioridad: si algo es peligroso, adviértelo con claridad.
                5. Eres parte del ecosistema Elysium Vanguard, por lo que conoces las funciones de la app (Scanner, DTCs, Topología, Pruebas Activas).
                
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
                            put("parts", JSONArray().put(JSONObject().put("text", "Entendido. Soy el asistente Elysium Vanguard AI. ¿En qué puedo ayudarte con el $vehicleInfo hoy?")))
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
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e("GeminiDiag", "HTTP Error in callApiRaw response code: ${connection.responseCode}, body: $errorResponse")
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

    suspend fun analyzeLiveTelemetry(
        vehicleInfo: String,
        oscilloscopeData: Map<String, List<Pair<Long, Float>>>
    ): DiagnosticResult {
        return withContext(Dispatchers.IO) {
            if (oscilloscopeData.isEmpty()) {
                return@withContext DiagnosticResult("No hay datos de telemetría para analizar.")
            }

            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            if (!hasValidKey && !isCustomEndpoint) {
                return@withContext DiagnosticResult("Modo Offline: Se requiere conexión a AI para análisis de osciloscopio.")
            }

            val endpoint = if (isCustomEndpoint) (customEndpointUrl ?: return@withContext DiagnosticResult("Error")) else "$defaultEndpoint?key=$apiKey"
            
            val telemetrySummary = oscilloscopeData.map { (pid, dataPoints) ->
                val values = dataPoints.map { it.second }
                val stats = if (values.isNotEmpty()) {
                    "Min: ${values.minOrNull()}, Max: ${values.maxOrNull()}, Avg: ${String.format("%.2f", values.average())}, Variación: ${String.format("%.2f", values.maxOrNull()!! - values.minOrNull()!!)}"
                } else "Sin datos"
                
                // Sample some points for the AI (e.g., evenly spaced to show the wave)
                val sampleSize = minOf(20, values.size)
                val step = if (sampleSize > 0) values.size / sampleSize else 1
                val sampledValues = values.filterIndexed { index, _ -> index % step == 0 }.take(sampleSize).map { String.format("%.2f", it) }
                
                "Sensor $pid: $stats\nSecuencia de muestra (forma de onda): [${sampledValues.joinToString(", ")}]"
            }.joinToString("\n\n")

            val prompt = """
                # SISTEMA DE ANÁLISIS DE OSCILOSCOPIO Y TELEMETRÍA DE ALTA FRECUENCIA
                
                Eres un Especialista en Diagnóstico Automotriz Avanzado con osciloscopio y analizador de motores.
                
                ## CONTEXTO TÉCNICO:
                - **VEHÍCULO:** $vehicleInfo.
                - **DATOS DE OSCILOSCOPIO (Alta Frecuencia):** 
                $telemetrySummary
                
                ## TU MISIÓN:
                1. **Análisis de Señal:** Analiza la "Secuencia de muestra" para determinar si la forma de onda es normal, tiene ruido, picos anómalos o caídas repentinas.
                2. **Diagnóstico del Componente:** Basado en el PID y la forma de onda (Ej: Si el PID 42/0142 es Voltaje de Batería, un rango de 13.5V a 14.5V estable es normal con el motor encendido. Si hay rizado de CA grande o caídas, el alternador o batería fallan).
                3. **Recomendaciones Reales:** Da pasos de acción precisos.
                
                ## FORMATO DE RESPUESTA:
                - **ANÁLISIS DE ONDA:** (Interpretación de la gráfica)
                - **DIAGNÓSTICO:** (Cuál es el estado del sistema)
                - **ACCIÓN RECOMENDADA:** (Qué debe revisar físicamente el mecánico)
                
                Debes incluir un bloque JSON al final con los PIDs anómalos, igual que en el diagnóstico maestro:
                ```json
                {
                  "anomalous_pids": ["0142"],
                  "confidence": 0.95,
                  "urgency": "HIGH"
                }
                ```
            """.trimIndent()

            try {
                val response = callGemini(endpoint, prompt, isCustomEndpoint, hasValidKey)
                if (response == null) {
                    return@withContext DiagnosticResult("Error en el análisis de telemetría.")
                }
                return@withContext parseDiagnosticResponse(response)
            } catch (e: Exception) {
                Log.e("GeminiDiag", "Error in Oscilloscope AI Analysis", e)
                return@withContext DiagnosticResult("Error al contactar al servidor de IA.")
            }
        }
    }

    suspend fun analyzeNetworkTopology(
        vehicleInfo: String,
        modules: List<com.elysium369.meet.core.obd.NetworkModule>
    ): DiagnosticResult {
        return withContext(Dispatchers.IO) {
            if (modules.isEmpty()) {
                return@withContext DiagnosticResult("No hay módulos de red detectados para analizar.")
            }

            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            if (!hasValidKey && !isCustomEndpoint) {
                return@withContext DiagnosticResult("Modo Offline: Se requiere conexión a AI para análisis de topología.")
            }

            val endpoint = if (isCustomEndpoint) (customEndpointUrl ?: return@withContext DiagnosticResult("Error")) else "$defaultEndpoint?key=$apiKey"
            
            val topologySummary = modules.joinToString("\n") { module ->
                val status = if (module.isAlive) "VIVO (Latencia: ${module.latencyMs}ms)" else "MUDO (SIN RESPUESTA / OFFLINE)"
                val dtcs = if (module.dtcs.isNotEmpty()) "DTCs: ${module.dtcs.joinToString(", ")}" else "Sin DTCs"
                "- Nodo 0x${module.id.uppercase()} [${module.name}] (${module.networkType}): $status, $dtcs"
            }

            val prompt = """
                # SISTEMA DE DIAGNÓSTICO EXPERTO EN REDES AUTOMOTRICES (CAN-BUS/LIN/K-LINE)
                
                Eres un Especialista en Diagnóstico de Redes de Comunicación Multiplexada (Master Diagnostician).
                
                ## CONTEXTO TÉCNICO:
                - **VEHÍCULO:** $vehicleInfo.
                - **ESTADO DE LA TOPOLOGÍA DE RED:**
                $topologySummary
                
                ## TU MISIÓN CRÍTICA:
                1. **Análisis de Integridad del Bus:** Analiza qué módulos están mudos en cada tipo de bus (CAN High, CAN Low, LIN, K-Line). 
                   - Si varios módulos de un mismo bus están mudos, deduce si hay una falla física del bus (ej: corto a tierra, corto a 12V, bus abierto).
                   - Si solo un módulo está mudo, analiza si es un problema localizado de alimentación/masa o falla del propio módulo.
                2. **Interpretación de DTCs de Comunicación (Códigos 'U'):** Correlaciona los DTCs (ej. códigos U0100, U0101, etc.) reportados por los módulos vivos para determinar con qué módulo se perdió la comunicación.
                3. **Estrategia de Diagnóstico Físico:** Indica los pines del puerto OBD-II a comprobar (ej. Pin 6 y 14 para CAN High/Low con multímetro/osciloscopio, resistencia de terminación de 60 ohms) y qué valores teóricos buscar.
                
                ## FORMATO DE RESPUESTA REQUERIDO (ESPAÑOL, MARKDOWN PREMIUM):
                - **RESUMEN DE SALUD DE RED:** (Conclusión general de la integridad de los buses)
                - **ANÁLISIS DE CAUSA RAÍZ:** (Deducción técnica de la falla)
                - **GUÍA DE PRUEBAS DE HARDWARE (OBD-II PINOUT):** (Paso a paso con multímetro/osciloscopio especificando pines exactos del conector OBD-II)
                - **RECOMENDACIÓN FINAL:** (Gravedad y pasos a seguir)
                
                Debes incluir un bloque JSON al final con los IDs de módulos anómalos o sospechosos, igual que en el diagnóstico maestro:
                ```json
                {
                  "anomalous_pids": [],
                  "confidence": 0.95,
                  "urgency": "HIGH"
                }
                ```
            """.trimIndent()

            try {
                val response = callGemini(endpoint, prompt, isCustomEndpoint, hasValidKey)
                if (response == null) {
                    return@withContext DiagnosticResult("Error en el análisis de topología.")
                }
                return@withContext parseDiagnosticResponse(response)
            } catch (e: Exception) {
                Log.e("GeminiDiag", "Error in Topology AI Analysis", e)
                return@withContext DiagnosticResult("Error al contactar al servidor de IA.")
            }
        }
    }

    suspend fun analyzeActiveTest(
        vehicleInfo: String,
        testName: String,
        testId: String,
        monitoredData: Map<String, Float>
    ): DiagnosticResult {
        return withContext(Dispatchers.IO) {
            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            if (!hasValidKey && !isCustomEndpoint) {
                return@withContext DiagnosticResult(runFallbackActiveTestDiagnosis(testId, monitoredData))
            }

            val endpoint = if (isCustomEndpoint) (customEndpointUrl ?: return@withContext DiagnosticResult("Error")) else "$defaultEndpoint?key=$apiKey"
            
            val dataSummary = monitoredData.map { (sensor, value) ->
                "- **$sensor:** $value"
            }.joinToString("\n")

            val prompt = """
                # SISTEMA DE DIAGNÓSTICO MAESTRO ELYSIUM: EVALUACIÓN DE CONTROL BIDIRECCIONAL (PRUEBA ACTIVA)
                
                Eres un Especialista de Diagnóstico Master L1 (ASE) de la división Elysium Vanguard.
                Tu tarea es evaluar el resultado de una prueba activa bidireccional en un actuador específico.
                
                ## CONTEXTO DE LA PRUEBA:
                - **Vehículo:** $vehicleInfo
                - **Prueba Activa Ejecutada:** $testName (ID: $testId)
                - **Valores Monitoreados al Finalizar:** 
                ${if (monitoredData.isEmpty()) "No se monitorearon PIDs en esta prueba." else dataSummary}
                
                ## TU MISIÓN CRÍTICA:
                1. **Evaluación de Desempeño:** Basado en la física y especificaciones de taller, ¿los valores reportados indican que el actuador respondió correctamente?
                   - *Bomba de Combustible / Balance de Inyectores:* Presión nominal debe subir a ~45-55 PSI (300-380 kPa). Si bajó drásticamente o no subió, hay fallas. En balance de inyectores, cada inyector debe causar una caída coherente y similar de presión.
                   - *Electroventiladores:* La temperatura del refrigerante debe descender si la prueba corre suficiente tiempo, o el consumo de corriente (si se mide) debe subir.
                   - *Válvulas EVAP / EGR:* Los cambios en el flujo de aire/combustible deben provocar variaciones leves en las RPM del motor o en el Trim de Combustible.
                   - *Acelerador:* Posición de mariposa debe coincidir con el comando de apertura (ej. si la prueba abre a 25%, el sensor de posición TPS debe marcar ~25%).
                2. **Veredicto Clínico:** Determinar la causa raíz (si falló el actuador, hay fuga de presión, bobina abierta, etc.) y guiar los siguientes pasos de diagnóstico de taller.
                
                ## FORMATO DE RESPUESTA REQUERIDO:
                Responde en **ESPAÑOL** con estructura Markdown:
                
                - **🛡️ VEREDICTO DE LA PRUEBA:** (Usa etiquetas claras como: COMPONENTE SANO, RESPUESTA INSUFICIENTE, FALLO DE ALIMENTACIÓN, o FUERA DE ESPECIFICACIÓN).
                - **⚙️ ANÁLISIS DE TELEMETRÍA:** Interpretación técnica detallada de los valores medidos.
                - **⚡ RECOMENDACIONES DE TALLER:** Pasos prácticos para verificar físicamente el circuito del actuador (ej. medir resistencia de bobina, verificar fusible, probar caída de voltaje).
                
                Debes incluir un bloque JSON final de la siguiente manera:
                ```json
                {
                  "anomalous_pids": [],
                  "confidence": 0.95,
                  "urgency": "NONE"
                }
                ```
            """.trimIndent()

            try {
                val response = callGemini(endpoint, prompt, isCustomEndpoint, hasValidKey)
                if (response == null) {
                    return@withContext DiagnosticResult(runFallbackActiveTestDiagnosis(testId, monitoredData))
                }
                return@withContext parseDiagnosticResponse(response)
            } catch (e: Exception) {
                Log.e("GeminiDiag", "Error in Active Test AI Analysis", e)
                return@withContext DiagnosticResult(runFallbackActiveTestDiagnosis(testId, monitoredData))
            }
        }
    }

    private fun runFallbackActiveTestDiagnosis(testId: String, monitoredData: Map<String, Float>): String {
        val dataSummary = if (monitoredData.isEmpty()) "Sin datos" else monitoredData.map { "- **${it.key}:** ${it.value}" }.joinToString("\n")
        return """
            # 🛡️ VEREDICTO DE LA PRUEBA: PRUEBA PROCESADA LOCALMENTE
            
            **ESTADO:** MODO OFFLINE / DIAGNÓSTICO INTEGRADO
            
            ## ⚙️ ANÁLISIS DE TELEMETRÍA:
            Se evaluaron las lecturas del bus para la prueba **$testId**:
            $dataSummary
            
            *Nota: En condiciones normales de operación, la señal del sensor retroalimenta la ECU según los ciclos programados del componente.*
            
            ## ⚡ RECOMENDACIONES DE TALLER:
            1. **Revisión del Actuador:** Utilice una lámpara de prueba o multímetro en las terminales del actuador para verificar que le llegue voltaje de activación (+12V o control por tierra PWM) al dar el comando.
            2. **Medición de Resistencia:** Mida la resistencia de la bobina interna del actuador (por ejemplo, electroválvula EGR o bobina de bomba). Compare contra la especificación técnica del fabricante (generalmente entre 10 y 50 ohmios para la mayoría de solenoides).
            3. **Prueba de Vacío/Presión Externa:** Si la parte eléctrica está sana pero no hay flujo, verifique obstrucciones en las mangueras de vacío (EVAP/EGR) o fugas en la línea de combustible.
        """.trimIndent()
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
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e("GeminiDiag", "HTTP Error in callGemini response code: ${connection.responseCode}, body: $errorResponse")
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
        val cleanText = rawText.replace(JSON_MARKDOWN_REGEX, "").trim()
        
        return DiagnosticResult(cleanText, anomalousPids, confidence)
    }

    private fun extractJsonFromText(text: String): String? {
        val pattern = JSON_MARKDOWN_PATTERN
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

    suspend fun analyzeServiceReset(
        vehicleInfo: String,
        resetName: String,
        resetId: String,
        manufacturer: String,
        isSuccess: Boolean
    ): DiagnosticResult {
        return withContext(Dispatchers.IO) {
            val isCustomEndpoint = !customEndpointUrl.isNullOrEmpty()
            val hasValidKey = !apiKey.isNullOrEmpty()
            
            if (!hasValidKey && !isCustomEndpoint) {
                return@withContext DiagnosticResult(runFallbackServiceResetDiagnosis(resetId, manufacturer, isSuccess))
            }

            val endpoint = if (isCustomEndpoint) (customEndpointUrl ?: return@withContext DiagnosticResult("Error")) else "$defaultEndpoint?key=$apiKey"

            val prompt = """
                # SISTEMA DE ASISTENCIA ELYSIUM: PROCEDIMIENTO Y VERIFICACIÓN DE REINICIO DE SERVICIO (SERVICE RESET)
                
                Eres un Especialista de Diagnóstico Master L1 (ASE) de la división Elysium Vanguard.
                Tu tarea es asistir en un proceso de reinicio de servicio (Service Reset) para el vehículo actual.
                
                ## CONTEXTO DEL REINICIO:
                - **Vehículo:** $vehicleInfo
                - **Fabricante Detectado:** $manufacturer
                - **Servicio:** $resetName (ID: $resetId)
                - **Estado del Proceso Automático:** ${if (isSuccess) "ÉXITO (La rutina OBD-II / UDS se completó)" else "FALLÓ o NO SOPORTADO (La rutina automática OBD-II falló o el módulo no es compatible)"}
                
                ## TU MISIÓN CRÍTICA:
                1. **Si el proceso automático fue Exitoso:**
                   - Explica los pasos de confirmación final (ej: ciclar el encendido, apagar el motor 10 segundos, revisar la pantalla del clúster).
                   - Menciona posibles precauciones o verificaciones mecánicas asociadas al servicio (ej: verificar nivel de aceite físico con varilla tras reinicio, no reiniciar aceite si no se cambió, etc.).
                2. **Si el proceso automático Falló/No es Soportado (¡CRÍTICO!):**
                   - Proporciona el **Procedimiento de Reinicio Manual (Pedal/Tablero)** específico para este fabricante ($manufacturer) y vehículo ($vehicleInfo), de forma paso a paso.
                   - Por ejemplo: Secuencia de botones del tablero, interruptor de encendido (Ignition ON/OFF) y pedales (acelerador/freno) para reiniciar el indicador de aceite, frenos, batería o TPMS de manera manual.
                
                ## FORMATO DE RESPUESTA REQUERIDO:
                Responde en **ESPAÑOL** con estructura Markdown:
                
                - **📋 INSTRUCCIONES DE VERIFICACIÓN / PROCEDIMIENTO MANUAL:** (Proporciona los pasos claros y enumerados).
                - **🛠️ RECOMENDACIONES TÉCNICAS ELYSIUM:** Consejos profesionales de taller para verificar que el sistema haya aceptado el cambio y buenas prácticas del componente.
                
                Debes incluir un bloque JSON final de la siguiente manera:
                ```json
                {
                  "anomalous_pids": [],
                  "confidence": 0.98,
                  "urgency": "NONE"
                }
                ```
            """.trimIndent()

            try {
                val response = callGemini(endpoint, prompt, isCustomEndpoint, hasValidKey)
                if (response == null) {
                    return@withContext DiagnosticResult(runFallbackServiceResetDiagnosis(resetId, manufacturer, isSuccess))
                }
                return@withContext parseDiagnosticResponse(response)
            } catch (e: Exception) {
                Log.e("GeminiDiag", "Error in Service Reset AI Analysis", e)
                return@withContext DiagnosticResult(runFallbackServiceResetDiagnosis(resetId, manufacturer, isSuccess))
            }
        }
    }

    private fun runFallbackServiceResetDiagnosis(resetId: String, manufacturer: String, isSuccess: Boolean): String {
        return """
            ⚠️ MODO OFFLINE/FALLBACK ACTIVADO - SERVICE RESET
            
            **Servicio:** ${resetId.uppercase()} ($manufacturer)
            **Estado Automático:** ${if (isSuccess) "Completado" else "No completado / No compatible"}
            
            ### Procedimiento de Verificación Estándar:
            1. Apague el interruptor de encendido (Ignición OFF) durante al menos 10 segundos.
            2. Vuelva a encender la ignición (Ignición ON) sin arrancar el motor.
            3. Verifique que el indicador de advertencia o el mensaje de mantenimiento en el cuadro de instrumentos se haya apagado.
            4. Si el reinicio automático falló, intente buscar el procedimiento manual usando los botones del tablero (Trip/Odometer/OK) en combinación con la ignición de su modelo específico.
            
            ## 🛠️ Consejos Técnicos de Taller:
            - **Pastillas de freno (EPB):** Si abrió la pinza electrónica para cambiar pastillas, recuerde volver a cerrarla mediante el menú antes de presionar el pedal de freno.
            - **Batería:** El registro de batería es vital para evitar la sobrecarga y extender la vida útil del nuevo acumulador en marcas como BMW y Audi/VW.
        """.trimIndent()
    }

    companion object {
        private val JSON_MARKDOWN_REGEX = Regex("```json[\\s\\S]*?```")
        private val JSON_MARKDOWN_PATTERN = Pattern.compile("```json([\\s\\S]*?)```")
    }
}
