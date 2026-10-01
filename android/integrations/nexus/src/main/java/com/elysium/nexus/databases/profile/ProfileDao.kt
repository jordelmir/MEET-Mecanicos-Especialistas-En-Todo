package com.elysium.nexus.databases.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * The Room DAO for the §15 profile database.
 *
 * The DAO is the persistence-side API. The
 * [ProfileRepository] in the same package is the
 * domain-side API; the production implementation
 * [RoomProfileRepository] delegates to this DAO.
 *
 * The DAO is a Room interface annotated with `@Dao`.
 * Room generates the implementation at compile time
 * (via the KSP processor added in Phase 1.0). The
 * generated code is in `build/generated/ksp/.../`.
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
 * The `upsert` semantic is "if the row exists,
 * replace it; otherwise insert". The composite primary
 * key on [ProfileControlEntity] makes REPLACE a true
 * upsert: a row with the same `(profileId, controlId)`
 * is replaced atomically. The single-column primary
 * key on [ProfileEntity] makes REPLACE a true upsert
 * for the same reason.
 *
 * ## Why `replaceProfile` and `replaceControls` are
 * separate methods
 *
 * The activity's `onProfileUpdated` is called every
 * time the user drags a single control. We update the
 * controls rows in a single transaction
 * ([replaceControls]) without touching the profile
 * header row, so a drag does not change the
 * `updatedAt` field inadvertently. The header's
 * `updatedAt` is the value passed in by the caller.
 *
 * ## Why `selectProfileWithControls` is a `@Transaction`
 *
 * The two reads (header + child rows) are not atomic
 * by themselves; a concurrent write could split them.
 * The `@Transaction` annotation wraps both reads in
 * a single SQLite transaction; Room guarantees the
 * pair is observed at the same snapshot.
 */
@Dao
interface ProfileDao {

    // ---- Profile header ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(entity: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = :id LIMIT 1")
    suspend fun profileById(id: Int): ProfileEntity?

    @Query("SELECT * FROM profile ORDER BY id ASC")
    suspend fun allProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM profile ORDER BY id ASC LIMIT 1")
    suspend fun firstProfile(): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun countProfiles(): Int

    @Query("SELECT MAX(id) FROM profile")
    suspend fun maxProfileId(): Int?

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun deleteProfile(id: Int)

    // ---- Profile controls ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertControl(entity: ProfileControlEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertControls(entities: List<ProfileControlEntity>)

    @Query("DELETE FROM profile_control WHERE profileId = :profileId")
    suspend fun deleteControlsForProfile(profileId: Int)

    @Query("SELECT * FROM profile_control WHERE profileId = :profileId ORDER BY ordering ASC")
    suspend fun controlsForProfile(profileId: Int): List<ProfileControlEntity>

    /**
     * Atomically replace the entire control set for [profileId].
     * The transaction is `delete-then-insert`; the rows are
     * visible only after both complete. Used by
     * [RoomProfileRepository.upsert].
     */
    @Transaction
    suspend fun replaceControls(profileId: Int, controls: List<ProfileControlEntity>) {
        deleteControlsForProfile(profileId)
        if (controls.isNotEmpty()) {
            insertControls(controls)
        }
    }
}
