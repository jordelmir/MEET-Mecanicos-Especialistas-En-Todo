package com.elysium.nexus.databases.compatibility

import com.elysium.nexus.core.compat.CompatibilityResult
import com.elysium.nexus.core.compat.CompatibilityStatus

/**
 * The repository interface for the §33 compatibility
 * database.
 *
 * The repository is the *domain-side* API. The
 * persistence side (Room) is hidden behind this
 * interface, so the production implementation
 * ([RoomCompatibilityRepository]) and the test
 * implementation ([InMemoryCompatibilityRepository])
 * are interchangeable. The activity / service that
 * consumes the database does not know which one it
 * has.
 *
 * ## Why an interface
 *
 * The agent-memory rule "Wiring Android Context-dependent
 * classes into JVM-testable code" applies: the Room
 * implementation requires a real `Context` (to open
 * the SQLite file), so it cannot be unit-tested from
 * the JVM. The in-memory implementation is the
 * JVM-testeable stand-in; the production wiring uses
 * the Room one. The interface is the seam.
 *
 * ## Why the conversion to / from `CompatibilityEntity` lives here
 *
 * The domain shape is [CompatibilityResult]; the
 * persistence shape is [CompatibilityEntity]. The
 * conversion is straightforward (lists ↔ semicolon-
 * separated strings, no field transformation), so it
 * lives in the repository, not in the DAO. The DAO
 * speaks only `CompatibilityEntity`; the repository
 * speaks only `CompatibilityResult`. The interface
 * is the boundary.
 */
interface CompatibilityRepository {

    /**
     * Add a [CompatibilityResult] to the database.
     * Validated by the [CompatibilityResult] data
     * class before reaching the repository. The
     * repository does not re-validate; the conversion
     * to an entity is total.
     */
    suspend fun add(record: CompatibilityResult)

    /**
     * @return every record for [deviceId], oldest first.
     */
    suspend fun byDevice(deviceId: String): List<CompatibilityResult>

    /**
     * @return every record for [targetPlatform], oldest
     *   first.
     */
    suspend fun byTarget(targetPlatform: String): List<CompatibilityResult>

    /**
     * @return every record with status [status], oldest
     *   first.
     */
    suspend fun byStatus(status: CompatibilityStatus): List<CompatibilityResult>

    /**
     * @return the most recent record for the given
     *   device + target pair, or `null` if no record
     *   exists.
     */
    suspend fun latest(deviceId: String, targetPlatform: String): CompatibilityResult?

    /**
     * @return every record in the database, oldest
     *   first.
     */
    suspend fun all(): List<CompatibilityResult>

    /**
     * @return the number of records in the database.
     */
    suspend fun count(): Int

    /**
     * Aggregate: how many records are at each status?
     * Used by the future diagnostic dashboard.
     */
    suspend fun statusBreakdown(): Map<CompatibilityStatus, Int>
}
