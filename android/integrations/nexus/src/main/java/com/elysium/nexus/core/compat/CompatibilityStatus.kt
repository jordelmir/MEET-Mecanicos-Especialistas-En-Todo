package com.elysium.nexus.core.compat

/**
 * The six compatibility states a device + target combination
 * can be in, per `MASTER_ORDER.md` §33.
 *
 * ```
 * VERIFIED_LAB           — measured in the lab on the test matrix
 * VERIFIED_COMMUNITY     — reported by a community tester with evidence
 * PARTIALLY_VERIFIED     — some capabilities pass, some fail
 * UNVERIFIED             — claimed, not measured
 * REGRESSION             — previously passed, now fails
 * BLOCKED                — cannot work (e.g. REQUIRES_VENDOR_LICENSE)
 * ```
 *
 * The states are intentionally **not** boolean. A binary
 * "compatible / not compatible" loses the nuance §33 calls
 * for: a target that passes 6 of 7 capabilities is not the
 * same as one that passes 7 of 7, and a target that used to
 * pass and now fails (a regression) is more important than
 * a target that never passed. The granularity is part of
 * the "no silent claims" rule.
 *
 * ## What is not here
 *
 * The §33 spec also lists the data shape (`device model`,
 * `target`, `capabilities tested`, `evidence`, etc.). That
 * lives in [CompatibilityResult] — a record that carries
 * one [CompatibilityStatus] plus the data §33 mandates.
 */
enum class CompatibilityStatus {
    /** Measured in the lab on the §39 hardware matrix. */
    VERIFIED_LAB,

    /** Reported by a community tester, with evidence attached. */
    VERIFIED_COMMUNITY,

    /** Some capabilities pass, some fail; partial functionality. */
    PARTIALLY_VERIFIED,

    /**
     * Claimed but not measured. §33 says "no confundas reporte
     * comunitario con verificación interna" — this is the
     * bucket for claims that have no evidence.
     */
    UNVERIFIED,

    /**
     * Previously passed on this device + target, now fails.
     * The most actionable state: a release blocker if it
     * slips from `VERIFIED_LAB` to `REGRESSION`.
     */
    REGRESSION,

    /**
     * Cannot work as designed. The most common cause is a
     * target that requires a vendor license we do not have
     * (e.g. PS4_LICENSED without a Sony dev kit). The
     * descriptor is documented; the runtime is empty.
     */
    BLOCKED;

    /**
     * @return the state's "confidence weight" used in
     *   aggregate reports. VERIFIED_LAB is the only state
     *   that counts as "we know this works". The other
     *   states are not failures per se, but they are
     *   *claims*, not measurements.
     */
    fun isMeasurement(): Boolean = this == VERIFIED_LAB
}
