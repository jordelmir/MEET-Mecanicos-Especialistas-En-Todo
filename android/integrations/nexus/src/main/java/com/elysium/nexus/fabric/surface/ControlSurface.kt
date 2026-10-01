package com.elysium.nexus.fabric.surface

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction

// ─── §28 Control Surface DSL ─────────────────────────────────────────────────

/**
 * Declarative language for defining control surfaces.
 *
 * ```yaml
 * surface:
 *   id: blender-editing
 *   name: "Blender Editing"
 *   description: "Control surface for Blender 3D editing"
 *   context:
 *     app: org.blender.Blender
 *   layout:
 *     rows: 3
 *     columns: 5
 *   controls:
 *     - type: rotary
 *       position: {row: 0, col: 0}
 *       action: zoom
 *       label: "Zoom"
 *     - type: touchpad
 *       position: {row: 1, col: 0}
 *       size: {rows: 2, cols: 2}
 *       action: viewport
 *       label: "Viewport"
 *     - type: button
 *       position: {row: 0, col: 2}
 *       action: undo
 *       label: "Undo"
 *       icon: "↩"
 * ```
 *
 * This makes profiles portable across devices and surfaces.
 */
data class ControlSurfaceDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0",
    val author: String = "",
    val context: SurfaceContext? = null,
    val layout: SurfaceLayout,
    val controls: List<ControlDefinition>,
    val variables: Map<String, VariableDefinition> = emptyMap(),
    val conditionals: List<ConditionalRule> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

data class SurfaceContext(
    val app: String? = null,
    val device: String? = null,
    val protocol: String? = null,
    val capabilities: Set<String> = emptySet()
)

data class SurfaceLayout(
    val rows: Int,
    val columns: Int,
    val orientation: Orientation = Orientation.PORTRAIT,
    val responsive: Boolean = false
)

enum class Orientation {
    PORTRAIT, LANDSCAPE, SQUARE
}

// ─── Controls ────────────────────────────────────────────────────────────────

sealed class ControlDefinition {
    abstract val position: CellPosition
    abstract val label: String
    abstract val icon: String?
    abstract val visible: Boolean
    abstract val enabled: Boolean

    /** A simple button. */
    data class Button(
        override val position: CellPosition,
        val size: CellSize = CellSize(1, 1),
        override val label: String,
        override val icon: String? = null,
        val action: UniversalAction,
        val pressType: PressType = PressType.TAP,
        val longPressAction: UniversalAction? = null,
        val doublePressAction: UniversalAction? = null,
        val stateLabel: Map<String, String> = emptyMap(),
        override val visible: Boolean = true,
        override val enabled: Boolean = true,
        val condition: String? = null
    ) : ControlDefinition()

    /** A toggle button. */
    data class Toggle(
        override val position: CellPosition,
        val size: CellSize = CellSize(1, 1),
        override val label: String,
        override val icon: String? = null,
        val onAction: UniversalAction,
        val offAction: UniversalAction,
        val stateKey: String,
        override val visible: Boolean = true,
        override val enabled: Boolean = true
    ) : ControlDefinition()

    /** A rotary knob / dial. */
    data class Rotary(
        override val position: CellPosition,
        val size: CellSize = CellSize(1, 1),
        override val label: String,
        override val icon: String? = null,
        val action: String,  // semantic action name
        val min: Float = 0f,
        val max: Float = 1f,
        val step: Float = 0.01f,
        val actionMapper: (Float) -> UniversalAction,
        override val visible: Boolean = true,
        override val enabled: Boolean = true
    ) : ControlDefinition()

    /** A touchpad area. */
    data class Touchpad(
        override val position: CellPosition,
        val size: CellSize = CellSize(2, 2),
        override val label: String,
        override val icon: String? = null,
        val action: String,
        val sensitivity: Float = 1.0f,
        val gestureMap: Map<String, UniversalAction> = emptyMap(),
        override val visible: Boolean = true,
        override val enabled: Boolean = true
    ) : ControlDefinition()

    /** A slider. */
    data class Slider(
        override val position: CellPosition,
        val size: CellSize = CellSize(3, 1),
        override val label: String,
        override val icon: String? = null,
        val orientation: SliderOrientation = SliderOrientation.HORIZONTAL,
        val min: Float = 0f,
        val max: Float = 1f,
        val defaultValue: Float = 0.5f,
        val actionMapper: (Float) -> UniversalAction,
        override val visible: Boolean = true,
        override val enabled: Boolean = true
    ) : ControlDefinition()

    /** A label (non-interactive). */
    data class Label(
        override val position: CellPosition,
        val size: CellSize = CellSize(1, 1),
        override val label: String,
        override val icon: String? = null,
        val stateKey: String? = null,
        val format: String? = null,
        override val visible: Boolean = true,
        override val enabled: Boolean = false
    ) : ControlDefinition()

    /** A folder that navigates to another surface. */
    data class Folder(
        override val position: CellPosition,
        val size: CellSize = CellSize(1, 1),
        override val label: String,
        override val icon: String? = null,
        val targetSurfaceId: String,
        override val visible: Boolean = true,
        override val enabled: Boolean = true
    ) : ControlDefinition()

    /** A separator / spacer. */
    data class Spacer(
        override val position: CellPosition,
        val size: CellSize = CellSize(1, 1),
        override val label: String = "",
        override val icon: String? = null,
        override val visible: Boolean = true,
        override val enabled: Boolean = false
    ) : ControlDefinition()
}

// ─── Position & Size ─────────────────────────────────────────────────────────

data class CellPosition(val row: Int, val col: Int) {
    init {
        require(row >= 0 && col >= 0) { "Position must be non-negative." }
    }
}

data class CellSize(val rows: Int, val cols: Int) {
    init {
        require(rows >= 1 && cols >= 1) { "Size must be at least 1x1." }
    }
}

// ─── Types ───────────────────────────────────────────────────────────────────

enum class PressType {
    TAP, LONG_PRESS, DOUBLE_PRESS
}

enum class SliderOrientation {
    HORIZONTAL, VERTICAL
}

// ─── Variables & Conditionals ────────────────────────────────────────────────

data class VariableDefinition(
    val type: VariableType,
    val defaultValue: Any? = null,
    val options: List<Any> = emptyList(),
    val description: String = ""
)

enum class VariableType {
    STRING, NUMBER, BOOLEAN, COLOR
}

data class ConditionalRule(
    val condition: String,
    val thenShow: List<String> = emptyList(),
    val thenHide: List<String> = emptyList(),
    val thenEnable: List<String> = emptyList(),
    val thenDisable: List<String> = emptyList()
)

// ─── §29 Stream Deck Mode ────────────────────────────────────────────────────

/**
 * Extended control surface with Stream Deck features:
 * - Pages / folders
 * - Context switching
 * - Variables
 * - Conditional buttons
 * - Dynamic labels
 * - Live states
 * - Macros
 *
 * But every button still executes [UniversalAction].
 */
data class StreamDeckProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0",

    // ── Pages ─────────────────────────────────────────────
    val pages: List<StreamDeckPage>,

    // ── Variables ─────────────────────────────────────────
    val variables: Map<String, VariableDefinition> = emptyMap(),

    // ── Context ───────────────────────────────────────────
    val contextRules: List<ContextSwitchRule> = emptyList(),

    // ── Global ────────────────────────────────────────────
    val brightness: Int = 80,
    val autoReturn: Boolean = true,
    val autoReturnDelayMs: Long = 5_000L
) {
    init {
        require(pages.isNotEmpty()) { "Profile must have at least one page." }
    }
}

data class StreamDeckPage(
    val id: String,
    val name: String,
    val controls: List<StreamDeckControl>,
    val columns: Int = 5,
    val rows: Int = 3
) {
    init {
        require(controls.all { it.position.row < rows && it.position.col < columns }) {
            "All controls must fit within page bounds."
        }
    }
}

data class StreamDeckControl(
    val position: CellPosition,
    val control: ControlDefinition,

    // ── Dynamic State ─────────────────────────────────────
    val stateBindings: Map<String, StateBinding> = emptyMap(),
    val dynamicLabel: DynamicLabel? = null,
    val liveState: LiveState? = null,

    // ── Navigation ────────────────────────────────────────
    val navigation: NavigationAction? = null
)

data class StateBinding(
    val stateKey: String,
    val valueMap: Map<String, ControlState>
)

data class ControlState(
    val label: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val enabled: Boolean = true
)

data class DynamicLabel(
    val stateKey: String,
    val format: String,  // e.g., "Vol: %s"
    val fallback: String = ""
)

data class LiveState(
    val deviceId: DeviceId,
    val capability: String,
    val updateIntervalMs: Long = 1_000L,
    val formatter: String? = null
)

sealed class NavigationAction {
    data class GoToPage(val pageId: String) : NavigationAction()
    data object GoBack : NavigationAction()
    data class OpenFolder(val surfaceId: String) : NavigationAction()
}

data class ContextSwitchRule(
    val trigger: String,  // "app_changed", "device_connected", "time"
    val value: String,
    val targetPage: String
)
