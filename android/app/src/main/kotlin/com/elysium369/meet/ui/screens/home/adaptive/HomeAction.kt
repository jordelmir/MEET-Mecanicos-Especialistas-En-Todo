package com.elysium369.meet.ui.screens.home.adaptive

enum class HomeActionPriority(val level: Int) {
    CRITICAL(1),
    HIGH(2),
    NORMAL(3),
    LOW(4)
}

enum class HomeActionCategory {
    OBD_CONNECTION,
    DIAGNOSTIC_FAULT,
    REPAIR_WORKFLOW,
    MAINTENANCE_DUE,
    INSPECTION_PENDING,
    SYSTEM_NOTICE
}

data class HomeAction(
    val id: String,
    val priority: HomeActionPriority,
    val category: HomeActionCategory,
    val title: String,
    val subtitle: String,
    val destination: String,
    val buttonLabel: String,
    val glyph: String,
    val evidenceRefs: List<String> = emptyList(),
    val isDismissible: Boolean = false
)
