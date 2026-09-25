package com.elysium369.meet.core.agent

import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §11, §36 — Agent Bus (Gateway Pattern).
 * Extracted from OpenClaw's hub-and-spoke Gateway architecture.
 *
 * Central message bus that routes intents to specialized agents.
 * Each agent handles a domain (automotive, education, plumbing, etc.)
 * Messages are typed, routed by domain, and support request-response + events.
 *
 * Unlike OpenClaw which uses WebSocket, ELYSIUM's Agent Bus is in-process
 * for the APK (single process) with an interface that can be extended
 * to WebSocket/gRPC for server deployment.
 */

// ─── Agent Identity ───

@Serializable
data class AgentId(val value: String) {
    init { require(value.isNotBlank()) { "AgentId cannot be blank" } }
}

enum class AgentDomain(val label: String) {
    AUTOMOTIVE("Mecánica automotriz"),
    EDUCATION("Educación y aprendizaje"),
    PLUMBING("Fontanería"),
    ELECTRICAL("Electricidad"),
    TOWING("Grúas y remolque"),
    REAL_ESTATE("Bienes raíces"),
    COMMERCE("Comercio y repuestos"),
    TRAVEL("Viajes y transporte"),
    GENERAL("Asistente general"),
    ECONOMIC("Economía y empleo"),
    HEALTH("Salud"),
    LEGAL("Legal y contratos"),
}

enum class AgentState {
    IDLE, PROCESSING, WAITING_FOR_INPUT, ERROR, SUSPENDED,
}

@Serializable
data class AgentCapability(
    val intentPattern: String,
    val description: String,
    val requiredInputs: List<String> = emptyList(),
)

// ─── Agent Registration ───

data class AgentRegistration(
    val agentId: AgentId,
    val domain: AgentDomain,
    val capabilities: List<AgentCapability>,
    val priority: Int = 0,
    val state: AgentState = AgentState.IDLE,
)

data class AgentIntentMatch(
    val agent: AgentRegistration,
    val matchedCapability: AgentCapability,
    val score: Double,
    val matchType: String,
)

// ─── Bus Messages ───

@Serializable
sealed class BusMessage {
    abstract val messageId: String
    abstract val timestamp: Long
}

@Serializable
data class AgentRequest(
    override val messageId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val fromAgentId: String = "user",
    val toAgentId: String? = null,
    val targetDomain: AgentDomain? = null,
    val intent: String,
    val payload: Map<String, String> = emptyMap(),
) : BusMessage()

@Serializable
data class AgentResponse(
    override val messageId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val requestId: String,
    val fromAgentId: String,
    val status: ResponseStatus,
    val payload: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
) : BusMessage()

enum class ResponseStatus {
    SUCCESS, PARTIAL, ERROR, NEEDS_INPUT, FORWARDED,
}

@Serializable
data class AgentEvent(
    override val messageId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val fromAgentId: String,
    val eventType: String,
    val payload: Map<String, String> = emptyMap(),
) : BusMessage()

// ─── Agent Bus (the router) ───

class ElysiumAgentBus(
    private val layaDecisionEngine: com.elysium369.meet.core.agent.laya.LayaDecisionEngine? = com.elysium369.meet.core.agent.laya.LayaDecisionEngine(),
) {

    private val agents = mutableMapOf<AgentId, AgentRegistration>()
    private val eventListeners = mutableListOf<(AgentEvent) -> Unit>()

    fun registerAgent(registration: AgentRegistration) {
        require(registration.agentId !in agents) {
            "Agent ${registration.agentId.value} already registered"
        }
        agents[registration.agentId] = registration
    }

    fun unregisterAgent(agentId: AgentId) {
        agents.remove(agentId)
    }

    val registeredAgents: List<AgentRegistration>
        get() = agents.values.toList()

    /**
     * Routes a request to the best matching agent.
     * Priority: explicit toAgentId → domain match → capability match (syntactic + Laya semantic).
     */
    fun route(request: AgentRequest): AgentRegistration? {
        return routeWithMatch(request)?.agent
    }

    /**
     * Resolves the best agent and capability with its matching confidence score.
     */
    fun routeWithMatch(request: AgentRequest): AgentIntentMatch? {
        if (request.toAgentId != null) {
            val agent = agents[AgentId(request.toAgentId)] ?: return null
            val defaultCap = agent.capabilities.firstOrNull() ?: AgentCapability("direct", "Direct addressing")
            return AgentIntentMatch(agent, defaultCap, 1.0, "DIRECT")
        }

        // Domain-based routing filter
        val eligibleAgents = if (request.targetDomain != null) {
            agents.values.filter { it.domain == request.targetDomain }
        } else {
            agents.values.toList()
        }

        // 1. Syntactic match attempt
        val syntacticMatches = eligibleAgents.flatMap { reg ->
            reg.capabilities.mapNotNull { cap ->
                calculateMatchScore(request.intent, cap.intentPattern)?.let { (score, matchType) ->
                    AgentIntentMatch(reg, cap, score, matchType)
                }
            }
        }.sortedWith(
            compareByDescending<AgentIntentMatch> { it.score }
                .thenByDescending { it.agent.priority }
        )

        if (syntacticMatches.isNotEmpty()) {
            return syntacticMatches.first()
        }

        // 2. Laya System 1 Semantic Match Fallback
        if (layaDecisionEngine != null && request.intent.isNotBlank()) {
            val candidateCapabilities = eligibleAgents.flatMap { reg ->
                reg.capabilities.map { cap -> reg to cap }
            }
            if (candidateCapabilities.isNotEmpty()) {
                val candidateOptions = candidateCapabilities.map { it.second.intentPattern }
                val questions = listOf(
                    com.elysium369.meet.core.agent.laya.LayaQuestion.Choice(
                        name = "target_capability",
                        options = candidateOptions
                    )
                )
                val batch = layaDecisionEngine.evaluateSync(request.intent, questions)
                val chosenPattern = batch.choice("target_capability")
                if (chosenPattern != null && chosenPattern.confidence >= 0.50) {
                    val found = candidateCapabilities.find { it.second.intentPattern == chosenPattern.value }
                    if (found != null) {
                        return AgentIntentMatch(
                            agent = found.first,
                            matchedCapability = found.second,
                            score = chosenPattern.confidence,
                            matchType = "LAYA_SYSTEM_ONE_SEMANTIC"
                        )
                    }
                }
            }
        }

        return null
    }

    private fun calculateMatchScore(inputIntent: String, pattern: String): Pair<Double, String>? {
        val trimmedInput = inputIntent.trim()
        val trimmedPattern = pattern.trim()
        if (trimmedInput.isEmpty() || trimmedPattern.isEmpty()) return null

        // 1. Exact match (highest confidence)
        if (trimmedInput.equals(trimmedPattern, ignoreCase = true)) {
            return 1.0 to "EXACT"
        }

        // 2. Regex pattern match
        if (trimmedPattern.contains(".*") || trimmedPattern.startsWith("^") || trimmedPattern.endsWith("$") || trimmedPattern.contains("\\b")) {
            try {
                val regex = Regex(trimmedPattern, RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(trimmedInput)) {
                    return 0.95 to "REGEX"
                }
            } catch (_: Exception) {}
        }

        // 3. Whole-word / token boundary match
        try {
            val wordRegex = Regex("\\b${Regex.escape(trimmedPattern)}\\b", RegexOption.IGNORE_CASE)
            if (wordRegex.containsMatchIn(trimmedInput)) {
                return 0.85 to "WORD_BOUNDARY"
            }
        } catch (_: Exception) {}

        // 4. Token intersection match (multi-word input vs pattern)
        val inputTokens = trimmedInput.lowercase().split("\\s+".toRegex()).toSet()
        val patternTokens = trimmedPattern.lowercase().split("\\s+".toRegex()).toSet()
        if (patternTokens.isNotEmpty() && inputTokens.containsAll(patternTokens)) {
            return 0.80 to "TOKEN_EXHAUSTIVE"
        }

        // 5. Substring match fallback
        if (trimmedInput.contains(trimmedPattern, ignoreCase = true)) {
            return 0.60 to "SUBSTRING"
        }

        return null
    }

    /**
     * Finds all agents that can handle a given domain.
     */
    fun agentsForDomain(domain: AgentDomain): List<AgentRegistration> {
        return agents.values
            .filter { it.domain == domain }
            .sortedByDescending { it.priority }
    }

    fun addEventListener(listener: (AgentEvent) -> Unit) {
        eventListeners.add(listener)
    }

    fun publishEvent(event: AgentEvent) {
        eventListeners.forEach { it(event) }
    }
}
