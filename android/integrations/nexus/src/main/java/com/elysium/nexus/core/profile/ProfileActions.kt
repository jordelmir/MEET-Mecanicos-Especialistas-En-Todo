package com.elysium.nexus.core.profile

/**
 * The §15 pure profile operations.
 *
 * The module exposes two operations beyond
 * construction and the editor's per-control
 * mutations:
 *
 *  - [duplicate]: produce a fresh [Profile] that
 *    is a copy of [source] with a new `id` and
 *    fresh timestamps. The new profile's
 *    controls are *new* elements (new `id` per
 *    control) so the editor can edit the
 *    duplicate without touching the source.
 *  - [rename]: produce a copy of [source] with
 *    a new `name` and a fresh `updatedAt`.
 *
 * Both operations are total: the input [Profile]
 * has already been validated by its `init` block;
 * the output [Profile] is also validated. A blank
 * new name throws [IllegalArgumentException].
 *
 * ## Why pure functions and not methods on [Profile]
 *
 * The operations need an `id` and `now` (the new
 * timestamps); neither belongs on the data class
 * (the data class is the document, the operations
 * are the *transforms* on the document). Pure
 * functions are the smallest type that captures
 * "transform with an `id` and a `now`".
 *
 * ## Why a new id for every control on duplicate
 *
 * The [Profile.controls] list is keyed by the
 * control's `id`. The editor selects a control
 * by id; the repository's `upsert` matches on
 * id. If duplicate copied the source's control
 * ids, editing the duplicate would change the
 * source (the repository's `upsert` would see
 * the same ids and overwrite the source). The
 * duplicate is a *new* profile; the controls
 * are *new* elements.
 */
object ProfileActions {

    /**
     * Duplicate [source] into a fresh [Profile] with
     * `id = newId` and fresh timestamps. The new
     * profile's controls are *new* elements
     * (each control's `id` is offset by a stable
     * per-profile delta so the duplicate's ids do
     * not collide with the source's).
     *
     * The [now] parameter is the wall-clock
     * millis at the moment of duplicate; both
     * `createdAt` and `updatedAt` are set to it.
     */
    fun duplicate(
        source: Profile,
        newId: Int,
        now: Long
    ): Profile {
        val newControls = source.controls.map { control ->
            // The control id is the source's id
            // plus a stable offset. The offset is
            // the source's profile id scaled by
            // 1000 (room for 1000 controls per
            // profile). The new id is in a
            // different "namespace" from the
            // source's ids and from any other
            // duplicate's ids.
            val offset = source.id.toLong() * 1_000L
            val newControlId = (control.id + offset).toInt()
            control.copy(id = newControlId)
        }
        val baseName = source.name.ifBlank { "Profile" }
        return source.copy(
            id = newId,
            name = "$baseName (copy)",
            controls = newControls,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Rename [source] to [newName] with a fresh
     * [now] for `updatedAt`. The function is
     * total over the [Profile] domain; a blank
     * [newName] throws [IllegalArgumentException]
     * via the [Profile] `init` block.
     */
    fun rename(
        source: Profile,
        newName: String,
        now: Long
    ): Profile {
        return source.copy(
            name = newName,
            updatedAt = now
        )
    }
}
