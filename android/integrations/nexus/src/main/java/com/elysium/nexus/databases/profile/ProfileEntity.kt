package com.elysium.nexus.databases.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The Room entity for the §15 profile header row.
 *
 * One row per [com.elysium.nexus.core.profile.Profile]. The
 * controls that the profile contains are stored in
 * [ProfileControlEntity], one row per control, with a
 * foreign key to this entity. The two-table layout is
 * chosen over a single-blob row because:
 *
 *  - The editor typically updates one control at a time
 *    (drag, resize, rotate, opacity). With a single blob
 *    column, every edit would have to rewrite the whole
 *    JSON; with a per-control row, the edit rewrites a
 *    single row.
 *  - The `Mapping and Profile Engine` (Phase 1.2+) reads
 *    the controls in draw order. A column for the
 *    `ordering` field on the child table preserves the
 *    `zIndex` ordering without sorting on read.
 *  - A future "import from JSON" feature can split a
 *    document into rows transactionally.
 *
 * ## Why `id: Int` and not `String` (UUID)
 *
 * The domain [com.elysium.nexus.core.profile.Profile.id]
 * is `Int` in Phase 1.1. Phase 1.2+ may promote to UUID;
 * the migration is `Migration(1, 2)` in
 * `MIGRATIONS.md`. The Room primary key is the *domain*
 * id, not an auto-generated surrogate, because the
 * repository's `upsert(profile)` semantics is "replace
 * by id".
 *
 * ## Why no `createdAt` / `updatedAt` indices
 *
 * The profile list is typically 1-10 entries; the
 * ordering and filtering are done in memory. We add
 * indices when the table grows past 1000 rows (the
 * §15 "library" milestone).
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    /** Domain primary key. `Int` in 1.1; promoted to UUID in 1.2+ if §15 changes. */
    @PrimaryKey
    val id: Int,

    /** Human-readable name, e.g. "Elysium Nexus Default". */
    val name: String,

    /** Author of the profile. "system" for the default profile. */
    val author: String,

    /** Schema version of the profile document. */
    val version: Int,

    /** Wall-clock millis when the profile was created. */
    val createdAt: Long,

    /** Wall-clock millis when the profile was last edited. */
    val updatedAt: Long
)
