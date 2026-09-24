package com.elysium369.meet.core.emissions.domain

/**
 * Metrological origin of diagnostic or emissions evidence.
 */
enum class EvidenceOrigin {
    ECU_DIRECT,
    ECU_MONITOR,
    PHYSICAL_GAS_ANALYZER,
    PHYSICS_DERIVED,
    MODEL_ESTIMATED,
    USER_DECLARED
}

/**
 * Strict truth classification for metrology.
 * UNKNOWN must never be coerced to 0, PASS, or NORMAL.
 * MEASURED can only originate from direct physical sources.
 */
enum class TruthClass {
    MEASURED,
    REPORTED,
    DERIVED,
    ESTIMATED,
    UNKNOWN,
    NOT_SUPPORTED,
    INVALID
}

/**
 * Regulatory or diagnostic evaluation verdict.
 */
enum class Evaluation {
    PASS,
    FAIL,
    INCONCLUSIVE,
    NOT_APPLICABLE
}

enum class FuelType {
    GASOLINE,
    DIESEL,
    ETHANOL_E85,
    CNG,
    LPG,
    HYBRID_GASOLINE,
    ELECTRIC,
    UNKNOWN
}

enum class EngineCycle {
    FOUR_STROKE,
    TWO_STROKE,
    ROTARY_WANKEL,
    UNKNOWN
}
