package com.elysium369.meet.domain.visualdiagnostics

data class VisualDiagnosticState(
    val selectedEngineType: EngineType = EngineType.L4,
    val selectedComponentId: String? = null,
    val selectedTab: VisualDiagnosticTab = VisualDiagnosticTab.MOTOR_3D,
    val isExplodedView: Boolean = false,
    val components: List<DiagnosticComponent> = emptyList(),
    val livePidValues: Map<String, String> = emptyMap(),
    val activeDtcs: Set<String> = emptySet(),
    val isConnected: Boolean = false,
    val error: String? = null
)

enum class VisualDiagnosticTab {
    MOTOR_3D,
    RELAY_FUSE_BOX,
    WIRING_HARNESS
}

