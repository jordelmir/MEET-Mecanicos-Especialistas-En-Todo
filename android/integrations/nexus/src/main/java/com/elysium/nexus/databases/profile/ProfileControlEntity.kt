package com.elysium.nexus.databases.profile

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect

/**
 * The Room entity for a single [com.elysium.nexus.core.profile.ControlElement]
 * within a [com.elysium.nexus.core.profile.Profile].
 *
 * The composite primary key `(profileId, controlId)` matches
 * the domain invariant: every control lives in exactly one
 * profile, and the `controlId` is unique within that profile
 * (the editor allocates fresh ids on add). The foreign key
 * to [ProfileEntity] is `CASCADE` on delete: removing a
 * profile removes its controls.
 *
 * ## Why an `ordering` column and not `zIndex`
 *
 * The domain [com.elysium.nexus.core.profile.ControlElement.zIndex]
 * is the *draw* order. We store the row's position in the
 * controls list as `ordering`; the domain `zIndex` is
 * recomputed at read time. The reason: SQLite is a row
 * store, not a list store, so we materialise the order in
 * a column to avoid `ORDER BY` on every read.
 *
 * In Phase 1.2 the mapping is
 * `zIndex = ordering` and vice-versa. If a future
 * contributor introduces *gaps* (e.g. so a new control
 * can be inserted between two existing ones without
 * re-numbering), the read path is
 * `ORDER BY ordering ASC` and the write path
 * re-numbers — the API contract stays the same.
 *
 * ## Why a composite index on `profileId`
 *
 * The `byId(profileId)` query is `SELECT * FROM
 * profile_control WHERE profileId = ? ORDER BY ordering
 * ASC`. The composite index `(profileId, ordering)`
 * covers both the WHERE and the ORDER BY in a single
 * B-tree walk.
 *
 * ## Why `binding` is a `String` and not a relation
 *
 * The closed set of [CanonicalBinding] variants
 * (Button, Stick, Trigger, Neutralize) is encoded as a
 * `String` via [ProfileConverters]. Normalising to a
 * relation table would require a join on every read; the
 * converter is a single string column. Adding a new
 * binding variant requires a converter branch, not a
 * schema migration.
 */
@Entity(
    tableName = "profile_control",
    primaryKeys = ["profileId", "controlId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["profileId", "ordering"],
            name = "idx_profile_control_ordering"
        )
    ]
)
data class ProfileControlEntity(
    /** The owning profile's id. */
    val profileId: Int,

    /** The control's domain id (unique within the profile). */
    val controlId: Int,

    /** The kind of control (Button, Stick, Trigger, Dpad, Touchpad). */
    val type: ControlType,

    /** The canonical binding; encoded via [ProfileConverters]. */
    val binding: CanonicalBinding,

    /** The visual bounds (where the control draws). */
    val visualBounds: NormalizedRect,

    /** The hit bounds (where the control consumes touches). */
    val hitBounds: NormalizedRect,

    /** Draw order; lower draws first. */
    val zIndex: Int,

    /** Visual rotation in degrees `[0, 360]`. */
    val rotation: Float,

    /** Visual opacity in `[0, 1]`. */
    val opacity: Float,

    /** Position within the profile's controls list (0-based). */
    val ordering: Int
)
