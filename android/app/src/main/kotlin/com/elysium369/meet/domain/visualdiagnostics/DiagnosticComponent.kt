package com.elysium369.meet.domain.visualdiagnostics

data class DiagnosticComponent(
    val id: String,
    val engineType: EngineType,
    val name: String,
    val category: ComponentCategory,
    val description: String,
    val location: String,
    val commonFailures: List<String>,
    val workshopTests: List<ComponentTest>,
    val repairFlow: List<RepairStep>,
    val specs: List<ComponentSpec>,
    val requiredTools: List<String>,
    val safetyWarnings: List<SafetyWarning>,
    val relatedPids: List<RelatedPid>,
    val relatedDtcs: List<RelatedDtc>,
    val position: ComponentPosition,
    val meshKey: String
)

enum class ComponentHealthStatus {
    OK,
    WARNING,
    CRITICAL
}

