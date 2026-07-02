package com.elysium369.meet.diagnostic

import kotlinx.serialization.Serializable

/**
 * Comparador Before/After entre dos snapshots.
 *
 * Regla crítica del producto: `canDeclareRepaired` es FALSE por defecto.
 * Sólo es true si TODAS estas condiciones se cumplen:
 * 1. DTC activo no volvió.
 * 2. Monitor readiness completa.
 * 3. Valores live dentro de rango.
 * 4. (Opcional) prueba de manejo cumple condición del freeze frame.
 *
 * Si la provenance es SinEnlace o Simulated, nunca declarar reparado.
 */
@Serializable
data class ComparisonResult(
    val beforeId: String,
    val afterId: String,
    val newDtcs: List<String> = emptyList(),
    val clearedDtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
    val readinessBefore: Map<String, Boolean> = emptyMap(),
    val readinessAfter: Map<String, Boolean> = emptyMap(),
    val readinessCompleted: Boolean = false,
    val voltageDelta: Double? = null,
    val coolantTempDelta: Double? = null,
    val rpmDelta: Double? = null,
    val stftDelta: Double? = null,
    val ltftDelta: Double? = null,
    val freezeFrameConditionMet: Boolean = false,
    val roadTestPassed: Boolean = false,
    val provenanceBefore: DiagnosticProvenance,
    val provenanceAfter: DiagnosticProvenance,
    val conclusion: ComparisonConclusion
) {
    /**
     * Regla de cierre:
     * - canDeclareRepaired = true SÓLO si todas las condiciones se cumplen.
     * - Si provenanceBefore o provenanceAfter no es Real, nunca.
     */
    val canDeclareRepaired: Boolean
        get() = conclusion == ComparisonConclusion.REPAIRED &&
                provenanceBefore is DiagnosticProvenance.Real &&
                provenanceAfter is DiagnosticProvenance.Real &&
                clearedDtcs.isNotEmpty() &&
                newDtcs.isEmpty() &&
                readinessCompleted &&
                freezeFrameConditionMet &&
                roadTestPassed
}

@Serializable
enum class ComparisonConclusion {
    /** Comparación indica reparación confirmada (caller aún debe verificar canDeclareRepaired). */
    REPAIRED,

    /** DTC nuevo apareció: probablemente la acción empeoró algo. */
    REGRESSION,

    /** DTC activo se mantuvo. Acción no efectiva. */
    NO_CHANGE,

    /** Datos insuficientes para concluir. */
    INSUFFICIENT_DATA,

    /** Provenance no confiable (SinEnlace / Simulated / Manual). */
    UNVERIFIED
}

/**
 * Lógica de comparación before/after.
 *
 * El caller decide si pasa roadTestPassed y freezeFrameConditionMet.
 */
object BeforeAfterComparator {

    fun compare(
        before: DiagnosticSnapshot,
        after: DiagnosticSnapshot,
        roadTestPassed: Boolean = false,
        freezeFrameConditionMet: Boolean = false,
        liveValueInRange: Boolean = true
    ): ComparisonResult {
        val beforeSet = before.dtcsActive.toSet()
        val afterSet = after.dtcsActive.toSet()
        val newDtcs = afterSet - beforeSet
        val clearedDtcs = beforeSet - afterSet

        val readinessBefore = before.readiness
        val readinessAfter = after.readiness
        val readinessCompleted = readinessAfter.isNotEmpty() &&
            readinessAfter.values.all { it }

        val provenanceBefore = before.provenance
        val provenanceAfter = after.provenance

        val conclusion = when {
            provenanceBefore !is DiagnosticProvenance.Real ||
                provenanceAfter !is DiagnosticProvenance.Real ->
                ComparisonConclusion.UNVERIFIED
            newDtcs.isNotEmpty() ->
                ComparisonConclusion.REGRESSION
            clearedDtcs.isEmpty() && beforeSet.isNotEmpty() ->
                ComparisonConclusion.NO_CHANGE
            clearedDtcs.isEmpty() && beforeSet.isEmpty() ->
                ComparisonConclusion.INSUFFICIENT_DATA
            else -> ComparisonConclusion.REPAIRED
        }

        return ComparisonResult(
            beforeId = before.id,
            afterId = after.id,
            newDtcs = newDtcs.toList(),
            clearedDtcs = clearedDtcs.toList(),
            pendingDtcs = after.dtcsPending,
            readinessBefore = readinessBefore,
            readinessAfter = readinessAfter,
            readinessCompleted = readinessCompleted && liveValueInRange,
            voltageDelta = (after.ecuVoltage ?: 0.0) - (before.ecuVoltage ?: 0.0),
            coolantTempDelta = (after.coolantTempC ?: 0.0) - (before.coolantTempC ?: 0.0),
            rpmDelta = (after.rpm ?: 0.0) - (before.rpm ?: 0.0),
            stftDelta = (after.fuelTrimStft ?: 0.0) - (before.fuelTrimStft ?: 0.0),
            ltftDelta = (after.fuelTrimLtft ?: 0.0) - (before.fuelTrimLtft ?: 0.0),
            freezeFrameConditionMet = freezeFrameConditionMet,
            roadTestPassed = roadTestPassed,
            provenanceBefore = provenanceBefore,
            provenanceAfter = provenanceAfter,
            conclusion = conclusion
        )
    }
}