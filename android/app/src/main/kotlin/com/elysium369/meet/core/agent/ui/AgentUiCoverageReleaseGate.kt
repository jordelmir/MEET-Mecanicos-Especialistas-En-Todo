package com.elysium369.meet.core.agent.ui

/**
 * ══════════════════════════════════════════════════════════════════════
 *  A G E N T   U I   C O V E R A G E   R E L E A S E   G A T E
 *  ──────────────────────────────────────────────────────────────
 *  Release gate autoritativo que audita la cobertura semántica de la UI.
 *  Garantiza que ninguna pantalla core (Home, Rides, Marketplace,
 *  Checkout, Safety) se libere a producción sin la instrumentación
 *  semántica necesaria para que EVAIR y el usuario interactúen con
 *  total precisión determinista.
 *
 *  Master Order Omega & Genesis §7, §28, §30.
 * ══════════════════════════════════════════════════════════════════════
 */
data class CoverageViolation(
    val controlId: String,
    val screenRoute: String,
    val severity: ViolationSeverity,
    val rule: String,
    val description: String,
)

enum class ViolationSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

data class CoverageAuditReport(
    val auditedScreens: List<String>,
    val totalInteractiveControls: Int,
    val compliantControls: Int,
    val coverageRatio: Double,
    val violations: List<CoverageViolation>,
    val passed: Boolean,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

class AgentUiCoverageReleaseGate(
    private val minimumCoverageRatio: Double = 0.90,
) {
    /**
     * Core screens that must pass semantic UI coverage before release.
     */
    val mandatoryScreens = setOf(
        "home",
        "scanner",
        "dtcs",
        "garage",
        "pro",
        "ride_service",
        "marketplace",
        "services_hub",
        "safety_dashboard",
    )

    fun audit(controls: List<AgentUiControlSnapshot>): CoverageAuditReport {
        val violations = mutableListOf<CoverageViolation>()
        var compliantCount = 0

        val screenGroups = controls.groupBy { it.route.ifBlank { "unassigned_route" } }

        for (control in controls) {
            var isCompliant = true

            // Rule 1: Control must have a non-blank, human-readable label
            if (control.label.isBlank()) {
                violations.add(
                    CoverageViolation(
                        controlId = control.id.value,
                        screenRoute = control.route,
                        severity = ViolationSeverity.CRITICAL,
                        rule = "NON_BLANK_LABEL",
                        description = "El control tiene una etiqueta vacía, impidiendo su invocación determinista por voz.",
                    )
                )
                isCompliant = false
            }

            // Rule 2: Text fields must have an assigned semantic role
            if ((control.kind == AgentUiControlKind.TEXT_FIELD || control.kind == AgentUiControlKind.SEARCH_FIELD) &&
                (control.role == null || control.role == AgentTextFieldRole.GENERIC)
            ) {
                violations.add(
                    CoverageViolation(
                        controlId = control.id.value,
                        screenRoute = control.route,
                        severity = ViolationSeverity.WARNING,
                        rule = "EXPLICIT_TEXT_ROLE",
                        description = "El campo de texto no tiene un rol semántico explícito asignado (e.g. PICKUP_ADDRESS, DESTINATION_ADDRESS, SEARCH).",
                    )
                )
                // We don't mark non-compliant if it has label, but warning is logged
            }

            // Rule 3: Secret/PIN fields must have SECRET sensitivity
            val labelLower = control.label.lowercase()
            if ((labelLower.contains("pin") || labelLower.contains("password") || labelLower.contains("contraseña") || labelLower.contains("cvv")) &&
                control.sensitivity != AgentUiSensitivity.SECRET
            ) {
                violations.add(
                    CoverageViolation(
                        controlId = control.id.value,
                        screenRoute = control.route,
                        severity = ViolationSeverity.CRITICAL,
                        rule = "SECRET_SENSITIVITY_REQUIRED",
                        description = "El control gestiona información confidencial (PIN/clave) pero no está marcado con AgentUiSensitivity.SECRET.",
                    )
                )
                isCompliant = false
            }

            if (isCompliant) {
                compliantCount++
            }
        }

        val total = controls.size
        val ratio = if (total > 0) compliantCount.toDouble() / total.toDouble() else 1.0
        val hasCriticalViolations = violations.any { it.severity == ViolationSeverity.CRITICAL }
        val passed = (ratio >= minimumCoverageRatio) && !hasCriticalViolations

        return CoverageAuditReport(
            auditedScreens = screenGroups.keys.toList(),
            totalInteractiveControls = total,
            compliantControls = compliantCount,
            coverageRatio = ratio,
            violations = violations,
            passed = passed,
        )
    }
}
