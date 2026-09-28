package com.elysium.nexus.core.compat

/**
 * A single compatibility result, per `MASTER_ORDER.md` §33.
 *
 * Each result is a record of "we tested device X on
 * target Y for capability Z, here's what happened". The
 * record carries the data §33 mandates (device, target,
 * capabilities tested / passed / failed, latency,
 * tester, evidence, confidence, status) and a
 * [CompatibilityStatus] that is the headline of the
 * record.
 *
 * ## Why a data class
 *
 * The §33 spec is explicit about which fields are in a
 * result. A data class makes the contract grep-able and
 * gives the test suite a single type to assert against.
 * It is the database's row.
 *
 * ## Why the fields are nullable
 *
 * `latencyP50`, `latencyP95`, `evidence` are nullable
 * because the §33 spec says they are present "when
 * available", not "always". A `VERIFIED_COMMUNITY` report
 * may not include latency (the community tester did not
 * have a `LatencyTracker`); an `UNVERIFIED` claim has
 * no evidence at all. The fields are nullable so the
 * shape is honest about what we know.
 */
data class CompatibilityResult(
    /** A stable identifier for the device under test. */
    val deviceId: String,

    /** The device's model name, e.g. "Honor Magic V2". */
    val deviceModel: String,

    /** The Android version on the device, e.g. "14". */
    val androidVersion: String,

    /** The OEM firmware version, e.g. "MagicOS 7.2". */
    val oemFirmware: String,

    /** The transport used for the test, e.g. "BluetoothClassicHID". */
    val transport: String,

    /** The target platform, e.g. "ANDROID_TV". */
    val targetPlatform: String,

    /** The target's OS / firmware, e.g. "Android TV 14". */
    val targetOsFirmware: String,

    /** The game / application the test was run against, if any. */
    val game: String?,

    /** The capabilities the test exercised. */
    val capabilitiesTested: List<String>,

    /** The capabilities that passed. */
    val capabilitiesPassed: List<String>,

    /** The capabilities that failed. */
    val capabilitiesFailed: List<String>,

    /** p50 latency, in nanoseconds, if measured. */
    val latencyP50Ns: Long?,

    /** p95 latency, in nanoseconds, if measured. */
    val latencyP95Ns: Long?,

    /** The tester (lab or community). */
    val tester: String,

    /** The test date in ISO-8601 (YYYY-MM-DD). */
    val date: String,

    /** A pointer to evidence (log file, screenshot, video). */
    val evidence: String?,

    /**
     * The test's confidence, in `[0, 100]`. 100 is "we
     * ran the full §39 matrix on this exact device +
     * target". 0 is "we are guessing". The number is a
     * field, not a derived value, because some tests are
     * partial by design (a community tester may not own
     * the lab equipment).
     */
    val confidence: Int,

    /**
     * The headline status. The convention is
     * `status == VERIFIED_LAB` iff
     * `capabilitiesFailed.isEmpty() && confidence >= 80
     * && tester == "lab"`. The database enforces this
     * convention in [CompatibilityDatabase.add]; manual
     * edits that violate it are flagged at the next
     * audit.
     */
    val status: CompatibilityStatus
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must be non-blank." }
        require(capabilitiesTested.isNotEmpty()) {
            "capabilitiesTested must be non-empty (per §33)."
        }
        require(confidence in 0..100) {
            "confidence must be in [0, 100] (got $confidence)."
        }
        // Defensive: a record marked VERIFIED_LAB should not
        // contain failures. The database enforces this in
        // `add`; we re-enforce here so a hand-built record
        // that violates the convention is caught at the
        // constructor.
        if (status == CompatibilityStatus.VERIFIED_LAB) {
            require(capabilitiesFailed.isEmpty()) {
                "VERIFIED_LAB must have no failed capabilities."
            }
            require(confidence >= 80) {
                "VERIFIED_LAB requires confidence >= 80 (got $confidence)."
            }
        }
    }
}
