package com.elysium369.meet.core.plugin

import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §69 — Plugin / Extension Registry.
 * Extracted from OpenClaw's defineToolPlugin + capability registry pattern.
 *
 * Enables third-party extensions: a taller connecting its inventory,
 * a school integrating its LMS, a fleet connecting its GPS system.
 * Plugins register capabilities the platform can orchestrate.
 */

// ─── Plugin Identity ───

@Serializable
data class PluginId(val value: String) {
    init { require(value.isNotBlank()) { "PluginId cannot be blank" } }
}

enum class PluginType {
    TOOL,           // Adds a callable tool/action
    DATA_SOURCE,    // Provides data (inventory, GPS, LMS grades)
    CHANNEL,        // Adds a communication channel
    AI_PROVIDER,    // Adds an AI model provider
    PAYMENT,        // Adds a payment gateway
    STORAGE,        // Adds storage backend
    ANALYTICS,      // Adds analytics/reporting
}

enum class PluginState {
    REGISTERED, ACTIVE, SUSPENDED, ERROR, DEPRECATED,
}

// ─── Plugin Capability ───

@Serializable
data class PluginCapability(
    val name: String,
    val description: String,
    val inputSchema: Map<String, String> = emptyMap(),
    val outputSchema: Map<String, String> = emptyMap(),
    val requiresAuth: Boolean = false,
)

// ─── Plugin Registration ───

@Serializable
data class PluginRegistration(
    val pluginId: PluginId,
    val name: String,
    val version: String,
    val type: PluginType,
    val author: String,
    val description: String,
    val capabilities: List<PluginCapability>,
    val state: PluginState = PluginState.REGISTERED,
    val configSchema: Map<String, String> = emptyMap(),
    val registeredAtEpochMs: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean get() = state == PluginState.ACTIVE
}

// ─── Plugin Registry ───

class ElysiumPluginRegistry {

    private val plugins = mutableMapOf<PluginId, PluginRegistration>()

    /**
     * Registers a new plugin. Validates uniqueness and basic schema.
     */
    fun register(plugin: PluginRegistration): Boolean {
        if (plugin.pluginId in plugins) return false
        require(plugin.capabilities.isNotEmpty()) { "Plugin must have at least one capability" }
        plugins[plugin.pluginId] = plugin.copy(state = PluginState.ACTIVE)
        return true
    }

    fun unregister(pluginId: PluginId): Boolean {
        return plugins.remove(pluginId) != null
    }

    fun getPlugin(pluginId: PluginId): PluginRegistration? {
        return plugins[pluginId]
    }

    fun activate(pluginId: PluginId): Boolean {
        val plugin = plugins[pluginId] ?: return false
        plugins[pluginId] = plugin.copy(state = PluginState.ACTIVE)
        return true
    }

    fun suspend(pluginId: PluginId): Boolean {
        val plugin = plugins[pluginId] ?: return false
        plugins[pluginId] = plugin.copy(state = PluginState.SUSPENDED)
        return true
    }

    /**
     * Finds plugins by type.
     */
    fun pluginsOfType(type: PluginType): List<PluginRegistration> {
        return plugins.values.filter { it.type == type && it.isActive }
    }

    /**
     * Finds plugins that provide a specific capability name.
     */
    fun pluginsWithCapability(capabilityName: String): List<PluginRegistration> {
        return plugins.values.filter { reg ->
            reg.isActive && reg.capabilities.any { it.name == capabilityName }
        }
    }

    val activePlugins: List<PluginRegistration>
        get() = plugins.values.filter { it.isActive }

    val registeredCount: Int get() = plugins.size
}
