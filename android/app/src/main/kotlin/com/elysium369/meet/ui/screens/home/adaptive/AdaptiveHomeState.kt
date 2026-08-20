package com.elysium369.meet.ui.screens.home.adaptive

import com.elysium369.meet.core.obd.ObdState

/**
 * MEET DHEA — Adaptive / Command Home Projection State.
 * Pure presentation state derived from verifiable domain truth without duplicating domain logic.
 */
data class AdaptiveHomeState(
    val vehicleSummary: HomeVehicleSummary?,
    val connectionSummary: HomeConnectionSummary,
    val prioritizedActions: List<HomeAction>,
    val diagnosticSummary: HomeDiagnosticSummary,
    val registeredModulesBySection: Map<HomeSectionCategory, List<HomeModuleItem>>,
    val userRoleTitle: String,
    val isLoading: Boolean = false
)

data class HomeVehicleSummary(
    val id: String,
    val displayName: String,
    val year: Int,
    val maskedVin: String,
    val plate: String,
    val healthPercent: Int,
    val mileageKm: Int? = null,
    val isImmoOnline: Boolean = true
)

data class HomeConnectionSummary(
    val state: ObdState,
    val protocol: String,
    val adapterVersion: String,
    val isCloneAdapter: Boolean,
    val batteryVoltage: String? = null
)

data class HomeDiagnosticSummary(
    val activeDtcsCount: Int,
    val pendingDtcsCount: Int,
    val permanentDtcsCount: Int,
    val monitorsReady: Int,
    val monitorsTotal: Int,
    val hasActiveEmergency: Boolean = false
)

enum class HomeSectionCategory(val title: String, val glyph: String) {
    NOW("AHORA", "⚡"),
    DIAGNOSTICS("DIAGNÓSTICO & CONTROL", "🩺"),
    VEHICLE("MI VEHÍCULO & HISTORIAL", "🚗"),
    SERVICES("SERVICIOS & RED DE ASISTENCIA", "🛠️"),
    TOOLS("HERRAMIENTAS AVANZADAS", "🔬"),
    PROFESSIONAL("VANGUARD PRO & FLOTA", "👑")
}

data class HomeModuleItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val destination: String,
    val section: HomeSectionCategory,
    val glyph: String,
    val isHighlight: Boolean = false,
    val badgeText: String? = null
)
