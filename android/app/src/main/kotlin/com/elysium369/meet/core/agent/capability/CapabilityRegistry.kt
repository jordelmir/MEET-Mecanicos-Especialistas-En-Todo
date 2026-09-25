package com.elysium369.meet.core.agent.capability

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry of all capabilities available to the Elysium AI OS.
 * Capabilities are registered by domain modules (Rides, Diagnostics, Emissions, etc.).
 * Master Order Omega §19, §20.
 */
class CapabilityRegistry {

    private val capabilities = ConcurrentHashMap<CapabilityId, AgentCapability<*, *>>()

    fun register(capability: AgentCapability<*, *>) {
        capabilities[capability.id] = capability
    }

    fun unregister(id: CapabilityId): Boolean {
        return capabilities.remove(id) != null
    }

    @Suppress("UNCHECKED_CAST")
    fun <I : Any, O : Any> get(id: CapabilityId): AgentCapability<I, O>? {
        return capabilities[id] as? AgentCapability<I, O>
    }

    fun contains(id: CapabilityId): Boolean = capabilities.containsKey(id)

    fun listAll(): List<AgentCapability<*, *>> = capabilities.values.toList()

    fun listByDomain(domain: String): List<AgentCapability<*, *>> {
        return capabilities.values.filter { it.id.domain.equals(domain, ignoreCase = true) }
    }

    fun listByRisk(risk: AgentRisk): List<AgentCapability<*, *>> {
        return capabilities.values.filter { it.risk == risk }
    }

    val count: Int get() = capabilities.size

    companion object {
        val default: CapabilityRegistry by lazy { CapabilityRegistry() }
    }
}
