package com.elysium369.meet.core.emissions.physics

class DerivationCycleException(message: String) : IllegalStateException(message)

data class DerivationLineage(
    val target: String,
    val dependencies: Set<String> = emptySet(),
    val assumedValues: Map<String, Double> = emptyMap()
)

/**
 * Enforces strict acyclic lineage in physical and statistical derivations.
 * Prevents mathematical tautologies (e.g. deriving lambda from fuel rate that assumed lambda).
 */
class DerivationCycleGuard {

    fun assertAcyclic(target: String, dependencies: Set<String>, assumedAssumptions: Set<String> = emptySet()) {
        if (dependencies.contains(target)) {
            throw DerivationCycleException(
                "Circular derivation detected: '$target' cannot depend on itself directly or via lineage: $dependencies"
            )
        }
        if (assumedAssumptions.contains(target)) {
            throw DerivationCycleException(
                "Circular derivation detected: '$target' was already assumed as a fixed prior in this derivation chain"
            )
        }
    }

    fun assertLambdaNotDerivedFromAssumedLambdaFuelRate(fuelRateLineage: Set<String>) {
        if (fuelRateLineage.any { it.contains("ASSUMED_STOICHIOMETRIC_LAMBDA") || it.contains("LAMBDA_PRIOR") }) {
            throw DerivationCycleException(
                "Tautology violation: Lambda cannot be derived from a fuel rate that was calculated assuming stoichiometric lambda."
            )
        }
    }
}
