package com.elysium369.meet.education.ai

import com.elysium369.meet.ai.data.AiRepository
import com.elysium369.meet.ai.domain.AiContext
import com.elysium369.meet.ai.domain.AiFeature
import com.elysium369.meet.ai.domain.AiMessage
import com.elysium369.meet.ai.domain.AiRequest
import com.elysium369.meet.ai.domain.AiRole
import com.elysium369.meet.ai.domain.UserRole
import com.elysium369.meet.education.data.ConceptDeepKnowledge
import com.elysium369.meet.education.data.CurriculumConceptData
import com.elysium369.meet.education.data.InteractiveTaskData
import com.elysium369.meet.education.data.NationalCurriculumDeepKnowledge
import javax.inject.Inject
import javax.inject.Singleton

enum class SocraticMode {
    HINT,               // Pista socrática progresiva (1 a 3)
    REAL_WORLD_ANALOGY, // Analogía del mundo real o ingeniería
    MISCONCEPTION_HELP, // Explicación de por qué falló y cómo corregirlo
    STEP_BY_STEP,       // Descomposición del problema
    FREE_INQUIRY        // Pregunta libre del estudiante
}

data class SocraticDialogueMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val mode: SocraticMode? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

@Singleton
class SocraticTutorEngine @Inject constructor(
    private val aiRepository: AiRepository?
) {
    // Secondary constructor for unit testing without DI
    constructor() : this(null)

    suspend fun consultTutor(
        concept: CurriculumConceptData,
        task: InteractiveTaskData,
        mode: SocraticMode,
        userQuery: String? = null,
        misconceptionCode: String? = null,
        hintTier: Int = 1
    ): SocraticDialogueMessage {
        val knowledge = NationalCurriculumDeepKnowledge.getKnowledgeForConcept(concept)

        // Try LLM response first if AI repository is available and online
        if (aiRepository != null) {
            val llmResponse = runCatching {
                queryLlmTutor(concept, task, knowledge, mode, userQuery, misconceptionCode, hintTier)
            }.getOrNull()

            if (!llmResponse.isNullOrBlank()) {
                return SocraticDialogueMessage(
                    id = "soc_${System.currentTimeMillis()}",
                    isUser = false,
                    text = llmResponse,
                    mode = mode
                )
            }
        }

        // High-Quality Deterministic Socratic Fallback (Offline resilient)
        val offlineAnswer = generateDeterministicSocraticResponse(
            concept = concept,
            task = task,
            knowledge = knowledge,
            mode = mode,
            userQuery = userQuery,
            misconceptionCode = misconceptionCode,
            hintTier = hintTier
        )

        return SocraticDialogueMessage(
            id = "soc_offline_${System.currentTimeMillis()}",
            isUser = false,
            text = offlineAnswer,
            mode = mode
        )
    }

    private suspend fun queryLlmTutor(
        concept: CurriculumConceptData,
        task: InteractiveTaskData,
        knowledge: ConceptDeepKnowledge,
        mode: SocraticMode,
        userQuery: String?,
        misconceptionCode: String?,
        hintTier: Int
    ): String? {
        val repo = aiRepository ?: return null

        val systemPrompt = """
Eres el Tutor Socrático de Inteligencia Artificial de ELYSIUM LEARNING OS (MEP Costa Rica).
Tu misión es guiar al estudiante a través de la mayéutica socrática para que descubra el conocimiento por sí mismo (Efecto Bloom 2-Sigma).

REGLAS PEDAGÓGICAS SUPREMAS:
1. JAMÁS reveles la respuesta final, la opción correcta o la letra (A, B, C, D) del ejercicio. Si lo haces, destruirás el aprendizaje.
2. Formula preguntas orientadoras reflexivas (1 o máximo 2 preguntas) que iluminen el siguiente paso mental.
3. Utiliza analogías del mundo real, ingeniería automotriz, oficios costarricenses o la vida cotidiana para aterrizar conceptos abstractos.
4. Mantén un tono alentador, respetuoso, empático y constructivo (Ley 8968 de protección de menores).
5. Si el estudiante cometió un error, ayuda a que identifique la contradicción lógica en su propio razonamiento sin juzgarlo.

CONTEXTO CURRICULAR OFICIAL:
- Concepto: ${concept.title} (${concept.conceptCode})
- Descripción MEP: ${concept.description}
- Ejercicio Actual: ${task.prompt}
- Intuición Central: ${knowledge.coreIntuition}
- Aplicación Real: ${knowledge.realWorldApplication}
""".trimIndent()

        val userPrompt = when (mode) {
            SocraticMode.HINT -> "Dame una pista socrática de nivel $hintTier para este ejercicio sin darme la solución."
            SocraticMode.REAL_WORLD_ANALOGY -> "Explícame este concepto con una analogía física del mundo real, un taller mecánico o la vida diaria."
            SocraticMode.MISCONCEPTION_HELP -> "Me equivoqué con el código de error '$misconceptionCode'. ¿Por qué mi razonamiento falló y qué pregunta debo hacerme para corregirlo?"
            SocraticMode.STEP_BY_STEP -> "Desglosa el modelo mental en pasos para abordar este problema sin resolverlo por mí."
            SocraticMode.FREE_INQUIRY -> userQuery ?: "Tengo una duda sobre este concepto."
        }

        val request = AiRequest(
            feature = AiFeature.EDUCATION_SOCRATIC_TUTOR,
            providerId = "gemini",
            model = "gemini-1.5-flash",
            messages = listOf(
                AiMessage(role = AiRole.SYSTEM, content = systemPrompt),
                AiMessage(role = AiRole.USER, content = userPrompt)
            ),
            temperature = 0.3,
            maxTokens = 600,
            context = AiContext(
                vehicle = null,
                obd = null,
                dtcs = emptyList(),
                livePids = emptyList(),
                manualAvailability = null,
                appModule = "EDUCATION_SOCRATIC_TUTOR",
                locale = "es_CR",
                userRole = UserRole.CLIENT,
                safetyMode = true
            )
        )

        val result = repo.complete(request)
        return result.getOrNull()?.text?.trim()
    }

    private fun generateDeterministicSocraticResponse(
        concept: CurriculumConceptData,
        task: InteractiveTaskData,
        knowledge: ConceptDeepKnowledge,
        mode: SocraticMode,
        userQuery: String?,
        misconceptionCode: String?,
        hintTier: Int
    ): String {
        return when (mode) {
            SocraticMode.HINT -> {
                val tierIndex = (hintTier - 1).coerceIn(0, (knowledge.socraticHintTiers.size - 1).coerceAtLeast(0))
                val tier = knowledge.socraticHintTiers.getOrNull(tierIndex)
                    ?: knowledge.socraticHintTiers.firstOrNull()

                if (tier != null) {
                    "💡 **Pista Socrática (${tier.title}):**\n\n${tier.socraticQuestion}\n\n*Apoyo conceptual:* ${tier.conceptualScaffold}"
                } else {
                    "💡 **Pista Orientativa:**\n\n¿Qué datos explícitos te entrega el problema y cuál es la propiedad fundamental de '${concept.title}' que los relaciona?"
                }
            }

            SocraticMode.REAL_WORLD_ANALOGY -> {
                buildString {
                    append("🔧 **Analogía del Mundo Real e Ingeniería:**\n\n")
                    append("${knowledge.realWorldApplication}\n\n")
                    if (!knowledge.vocationalEngineeringBridge.isNullOrBlank()) {
                        append("🛠️ **Conexión con Oficios / Técnica:**\n")
                        append("${knowledge.vocationalEngineeringBridge}\n\n")
                    }
                    append("❓ **Pregunta para reflexionar:**\n")
                    append(knowledge.reflectionPrompt)
                }
            }

            SocraticMode.MISCONCEPTION_HELP -> {
                val misconception = knowledge.misconceptions.firstOrNull { it.code == misconceptionCode }
                    ?: knowledge.misconceptions.firstOrNull()

                if (misconception != null) {
                    buildString {
                        append("🔍 **Diagnóstico Pedagógico:** ${misconception.title}\n\n")
                        append("⚠️ *Suposición que suele causar el error:* ${misconception.studentFaultyAssumption}\n\n")
                        append("⚖️ *Contraejemplo demostrativo:* ${misconception.counterExample}\n\n")
                        append("👉 *Pregunta de auto-corrección:* ${misconception.socraticRemediationPrompt}")
                    }
                } else {
                    "🔍 **Análisis de Error:**\n\nRevisa si tu respuesta conservó la coherencia de unidades y signos. Recuerda: ${task.explanation}"
                }
            }

            SocraticMode.STEP_BY_STEP -> {
                buildString {
                    append("🧩 **Modelo Mental Paso a Paso (${concept.title}):**\n\n")
                    knowledge.expertMentalModel.forEach { step ->
                        append("$step\n")
                    }
                    append("\n*¿En cuál de estos pasos sientes que está la mayor dificultad para este ejercicio?*")
                }
            }

            SocraticMode.FREE_INQUIRY -> {
                val query = userQuery?.trim().orEmpty()
                "🧠 **Tutor Socrático:**\n\nHas preguntado sobre: *\"$query\"*.\n\nPara responderte socráticamente: Recuerda que en '${concept.title}', ${knowledge.coreIntuition}\n\n¿Cómo aplicarías este principio a la duda que planteas?"
            }
        }
    }
}
