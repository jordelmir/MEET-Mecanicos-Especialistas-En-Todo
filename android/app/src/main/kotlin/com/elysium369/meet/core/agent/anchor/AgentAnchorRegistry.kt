package com.elysium369.meet.core.agent.anchor

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * ══════════════════════════════════════════════════════════════════════
 *  S E M A N T I C   U I   A N C H O R S   (Master Order Omega §10, §11)
 *  ──────────────────────────────────────────────────────────────
 *  Anchors identify UI elements semantically, NEVER through raw screen
 *  coordinates, synthetic taps, or AccessibilityService hacks.
 *  Coordinates are ephemeral presentation data, registered dynamically.
 * ══════════════════════════════════════════════════════════════════════
 */
@JvmInline
value class AgentAnchorId(val value: String) {
    companion object {
        // OBD Scanner & Emissions Anchors
        val SCANNER_CONNECT = AgentAnchorId("scanner.connect")
        val SCANNER_TAB_DASHBOARD = AgentAnchorId("scanner.tab.dashboard")
        val SCANNER_TAB_DIAGNOSTIC = AgentAnchorId("scanner.tab.diagnostic")
        val SCANNER_TAB_TOOLS = AgentAnchorId("scanner.tab.tools")
        val SCANNER_TAB_MONITORS = AgentAnchorId("scanner.tab.monitors")
        val SCANNER_ADD_PID = AgentAnchorId("scanner.add_pid")
        val SCANNER_EMISSIONS_CARD = AgentAnchorId("scanner.emissions_card")
        val SCANNER_MODE06_CARD = AgentAnchorId("scanner.mode06_card")
        val SCANNER_O2_BURST_BUTTON = AgentAnchorId("scanner.o2_burst_button")

        // Mobility & Rides Anchors
        val RIDE_PICKUP = AgentAnchorId("ride.pickup")
        val RIDE_DESTINATION = AgentAnchorId("ride.destination")
        val RIDE_CONFIRM = AgentAnchorId("ride.confirm")
        val RIDE_CANCEL = AgentAnchorId("ride.cancel")

        // Garage & Vehicle Anchors
        val GARAGE_VEHICLE_CARD = AgentAnchorId("garage.vehicle_card")
        val GARAGE_SCAN_BUTTON = AgentAnchorId("garage.scan_button")
        val GARAGE_HISTORY_BUTTON = AgentAnchorId("garage.history_button")

        // Universal EVAIR Guide Trigger
        val EVAIR_TRIGGER = AgentAnchorId("evair.trigger")
    }
}

data class AnchorBounds(
    val id: AgentAnchorId,
    val boundsInRoot: Rect,
    val isVisible: Boolean = true,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
) {
    val centerX: Float get() = boundsInRoot.left + boundsInRoot.width / 2f
    val centerY: Float get() = boundsInRoot.top + boundsInRoot.height / 2f
    val width: Float get() = boundsInRoot.width
    val height: Float get() = boundsInRoot.height
}

/**
 * Thread-safe global registry of semantic UI anchors.
 * Receives real-time geometry from Compose layout passes.
 */
class AgentAnchorRegistry private constructor() {

    private val anchorsMap = ConcurrentHashMap<AgentAnchorId, AnchorBounds>()
    private val _anchorsFlow = MutableStateFlow<Map<AgentAnchorId, AnchorBounds>>(emptyMap())
    val anchorsFlow: StateFlow<Map<AgentAnchorId, AnchorBounds>> = _anchorsFlow.asStateFlow()

    fun registerAnchor(id: AgentAnchorId, bounds: Rect, isVisible: Boolean = true) {
        val entry = AnchorBounds(id, bounds, isVisible)
        anchorsMap[id] = entry
        _anchorsFlow.update { HashMap(anchorsMap) }
    }

    fun unregisterAnchor(id: AgentAnchorId) {
        anchorsMap.remove(id)
        _anchorsFlow.update { HashMap(anchorsMap) }
    }

    fun getAnchor(id: AgentAnchorId): AnchorBounds? = anchorsMap[id]

    fun isAnchorVisible(id: AgentAnchorId): Boolean {
        val anchor = anchorsMap[id] ?: return false
        return anchor.isVisible && anchor.width > 0f && anchor.height > 0f
    }

    fun clear() {
        anchorsMap.clear()
        _anchorsFlow.value = emptyMap()
    }

    companion object {
        val default: AgentAnchorRegistry by lazy { AgentAnchorRegistry() }
    }
}
