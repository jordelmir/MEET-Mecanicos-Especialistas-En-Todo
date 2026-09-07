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

class ElysiumAgentBus {

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
     * Priority: explicit toAgentId → domain match → capability match.
     */
    fun route(request: AgentRequest): AgentRegistration? {
        // Direct addressing
        if (request.toAgentId != null) {
            return agents[AgentId(request.toAgentId)]
        }

        // Domain-based routing
        if (request.targetDomain != null) {
            val domainAgents = agents.values
                .filter { it.domain == request.targetDomain }
                .sortedByDescending { it.priority }
            if (domainAgents.isNotEmpty()) return domainAgents.first()
        }

        // Capability-based routing (intent matching)
        val capableAgents = agents.values.filter { reg ->
            reg.capabilities.any { cap ->
                request.intent.contains(cap.intentPattern, ignoreCase = true)
            }
        }.sortedByDescending { it.priority }

        return capableAgents.firstOrNull()
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
