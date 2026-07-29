package com.elysium369.meet.ai.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.elysium369.meet.ai.data.AiProviderRegistry
import com.elysium369.meet.ai.data.AiSecureKeyStoreImpl
import com.elysium369.meet.ai.data.AiUsageTracker
import com.elysium369.meet.ai.data.AiPromptStore
import com.elysium369.meet.ai.data.AiRepositoryImpl
import com.elysium369.meet.ai.domain.*
import com.elysium369.meet.automotive.parts.AutomotivePart
import com.elysium369.meet.automotive.parts.ProcedureKnowledgeBase
import com.elysium369.meet.automotive.parts.RegionalSynonymResolver
import com.elysium369.meet.automotive.parts.ResolvedAlias
import kotlinx.coroutines.*

/**
 * Debug-source-set BroadcastReceiver to test the AI engine via ADB.
 *
 * Usage:
 *   adb shell am broadcast -a com.elysium369.meet.AI_TEST \
 *       -n com.elysium369.meet/.ai.debug.AiTestReceiver \
 *       --es prompt "Diagnostica P0171 en un Toyota Corolla 2018" \
 *       --es provider "minimax" \
 *       --es model "MiniMax-M1"
 *
 * Watch output with:
 *   adb logcat -s AiTestReceiver:V
 */
class AiTestReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AiTestReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val rawPrompt = intent.getStringExtra("prompt") ?: ""
        val encodedPrompt = intent.getStringExtra("prompt_encoded") ?: ""
        val prompt = when {
            encodedPrompt.isNotBlank() -> java.net.URLDecoder.decode(encodedPrompt, "UTF-8")
            rawPrompt.isNotBlank() -> rawPrompt
            else -> "Diagnostica P0171 en un Toyota Corolla 2018"
        }
        val providerId = intent.getStringExtra("provider") ?: "minimax"
        val model = intent.getStringExtra("model") ?: "MiniMax-M1"

        val logFile = java.io.File(context.getExternalFilesDir(null), "ai_test_log.txt")
        logFile.writeText("=== AI TEST LOG STARTED ===\n")

        val log = { msg: String ->
            Log.i(TAG, msg)
            logFile.appendText(msg + "\n")
        }

        val logError = { msg: String, t: Throwable? ->
            Log.e(TAG, msg, t)
            logFile.appendText("ERROR: " + msg + "\n" + (t?.stackTraceToString() ?: "") + "\n")
        }

        log("╔════════════════════════════════════════════════════")
        log("║ AI TEST INITIATED")
        log("║ Provider: $providerId")
        log("║ Model:    $model")
        log("║ Prompt:   $prompt")
        log("╚════════════════════════════════════════════════════")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                log("Initializing Keystore...")
                val keyStore = AiSecureKeyStoreImpl(context)
                log("Initializing Provider Registry...")
                val registry = AiProviderRegistry(keyStore)
                val usageTracker = AiUsageTracker()
                val promptStore = AiPromptStore()
                log("Initializing Repository...")
                val repository = AiRepositoryImpl(registry, usageTracker, promptStore)

                val aiContext = AiContext(
                    vehicle = VehicleContext(
                        make = "Toyota", model = "Corolla", year = 2018,
                        engine = "1.8L 4cyl", transmission = "CVT",
                        fuel = "Gasolina", vin = "TEST000000000",
                        odometer = 85000.0
                    ),
                    obd = ObdContext(
                        connected = false,
                        activePidsCount = 0,
                        dtcActiveCount = 1,
                        batteryVoltage = 12.6f
                    ),
                    dtcs = listOf(DtcContext("P0171", "Activo")),
                    livePids = emptyList(),
                    manualAvailability = null,
                    appModule = "AI_TEST_ADB",
                    locale = "es-MX",
                    userRole = UserRole.MECHANIC,
                    safetyMode = true
                )

                log("Initializing KnowledgeBase...")
                val knowledgeBase = ProcedureKnowledgeBase(context)
                val synonymResolver = RegionalSynonymResolver()

                // RAG: Resolve regional synonyms and inject local knowledge
                val resolvedAlias = synonymResolver.resolve(prompt)
                val knowledgeContext = if (resolvedAlias != null) {
                    val part = knowledgeBase.getPart(resolvedAlias.partId)
                    if (part != null) {
                        buildKnowledgeInjection(part, resolvedAlias)
                    } else {
                        ""
                    }
                } else {
                    val matchedParts = knowledgeBase.searchParts(prompt)
                    if (matchedParts.isNotEmpty()) {
                        buildKnowledgeInjection(matchedParts.first(), null)
                    } else {
                        ""
                    }
                }

                val finalPrompt = if (knowledgeContext.isNotBlank()) {
                    "$knowledgeContext\n\n$prompt"
                } else {
                    prompt
                }

                val feature = if (resolvedAlias != null || knowledgeContext.isNotBlank()) {
                    AiFeature.REPAIR_GUIDE
                } else {
                    AiFeature.DIAGNOSTIC_DTC
                }

                val request = AiRequest(
                    feature = feature,
                    providerId = providerId,
                    model = model,
                    messages = listOf(
                        AiMessage(AiRole.USER, finalPrompt)
                    ),
                    context = aiContext,
                    timeoutMs = 30_000L
                )

                log("→ Sending request to $providerId/$model ...")
                val startMs = System.currentTimeMillis()
                val result = repository.complete(request)
                val elapsedMs = System.currentTimeMillis() - startMs

                result.fold(
                    onSuccess = { response ->
                        log("╔════════════════════════════════════════════════════")
                        log("║ ✅ AI RESPONSE SUCCESS")
                        log("║ Provider: ${response.providerId}")
                        log("║ Model:    ${response.model}")
                        log("║ Latency:  ${elapsedMs}ms")
                        response.usage?.let { u ->
                            log("║ Tokens:   prompt=${u.promptTokens} completion=${u.completionTokens} total=${u.totalTokens}")
                        }
                        log("╠════════════════════════════════════════════════════")
                        log("║ Content:")
                        log(response.text)
                        log("╚════════════════════════════════════════════════════")
                    },
                    onFailure = { error ->
                        logError("║ ❌ AI RESPONSE FAILED — Error: ${error.javaClass.simpleName}: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                logError("Fatal error in AiTestReceiver: ${e.message}", e)
            } finally {
                log("=== AI TEST LOG COMPLETED ===")
                pendingResult.finish()
            }
        }
    }

    private fun buildKnowledgeInjection(
        part: AutomotivePart,
        resolvedAlias: ResolvedAlias?
    ): String {
        return buildString {
            appendLine("=== CONOCIMIENTO TÉCNICO LOCAL (BASE DE DATOS VERIFICADA) ===")
            if (resolvedAlias != null) {
                appendLine("🔍 Sinónimo regional detectado → Pieza canónica: ${part.canonicalNameEs} (${part.canonicalNameEn})")
                appendLine("   Sistema: ${part.system.name} | Confianza: ${resolvedAlias.confidence}")
            }
            appendLine("Pieza: ${part.canonicalNameEs} (${part.canonicalNameEn})")
            appendLine("Sistema: ${part.system.name} | Subsistema: ${part.subsystem}")
            appendLine("Descripción: ${part.description}")
            appendLine("Aliases conocidos: ${part.aliases.joinToString(", ")}")
            appendLine("Síntomas de falla: ${part.symptoms.joinToString("; ")}")
            appendLine("DTCs relacionados: ${part.relatedDtcs.joinToString(", ")}")
            appendLine("Herramientas requeridas: ${part.requiredTools.joinToString(", ")}")
            appendLine("Nivel de seguridad: ${part.safetyLevel.name}")
            if (part.procedures.isNotEmpty()) {
                val proc = part.procedures.first()
                appendLine("\n--- PROCEDIMIENTO TÉCNICO ---")
                appendLine("Título: ${proc.title}")
                appendLine("Dificultad: ${proc.difficulty}")
                appendLine("Tiempo estimado: ${proc.estimatedTimeMinutes} minutos")
                appendLine("Requiere elevador: ${if (proc.requiresLift) "SÍ" else "NO"}")
                appendLine("Requiere alineación: ${if (proc.requiresAlignment) "SÍ" else "NO"}")
                appendLine("\nAntes de empezar:")
                proc.beforeStart.forEachIndexed { i, step -> appendLine("  ${i + 1}. $step") }
                appendLine("\nPasos:")
                proc.steps.forEachIndexed { i, step -> appendLine("  ${i + 1}. $step") }
                if (proc.torqueSpecs.isNotEmpty()) {
                    appendLine("\nTorques:")
                    proc.torqueSpecs.forEach { ts ->
                        appendLine("  - ${ts.description}: ${ts.torqueNm} Nm (${ts.torqueFtLbs} ft-lbs)${ts.angleDegrees?.let { " + ${it}°" } ?: ""}")
                    }
                }
                appendLine("\nErrores comunes:")
                proc.commonMistakes.forEachIndexed { i, m -> appendLine("  ${i + 1}. $m") }
                appendLine("\nValidación post-reparación:")
                proc.postRepairValidation.forEachIndexed { i, v -> appendLine("  ${i + 1}. $v") }
                appendLine("\n⚠️ ${proc.whenToStopWarning}")
                appendLine("💬 Explicación al cliente: ${proc.customerExplanation}")
            }
            if (part.notes.isNotEmpty()) {
                appendLine("\nNotas adicionales:")
                part.notes.forEach { appendLine("  • $it") }
            }
            appendLine("=== FIN CONOCIMIENTO LOCAL ===")
            appendLine("\nIMPORTANTE: Usa la información anterior como base técnica. NO inventes torques ni especificaciones que no estén aquí. Si no hay dato, indica 'consultar manual OEM'.")
        }
    }
}
