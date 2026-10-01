package com.elysium.nexus.databases.compatibility

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * The Room DAO for the §33 compatibility database.
 *
 * The DAO is the persistence-side API. The
 * [CompatibilityRepository] in the same package is the
 * domain-side API; the production implementation
 * ([RoomCompatibilityRepository]) delegates to this DAO.
 *
 * The DAO is a `Room` interface annotated with
 * `@Dao`. Room generates the implementation at compile
 * time (via the KSP processor added in Phase 1.0).
 * The generated code is in `build/generated/ksp/.../`.
 *
 * ## Why suspend functions
 *
 * Room's generated queries are synchronous on the
 * thread they are called on. We use `suspend` to push
 * the I/O off the main thread. Room internally
 * dispatches the query to its executor and resumes the
 * coroutine when the result is ready.
 *
 * ## Why `@Insert(onConflict = REPLACE)`
 *
 * The §33 spec allows a record to be re-measured (the
 * same device + target combination is tested again
 * with a new date). The DAO treats the new record as
 * a separate row (the `id` is auto-generated), so the
 * `onConflict` policy is for the *uniqueness* of the
 * primary key — which is `REPLACE` because we do not
 * have a natural key. The repository layer enforces
 * the "latest wins" semantic by querying the table
 * with an `ORDER BY date DESC LIMIT 1`.
 */
@Dao
interface CompatibilityDao {

    /**
     * Insert a record. The auto-generated `id` is
     * returned; the entity's `id` field is ignored on
     * insert (Room uses the `autoGenerate = true`
     * strategy to assign a new one).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CompatibilityEntity): Long

    /**
     * @return every record for [deviceId], in insertion
     *   order (oldest first). Used by
     *   [CompatibilityRepository.byDevice].
     */
    @Query("SELECT * FROM compatibility WHERE deviceId = :deviceId ORDER BY id ASC")
    suspend fun byDevice(deviceId: String): List<CompatibilityEntity>

    /**
     * @return every record for [targetPlatform], in
     *   insertion order. Used by
     *   [CompatibilityRepository.byTarget].
     */
    @Query("SELECT * FROM compatibility WHERE targetPlatform = :targetPlatform ORDER BY id ASC")
    suspend fun byTarget(targetPlatform: String): List<CompatibilityEntity>

    /**
     * @return the most recent record for the given
     *   device + target pair, or `null` if no record
     *   exists. The "most recent" is by `date DESC`; the
     *   `id` is the tie-breaker. The composite index on
     *   `(deviceId, targetPlatform, date)` covers the
     *   query in a single B-tree walk.
     */
    @Query(
        "SELECT * FROM compatibility " +
            "WHERE deviceId = :deviceId AND targetPlatform = :targetPlatform " +
            "ORDER BY date DESC, id DESC LIMIT 1"
    )
    suspend fun latest(deviceId: String, targetPlatform: String): CompatibilityEntity?

    /**
     * @return every record in the database, in insertion
     *   order. Used by [CompatibilityRepository.all] and
     *   by the future diagnostic dump.
     */
    @Query("SELECT * FROM compatibility ORDER BY id ASC")
    suspend fun all(): List<CompatibilityEntity>

    /**
     * @return the number of records in the database.
     *   Cheap (covered by SQLite's `COUNT(*)`).
     */
    @Query("SELECT COUNT(*) FROM compatibility")
    suspend fun count(): Int
}
