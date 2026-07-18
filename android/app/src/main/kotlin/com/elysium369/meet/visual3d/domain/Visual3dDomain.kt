package com.elysium369.meet.visual3d.domain

import kotlinx.serialization.Serializable

/**
 * ═══════════════════════════════════════════════════════════════════
 * ELYSIUM VANGUARD — Visual 3D Diagnostic Domain Models
 * ═══════════════════════════════════════════════════════════════════
 *
 * Defines the core domain types for the intelligent 3D diagnostic
 * engine. These models power the visual representation of component
 * health, measurement tracking, and hypothesis-driven diagnostics.
 *
 * HARD RULE: A DTC alone NEVER marks a component as CONFIRMED_FAULT.
 *            Only a physical measurement can elevate state beyond SUSPECT.
 * ═══════════════════════════════════════════════════════════════════
 */

// ─── Component Diagnostic State Machine ─────────────────────────

/**
 * State machine for a 3D component's diagnostic lifecycle.
 * Transitions follow strict evidence rules:
 *
 *  UNKNOWN → NORMAL              (no DTC, no anomaly)
 *  UNKNOWN → RELATED_TO_DTC      (DTC activates this component's circuit)
 *  RELATED_TO_DTC → SUSPECT      (AI or heuristic raises probability)
 *  SUSPECT → TEST_REQUIRED       (user acknowledges need for measurement)
 *  TEST_REQUIRED → TEST_IN_PROGRESS  (user begins measurement)
 *  TEST_IN_PROGRESS → TEST_PASSED    (measurement within spec)
 *  TEST_IN_PROGRESS → TEST_FAILED    (measurement out of spec)
 *  TEST_FAILED → CONFIRMED_FAULT     (physical evidence confirms failure)
 *  CONFIRMED_FAULT → REPLACED        (user marks as replaced)
 *  REPLACED → VALIDATED_REPAIR       (post-repair scan confirms fix)
 *
 *  ⛔ NEVER: RELATED_TO_DTC → CONFIRMED_FAULT (skips physical test)
 */
@Serializable
enum class Visual3dComponentState {
    UNKNOWN,
    NORMAL,
    RELATED_TO_DTC,
    SUSPECT,
    TEST_REQUIRED,
    TEST_IN_PROGRESS,
    TEST_PASSED,
    TEST_FAILED,
    CONFIRMED_FAULT,
    REPLACED,
    VALIDATED_REPAIR;

    /** Whether this state allows purchasing a replacement part */
    fun canPurchasePart(): Boolean = this in setOf(
        CONFIRMED_FAULT,
        REPLACED,
        VALIDATED_REPAIR
    )

    /** Whether this state requires a physical measurement before proceeding */
    fun requiresPhysicalTest(): Boolean = this in setOf(
        RELATED_TO_DTC,
        SUSPECT,
        TEST_REQUIRED
    )

    /** Human-readable label in Spanish */
    fun labelEs(): String = when (this) {
        UNKNOWN -> "Desconocido"
        NORMAL -> "Normal"
        RELATED_TO_DTC -> "Relacionado al DTC"
        SUSPECT -> "Sospechoso"
        TEST_REQUIRED -> "Prueba requerida"
        TEST_IN_PROGRESS -> "Prueba en progreso"
        TEST_PASSED -> "Prueba aprobada"
        TEST_FAILED -> "Prueba fallida"
        CONFIRMED_FAULT -> "Falla confirmada"
        REPLACED -> "Reemplazado"
        VALIDATED_REPAIR -> "Reparación validada"
    }
}

// ─── Location Confidence ────────────────────────────────────────

/**
 * How confident we are about the 3D position of a component.
 * If not EXACT_OEM, the UI MUST display:
 *   "Ubicación aproximada basada en plantilla genérica. Verifique con manual del vehículo."
 */
@Serializable
enum class LocationConfidence {
    EXACT_OEM,
    HIGH,
    MEDIUM,
    LOW,
    GENERIC_TEMPLATE,
    UNKNOWN;

    fun requiresDisclaimer(): Boolean = this != EXACT_OEM
}

// ─── Vehicle Representation Fidelity Level ──────────────────────

/**
 * Defines the geometric fidelity level of a vehicle's 3D representation.
 * This is NOT about component-level location confidence (see [LocationConfidence]),
 * but about the ENTIRE vehicle model's relationship to the actual OEM geometry.
 *
 * ⛔ HARD RULE: Only [OEM_EXACT] may be displayed without a disclaimer.
 *              All other levels MUST show their [disclaimerEs] in the UI at all times.
 *              Never present approximate as exact.
 *
 * Levels (highest to lowest fidelity):
 *
 *  OEM_EXACT            — Verified OEM CAD data (VIN-matched)
 *  OEM_PARTIAL          — Some OEM data, but incomplete coverage
 *  PARAMETRIC_ENGINEERING — Generated from parametric specs (dimensions, topology)
 *  CONCEPTUAL           — Schematic/topological representation, no geometric accuracy
 *  VISUAL_REFERENCE     — Purely illustrative, no engineering correspondence
 */
@Serializable
enum class VehicleRepresentationLevel {
    /**
     * Verified OEM CAD data matched to VIN/model/year/engine.
     * NO disclaimer needed. Full engineering trust.
     */
    OEM_EXACT,

    /**
     * Partial OEM data (e.g., engine bay is OEM-accurate but chassis is generic).
     * Disclaimer required for uncovered zones.
     */
    OEM_PARTIAL,

    /**
     * Generated entirely from parametric engineering specifications.
     * Geometry is reasonable but not OEM-verified.
     */
    PARAMETRIC_ENGINEERING,

    /**
     * Schematic or topological view. Relative positions are meaningful
     * (e.g., "fuel pump is near fuel tank") but absolute coordinates are not.
     */
    CONCEPTUAL,

    /**
     * Purely visual/illustrative model. Do not use for diagnostics or repair guidance.
     * Positions, sizes, and shapes have NO engineering correspondence.
     */
    VISUAL_REFERENCE;

    /** Whether a disclaimer banner MUST be shown over the 3D viewport */
    fun requiresDisclaimer(): Boolean = this != OEM_EXACT

    /** The mandatory disclaimer text in Spanish for this representation level */
    fun disclaimerEs(): String = when (this) {
        OEM_EXACT -> "" // No disclaimer needed
        OEM_PARTIAL ->
            "⚠️ Representación parcial OEM. Algunas zonas del vehículo utilizan geometría genérica. " +
            "Verifique con el manual del fabricante para componentes fuera de la zona verificada."
        PARAMETRIC_ENGINEERING ->
            "⚠️ Modelo paramétrico generado. Las posiciones y geometría son aproximaciones de ingeniería, " +
            "no datos OEM verificados. Utilice como referencia de ubicación relativa."
        CONCEPTUAL ->
            "⚠️ Vista conceptual/esquemática. Las ubicaciones representan relaciones topológicas del sistema, " +
            "no posiciones geométricas reales. No utilice para localización física de componentes."
        VISUAL_REFERENCE ->
            "⚠️ Representación visual de referencia. Las ubicaciones y geometría NO corresponden " +
            "al vehículo OEM. Solo para orientación visual general."
    }

    /** Short badge label for compact UI indicators */
    fun badgeLabelEs(): String = when (this) {
        OEM_EXACT -> "OEM ✓"
        OEM_PARTIAL -> "OEM PARCIAL"
        PARAMETRIC_ENGINEERING -> "PARAMÉTRICO"
        CONCEPTUAL -> "CONCEPTUAL"
        VISUAL_REFERENCE -> "REF. VISUAL"
    }

    /** Color hint (ARGB Long) for the badge background */
    fun badgeColorHex(): Long = when (this) {
        OEM_EXACT -> 0xFF4CAF50              // Green — trusted
        OEM_PARTIAL -> 0xFFFFC107            // Amber — partial trust
        PARAMETRIC_ENGINEERING -> 0xFF2196F3 // Blue — engineered
        CONCEPTUAL -> 0xFFFF9800             // Orange — approximate
        VISUAL_REFERENCE -> 0xFF9E9E9E       // Gray — illustrative only
    }

    companion object {
        /**
         * Safety rule: When displaying a specific OEM vehicle (VIN/model selected)
         * with a model that isn't OEM-verified, this is the maximum level allowed.
         * The UI must downgrade to this or lower and show the disclaimer.
         */
        val DEFAULT_FOR_UNVERIFIED_OEM = VISUAL_REFERENCE
    }
}

// ─── Measurement Types ──────────────────────────────────────────

@Serializable
enum class MeasurementType {
    VOLTAGE,
    RESISTANCE,
    CURRENT,
    PRESSURE,
    TEMPERATURE,
    FREQUENCY,
    DUTY_CYCLE,
    CONTINUITY,
    VOLTAGE_DROP,
    VISUAL_INSPECTION,
    FLOW_RATE,
    COMPRESSION,
    VACUUM;

    fun unitLabel(): String = when (this) {
        VOLTAGE -> "V"
        RESISTANCE -> "Ω"
        CURRENT -> "A"
        PRESSURE -> "psi"
        TEMPERATURE -> "°C"
        FREQUENCY -> "Hz"
        DUTY_CYCLE -> "%"
        CONTINUITY -> "Ω"
        VOLTAGE_DROP -> "V"
        VISUAL_INSPECTION -> ""
        FLOW_RATE -> "L/min"
        COMPRESSION -> "psi"
        VACUUM -> "inHg"
    }
}

@Serializable
enum class MeasurementResult {
    PASS,
    FAIL,
    INCONCLUSIVE;

    fun labelEs(): String = when (this) {
        PASS -> "Dentro de especificación"
        FAIL -> "Fuera de especificación"
        INCONCLUSIVE -> "Resultado inconcluso"
    }
}

// ─── Component Measurement Record ───────────────────────────────

/**
 * A physical measurement taken by the user on a component.
 * This is the ONLY evidence that can transition a component
 * from SUSPECT to CONFIRMED_FAULT (via TEST_FAILED).
 */
@Serializable
data class ComponentMeasurement(
    val componentId: String,
    val measurementType: MeasurementType,
    val measuredValue: Double,
    val unit: String,
    val expectedMin: Double? = null,
    val expectedMax: Double? = null,
    val result: MeasurementResult,
    val notes: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
    val toolUsed: String = ""
) {
    /** Human-readable summary for the UI and AI injection */
    fun toSummary(): String = buildString {
        append("${measurementType.name}: ${measuredValue}${unit}")
        if (expectedMin != null && expectedMax != null) {
            append(" (esperado: ${expectedMin}-${expectedMax}${unit})")
        }
        append(" → ${result.labelEs()}")
        if (notes.isNotBlank()) append(" | $notes")
    }
}

// ─── Component Hypothesis ───────────────────────────────────────

/**
 * AI-calculated diagnostic hypothesis for a 3D component.
 * Probability ranges from 0.0 (no evidence) to 1.0 (confirmed fault).
 */
@Serializable
data class ComponentHypothesis(
    val componentId: String,
    val componentName: String,
    val state: Visual3dComponentState,
    val probability: Double,
    val reasoning: String,
    val recommendedAction: String,
    val relatedDtcs: List<String> = emptyList(),
    val measurements: List<ComponentMeasurement> = emptyList(),
    val canPurchase: Boolean = state.canPurchasePart(),
    val purchaseBlockReason: String? = if (!state.canPurchasePart() && state.requiresPhysicalTest()) {
        "Se requiere una prueba física antes de recomendar compra de esta pieza."
    } else null
)

// ─── DTC-to-Component Mapping Entry ─────────────────────────────

/**
 * Maps a DTC code to its related 3D components.
 * Primary components are directly referenced by the DTC circuit.
 * Secondary components are in the same system and may be root cause.
 */
@Serializable
data class DtcComponentMapping(
    val dtcCode: String,
    val description: String,
    val primaryComponentIds: List<String>,
    val secondaryComponentIds: List<String>,
    val systemAffected: String,
    val initialProbabilities: Map<String, Double> = emptyMap()
)

// ─── 3D Component Visual Info ───────────────────────────────────

/**
 * The visual representation state for a single 3D component,
 * combining its identity, position confidence, diagnostic state,
 * and any measurements or hypotheses associated with it.
 */
data class Visual3dComponentInfo(
    val id: String,
    val name: String,
    val nameEs: String,
    val system: String,
    val state: Visual3dComponentState = Visual3dComponentState.UNKNOWN,
    val locationConfidence: LocationConfidence = LocationConfidence.GENERIC_TEMPLATE,
    val hypothesis: ComponentHypothesis? = null,
    val measurements: List<ComponentMeasurement> = emptyList(),
    val relatedDtcs: List<String> = emptyList()
) {
    /** Color hint for the 3D renderer based on diagnostic state */
    fun diagnosticColorHex(): Long = when (state) {
        Visual3dComponentState.UNKNOWN -> 0xFF808080        // Gray
        Visual3dComponentState.NORMAL -> 0xFF4CAF50         // Green
        Visual3dComponentState.RELATED_TO_DTC -> 0xFFFFC107 // Amber
        Visual3dComponentState.SUSPECT -> 0xFFFF9800        // Orange
        Visual3dComponentState.TEST_REQUIRED -> 0xFFE91E63  // Pink
        Visual3dComponentState.TEST_IN_PROGRESS -> 0xFF2196F3 // Blue
        Visual3dComponentState.TEST_PASSED -> 0xFF4CAF50    // Green
        Visual3dComponentState.TEST_FAILED -> 0xFFF44336    // Red
        Visual3dComponentState.CONFIRMED_FAULT -> 0xFFD32F2F // Dark Red
        Visual3dComponentState.REPLACED -> 0xFF9E9E9E       // Gray
        Visual3dComponentState.VALIDATED_REPAIR -> 0xFF00E676 // Bright Green
    }
}
