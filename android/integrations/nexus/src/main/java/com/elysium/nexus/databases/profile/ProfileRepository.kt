package com.elysium.nexus.databases.profile

import com.elysium.nexus.core.profile.Profile

/**
 * The repository interface for the §15 profile database.
 *
 * The interface is the domain-side API. The
 * persistence side (Room) is hidden behind it so the
 * production implementation
 * ([RoomProfileRepository], Phase 1.2) and the test
 * implementation ([InMemoryProfileRepository], 1.1) are
 * interchangeable. The agent-memory rule applies:
 * Context-dependent persistence is behind a narrow
 * interface, the JVM-testeable stand-in is the
 * `InMemoryProfileRepository`.
 */
interface ProfileRepository {

    /**
     * Insert or replace a profile. The profile's `id`
     * is the primary key. A new profile is created if
     * the id is not in the database; an existing
     * profile is replaced.
     */
    suspend fun upsert(profile: Profile)

    /**
     * @return the profile with id [id], or `null` if
     *   no such profile exists.
     */
    suspend fun byId(id: Int): Profile?

    /**
     * @return every profile in the database, in
     *   insertion order.
     */
    suspend fun all(): List<Profile>

    /**
     * @return the first profile (the "default"), or
     *   `null` if the database is empty. Used by the
     *   activity on first launch.
     */
    suspend fun firstOrNull(): Profile?

    /**
     * @return the number of profiles in the database.
     */
    suspend fun count(): Int

    /**
     * @return the next available profile id. The
     * next id is `max(existing id) + 1`, or `0` if
     * the database is empty. Used by the "new
     * profile" UI to allocate a fresh id without
     * racing on `MAX(id)`.
     */
    suspend fun nextId(): Int

    /**
     * Delete the profile with id [id]. The CASCADE
     * foreign key on `profile_control` removes
     * every control row of the deleted profile.
     * If no profile with [id] exists, the call is
     * a no-op.
     */
    suspend fun delete(id: Int)
}
