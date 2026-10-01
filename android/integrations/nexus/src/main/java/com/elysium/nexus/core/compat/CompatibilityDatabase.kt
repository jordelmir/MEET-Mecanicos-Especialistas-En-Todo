package com.elysium.nexus.core.compat

/**
 * The in-memory compatibility database.
 *
 * `MASTER_ORDER.md` §33 mandates a local, updateable
 * database of compatibility results. The schema is in
 * [CompatibilityResult]; the in-memory store is here.
 *
 * The full production database will be a Room / SQLite
 * layer in Phase 1+. The in-memory implementation in 0.9
 * is the seed: the schema, the query API, and the
 * validation that prevents `VERIFIED_LAB` records from
 * having failures. When the Room layer lands, the API
 * stays the same; only the implementation changes.
 *
 * ## Why in-memory in 0.9
 *
 * A SQLite layer is a big lift (Room + migration + SQL
 * queries) and is out of scope for the smallest
 * concrete sub-task that unlocks the most downstream
 * work. The schema + the query API are the parts the
 * `tools/compatibility-runner/` and the
 * `tools/hid-descriptor-validator/` consume. A
 * `List<CompatibilityResult>` behind a class with a
 * query API is enough.
 *
 * ## Thread safety
 *
 * The store is `@Synchronized` on every public method.
 * The Phase 1+ Room layer will be backed by SQLite and
 * has its own concurrency model; the synchronized
 * methods are a stopgap that makes the in-memory
 * implementation safe to share across the activity and
 * the future diagnostic service.
 */
class CompatibilityDatabase {

    private val records: MutableList<CompatibilityResult> = mutableListOf()

    /**
     * Add a [CompatibilityResult] to the database. The
     * record is validated against the §33 invariants
     * (already enforced in the data class) and against
     * the database-level invariant:
     *
     * > VERIFIED_LAB is the only state that does not
     * > require corroboration.
     *
     * Records that violate the invariant are rejected
     * with an [IllegalArgumentException]. The exception
     * is loud because the invariant is a release-quality
     * gate: a `VERIFIED_LAB` record with failures would
     * be a §33 violation in production.
     */
    @Synchronized
    fun add(record: CompatibilityResult) {
        // Defensive: the data class's `init` already
        // enforces this, but we re-check here so a
        // caller that bypasses the data class
        // constructor (e.g. a deserialiser) still gets
        // the guard.
        if (record.status == CompatibilityStatus.VERIFIED_LAB &&
            record.capabilitiesFailed.isNotEmpty()
        ) {
            throw IllegalArgumentException(
                "Cannot add VERIFIED_LAB record with failed capabilities: " +
                    "${record.deviceId}/${record.targetPlatform}"
            )
        }
        records.add(record)
    }

    /**
     * @return every record for [deviceId], in insertion
     *   order. The list is a defensive copy so callers
     *   cannot mutate the database's internal state.
     */
    @Synchronized
    fun byDevice(deviceId: String): List<CompatibilityResult> =
        records.filter { it.deviceId == deviceId }.toList()

    /**
     * @return every record for [targetPlatform], in
     *   insertion order. Used by the compatibility
     *   matrix UI (Phase 1+) and by the `tools/`
     *   runners.
     */
    @Synchronized
    fun byTarget(targetPlatform: String): List<CompatibilityResult> =
        records.filter { it.targetPlatform == targetPlatform }.toList()

    /**
     * @return every record with status [status], in
     *   insertion order.
     */
    @Synchronized
    fun byStatus(status: CompatibilityStatus): List<CompatibilityResult> =
        records.filter { it.status == status }.toList()

    /**
     * @return the *latest* record for the given
     *   `deviceId` + `targetPlatform` pair, or `null` if
     *   no record exists. The "latest" is the most
     *   recently added; the date field is informational
     *   only. This query is the one the diagnostic panel
     *   (§34) uses to show "what is the current state of
     *   this combination?".
     */
    @Synchronized
    fun latest(deviceId: String, targetPlatform: String): CompatibilityResult? =
        records.lastOrNull {
            it.deviceId == deviceId && it.targetPlatform == targetPlatform
        }

    /**
     * @return every record in the database, in insertion
     *   order. The list is a defensive copy.
     */
    @Synchronized
    fun all(): List<CompatibilityResult> = records.toList()

    /**
     * @return the number of records in the database.
     */
    @Synchronized
    fun size(): Int = records.size

    /**
     * Aggregate: how many records are at each status?
     * Used by the `tools/compatibility-runner/` and by
     * any future "compatibility dashboard" UI.
     */
    @Synchronized
    fun statusBreakdown(): Map<CompatibilityStatus, Int> {
        val out = mutableMapOf<CompatibilityStatus, Int>()
        for (status in CompatibilityStatus.values()) {
            out[status] = 0
        }
        for (r in records) {
            out[r.status] = (out[r.status] ?: 0) + 1
        }
        return out
    }
}
