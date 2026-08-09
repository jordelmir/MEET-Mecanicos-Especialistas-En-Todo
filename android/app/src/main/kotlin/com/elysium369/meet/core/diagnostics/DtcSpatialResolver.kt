package com.elysium369.meet.core.diagnostics

enum class DiagnosticSpatialSystem {
    POWERTRAIN_ENGINE,
    TRANSMISSION,
    CHASSIS,
    BRAKES_STEERING,
    BODY_ELECTRICAL,
    RESTRAINTS,
    COMMUNICATION_NETWORK,
    UNIVERSAL,
}

data class DiagnosticSpatialProjection(
    val primarySystem: DiagnosticSpatialSystem,
    val relatedSystems: Set<DiagnosticSpatialSystem>,
    val relationNotice: String =
        "Relación diagnóstica orientativa: no confirma por sí sola una pieza dañada.",
)

/**
 * Maps a finding to the vehicle system that should be inspected first.
 * It deliberately does not diagnose a failed part: that requires vehicle,
 * circuit and physical-test evidence from the knowledge graph/procedure.
 */
object DtcSpatialResolver {
    fun resolve(code: String?, moduleName: String? = null): DiagnosticSpatialProjection {
        val normalizedCode = code.orEmpty().trim().uppercase()
        val module = moduleName.orEmpty().trim().uppercase()

        val primary = when {
            module.contains("TCM") || module.contains("TRANSM") -> DiagnosticSpatialSystem.TRANSMISSION
            module.contains("ABS") || module.contains("BRAKE") -> DiagnosticSpatialSystem.BRAKES_STEERING
            module.contains("SRS") || module.contains("AIRBAG") -> DiagnosticSpatialSystem.RESTRAINTS
            module.contains("BCM") || module.contains("BODY") -> DiagnosticSpatialSystem.BODY_ELECTRICAL
            normalizedCode.startsWith("P07") || normalizedCode.startsWith("P17") ->
                DiagnosticSpatialSystem.TRANSMISSION
            normalizedCode.startsWith("C") -> DiagnosticSpatialSystem.CHASSIS
            normalizedCode.startsWith("B") -> DiagnosticSpatialSystem.BODY_ELECTRICAL
            normalizedCode.startsWith("U") -> DiagnosticSpatialSystem.COMMUNICATION_NETWORK
            normalizedCode.startsWith("P") -> DiagnosticSpatialSystem.POWERTRAIN_ENGINE
            else -> DiagnosticSpatialSystem.UNIVERSAL
        }

        val related = when (primary) {
            DiagnosticSpatialSystem.COMMUNICATION_NETWORK -> setOf(
                DiagnosticSpatialSystem.BODY_ELECTRICAL,
                DiagnosticSpatialSystem.POWERTRAIN_ENGINE,
            )
            DiagnosticSpatialSystem.RESTRAINTS -> setOf(DiagnosticSpatialSystem.BODY_ELECTRICAL)
            DiagnosticSpatialSystem.CHASSIS -> setOf(DiagnosticSpatialSystem.BRAKES_STEERING)
            else -> emptySet()
        }
        return DiagnosticSpatialProjection(primary, related)
    }
}
