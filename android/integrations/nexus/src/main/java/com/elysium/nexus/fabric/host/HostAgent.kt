package com.elysium.nexus.fabric.host

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.Flow

/**
 * §24 Elysium Host Fabric.
 *
 * Unifies macOS, Windows, and Linux agents
 * under a common [HostAgent] interface. The
 * host agent provides:
 *
 * - Mouse/keyboard control
 * - Clipboard access
 * - File operations
 * - Notifications
 * - Media control
 * - App list / active app
 * - Screen streaming
 * - System commands
 * - Battery/audio state
 * - Semantic commands
 *
 * ## Architecture
 *
 * ```
 * Android App
 *     ↕ Elysium Link
 * Mac Agent / Windows Agent / Linux Agent
 *     ↕ OS APIs
 * Host OS
 * ```
 *
 * The agent runs as a companion process
 * on the host machine. It connects to
 * the Android app via Elysium Link
 * (encrypted WebSocket or USB).
 *
 * ## Security
 *
 * The agent only exposes capabilities
 * that the user has explicitly authorized.
 * Each capability requires user consent.
 */
interface HostAgent {

    /** The host device identity. */
    val deviceId: DeviceId

    /** The host platform. */
    val platform: HostPlatform

    /** The agent's current state. */
    val state: Flow<HostAgentState>

    /** The active application on the host. */
    val activeApp: Flow<AppInfo?>

    /** Supported capabilities. */
    val capabilities: Set<HostCapability>

    /**
     * Start the host agent connection.
     */
    suspend fun connect(): HostConnectionResult

    /**
     * Disconnect from the host.
     */
    suspend fun disconnect()

    // ── Input ──────────────────────────────────

    /**
     * Move the mouse to absolute coordinates.
     */
    suspend fun mouseMove(x: Int, y: Int): Boolean

    /**
     * Move the mouse by relative delta.
     */
    suspend fun mouseMoveRelative(dx: Int, dy: Int): Boolean

    /**
     * Mouse click at current position.
     */
    suspend fun mouseClick(button: MouseButton): Boolean

    /**
     * Mouse double-click.
     */
    suspend fun mouseDoubleClick(button: MouseButton): Boolean

    /**
     * Mouse drag from current position.
     */
    suspend fun mouseDrag(dx: Int, dy: Int): Boolean

    /**
     * Scroll by delta.
     */
    suspend fun mouseScroll(delta: Int): Boolean

    /**
     * Type text via keyboard.
     */
    suspend fun keyboardType(text: String): Boolean

    /**
     * Press a key.
     */
    suspend fun keyboardPress(key: HostKey): Boolean

    /**
     * Release a key.
     */
    suspend fun keyboardRelease(key: HostKey): Boolean

    /**
     * Key combination (e.g. Cmd+C).
     */
    suspend fun keyboardCombo(vararg keys: HostKey): Boolean

    // ── Clipboard ──────────────────────────────

    /**
     * Get clipboard contents.
     */
    suspend fun clipboardGet(): String?

    /**
     * Set clipboard contents.
     */
    suspend fun clipboardSet(text: String): Boolean

    // ── Media ──────────────────────────────────

    /**
     * Get current media state.
     */
    suspend fun mediaState(): MediaState?

    /**
     * Execute media action.
     */
    suspend fun mediaAction(action: MediaAction): Boolean

    // ── Apps ───────────────────────────────────

    /**
     * List installed applications.
     */
    suspend fun listApps(): List<AppInfo>

    /**
     * Launch an application.
     */
    suspend fun launchApp(appId: String): Boolean

    /**
     * Get the currently focused application.
     */
    suspend fun focusedApp(): AppInfo?

    // ── System ─────────────────────────────────

    /**
     * Get system information.
     */
    suspend fun systemInfo(): SystemInfo

    /**
     * Execute a system command.
     */
    suspend fun systemCommand(command: SystemCommand): SystemCommandResult

    // ── Context ────────────────────────────────

    /**
     * Get the current context surface for
     * the active application.
     */
    suspend fun contextSurface(): ContextSurface?
}

/**
 * Host platform.
 */
enum class HostPlatform(val displayName: String) {
    MacOS("macOS"),
    Windows("Windows"),
    Linux("Linux"),
    Unknown("Unknown")
}

/**
 * Host agent state.
 */
enum class HostAgentState {
    Disconnected,
    Connecting,
    Connected,
    Authenticating,
    Authenticated,
    Error
}

/**
 * Supported host capabilities.
 */
enum class HostCapability {
    Mouse,
    Keyboard,
    Clipboard,
    MediaControl,
    AppList,
    ActiveApp,
    SystemCommands,
    BatteryState,
    AudioState,
    ScreenCapture,
    Notifications,
    FileOperations,
    SemanticCommands,
    ContextSurface
}

/**
 * Mouse button.
 */
enum class MouseButton {
    Left, Right, Middle
}

/**
 * Host keyboard key.
 */
enum class HostKey {
    A, B, C, D, E, F, G, H, I, J, K, L, M,
    N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    Num0, Num1, Num2, Num3, Num4, Num5,
    Num6, Num7, Num8, Num9,
    Space, Enter, Tab, Escape, Backspace, Delete,
    Up, Down, Left, Right,
    Home, End, PageUp, PageDown,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    Command, Option, Control, Shift, CapsLock
}

/**
 * Application info.
 */
data class AppInfo(
    val appId: String,
    val name: String,
    val bundleId: String? = null,
    val isRunning: Boolean = false,
    val isFocused: Boolean = false
)

/**
 * Media state.
 */
data class MediaState(
    val isPlaying: Boolean,
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val volume: Float = 0.5f,
    val isMuted: Boolean = false
)

/**
 * Media action.
 */
enum class MediaAction {
    Play, Pause, Stop, Next, Previous,
    VolumeUp, VolumeDown, Mute, Unmute,
    SetVolume
}

/**
 * System information.
 */
data class SystemInfo(
    val hostname: String,
    val platform: HostPlatform,
    val osVersion: String,
    val architecture: String,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val audioOutputDevice: String? = null
)

/**
 * System command.
 */
data class SystemCommand(
    val command: String,
    val args: List<String> = emptyList()
)

/**
 * System command result.
 */
data class SystemCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

/**
 * Context surface for the active application.
 */
data class ContextSurface(
    val appName: String,
    val actions: List<ContextAction>
)

/**
 * A context-aware action.
 */
data class ContextAction(
    val id: String,
    val label: String,
    val iconHint: String? = null,
    val action: UniversalAction
)

/**
 * Host connection result.
 */
sealed class HostConnectionResult {
    object Success : HostConnectionResult()
    data class Failed(val reason: String) : HostConnectionResult()
    data class AuthRequired(val pin: String) : HostConnectionResult()
}
