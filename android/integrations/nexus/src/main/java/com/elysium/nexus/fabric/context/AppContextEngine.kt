package com.elysium.nexus.fabric.context

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §62 App Context Engine.
 *
 * Host agents report:
 * ```
 * activeAppId
 * capabilities
 * context
 * ```
 *
 * No need to transmit full screen content.
 * This enables dynamic UIs without camera.
 *
 * ## Context Surface
 *
 * When Photoshop is active:
 * ```
 * brush, eraser, size, undo, zoom, layers
 * ```
 *
 * When VS Code:
 * ```
 * run, debug, terminal, git, tests, search
 * ```
 *
 * When Spotify:
 * ```
 * play, next, volume, playlist
 * ```
 *
 * The engine maps (activeApp + capabilities)
 * → ContextSurface with relevant actions.
 */
class AppContextEngine {

    private val _activeContext = MutableStateFlow<ApplicationContext?>(null)
    val activeContext: Flow<ApplicationContext?> = _activeContext.asStateFlow()

    private val _contextSurface = MutableStateFlow<ContextSurfaceResult?>(null)
    val contextSurface: Flow<ContextSurfaceResult?> = _contextSurface.asStateFlow()

    private val knownApps = mutableMapOf<String, AppContextDefinition>()

    /**
     * Register an app context definition.
     * Defines what actions are available for
     * a specific application.
     */
    fun registerApp(definition: AppContextDefinition) {
        knownApps[definition.appId] = definition
    }

    /**
     * Update the active application context.
     * Called by the host agent when the
     * focused application changes.
     */
    fun updateActiveApp(appId: String, appName: String, extras: Map<String, String> = emptyMap()) {
        val definition = knownApps[appId]
        val context = ApplicationContext(
            appId = appId,
            appName = appName,
            extras = extras,
            capabilities = definition?.capabilities ?: emptyList(),
            timestampNs = System.nanoTime()
        )
        _activeContext.value = context

        // Generate context surface
        val surface = definition?.let { generateSurface(it, extras) }
        _contextSurface.value = surface
    }

    /**
     * Clear the active context (no app focused).
     */
    fun clearContext() {
        _activeContext.value = null
        _contextSurface.value = null
    }

    /**
     * Get the current context surface.
     */
    fun currentSurface(): ContextSurfaceResult? = _contextSurface.value

    /**
     * Check if an action is available in the
     * current context.
     */
    fun isActionAvailable(action: UniversalAction): Boolean {
        val surface = _contextSurface.value ?: return false
        return surface.actions.any { it.action == action }
    }

    private fun generateSurface(
        definition: AppContextDefinition,
        extras: Map<String, String>
    ): ContextSurfaceResult {
        val actions = definition.actions.map { actionDef ->
            ContextAction(
                id = actionDef.id,
                label = actionDef.label,
                iconHint = actionDef.iconHint,
                action = actionDef.action,
                category = actionDef.category
            )
        }

        // Filter by extras (e.g. "tool=brush" shows brush-specific actions)
        val filteredActions = if (extras.containsKey("tool")) {
            actions.filter { it.category == extras["tool"] || it.category == "general" }
        } else {
            actions
        }

        return ContextSurfaceResult(
            appId = definition.appId,
            appName = definition.appName,
            actions = filteredActions,
            layout = definition.layout
        )
    }
}

/**
 * Application context.
 */
data class ApplicationContext(
    val appId: String,
    val appName: String,
    val extras: Map<String, String>,
    val capabilities: List<String>,
    val timestampNs: Long
)

/**
 * App context definition (registered per app).
 */
data class AppContextDefinition(
    val appId: String,
    val appName: String,
    val capabilities: List<String>,
    val actions: List<ContextActionDefinition>,
    val layout: ContextLayout = ContextLayout.Auto
)

/**
 * A context action definition.
 */
data class ContextActionDefinition(
    val id: String,
    val label: String,
    val iconHint: String? = null,
    val action: UniversalAction,
    val category: String = "general"
)

/**
 * Context surface result.
 */
data class ContextSurfaceResult(
    val appId: String,
    val appName: String,
    val actions: List<ContextAction>,
    val layout: ContextLayout
)

/**
 * A context action.
 */
data class ContextAction(
    val id: String,
    val label: String,
    val iconHint: String? = null,
    val action: UniversalAction,
    val category: String
)

/**
 * Context layout hint.
 */
enum class ContextLayout {
    Auto,
    Grid,
    List,
    Wheel,
    Palette
}
