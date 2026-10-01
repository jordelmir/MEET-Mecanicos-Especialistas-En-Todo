package com.elysium.nexus.core.profile

/**
 * A user-authored profile.
 *
 * `MASTER_ORDER.md` §15 says "Un perfil solo podrá
 * declarar: Controles, Mappings, Curvas, Gestos
 * permitidos, Tema, Metadatos." A profile is a
 * document that:
 *
 *  - lists the [ControlElement]s the user has placed
 *    on the touch surface;
 *  - declares metadata (name, author, timestamps);
 *  - has a version number for migrations;
 *  - is signed (the §15 "Firmar perfiles" — Phase 1.2+).
 *
 * ## Why a `List<ControlElement>` and not a `Set`
 *
 * The order of controls in the list is the draw order
 * (low `zIndex` first, high `zIndex` last). The editor
 * uses the list's index to compute `bringToFront`. A
 * `Set` would lose the order.
 *
 * ## Why a `List` and not a `Map<Int, ControlElement>`
 *
 * A `Map` would make the editor's "select all buttons"
 * query O(1), but the editor's hot path is the
 * per-frame draw, not the per-query selection. A list
 * is simpler and the O(n) scan for the 5-30 elements
 * a typical profile has is in the noise. Phase 1.2+
 * may revisit if a profile grows to hundreds of
 * elements.
 *
 * ## Why `createdAt` / `updatedAt` are `Long`
 *
 * Wall-clock millis since epoch. The platform's
 * `System.currentTimeMillis()` is the source. The
 * `Instant` / `ZonedDateTime` types are a Java 8
 * surface that is not on the minimum API the project
 * targets (the brand uses min SDK 26, which has
 * `java.time` via desugaring — Phase 1.2 promotes if
 * needed). For 1.1 `Long` is enough.
 */
data class Profile(
    /** A stable identifier. UUID in Phase 1.2; Int placeholder. */
    val id: Int,

    /** The human-readable name, e.g. "Elysium Nexus Default". */
    val name: String,

    /** The author. "system" for the default profile. */
    val author: String,

    /** The control elements in draw order. */
    val controls: List<ControlElement>,

    /** The schema version of the profile document. */
    val version: Int = CURRENT_VERSION,

    /** Wall-clock millis when the profile was created. */
    val createdAt: Long,

    /** Wall-clock millis when the profile was last edited. */
    val updatedAt: Long
) {
    init {
        require(id >= 0) { "id must be non-negative (got $id)." }
        require(name.isNotBlank()) { "name must be non-blank." }
        require(author.isNotBlank()) { "author must be non-blank." }
        require(version >= 1) { "version must be >= 1 (got $version)." }
        require(createdAt >= 0) { "createdAt must be non-negative." }
        require(updatedAt >= createdAt) {
            "updatedAt ($updatedAt) must be >= createdAt ($createdAt)."
        }
    }

    /**
     * @return a copy of this profile with [control] added
     *   at the end of the list and `updatedAt` set to
     *   [now]. Used by the editor's "add" action.
     */
    fun withControlAdded(control: ControlElement, now: Long): Profile =
        copy(controls = controls + control, updatedAt = now)

    /**
     * @return a copy of this profile with the control
     *   whose `id` matches [controlId] replaced by
     *   [updated], and `updatedAt` set to [now]. Used
     *   by the editor's drag / resize / rotate
     *   actions.
     */
    fun withControlReplaced(
        controlId: Int,
        updated: ControlElement,
        now: Long
    ): Profile = copy(
        controls = controls.map { if (it.id == controlId) updated else it },
        updatedAt = now
    )

    /**
     * @return a copy of this profile with the control
     *   whose `id` matches [controlId] removed, and
     *   `updatedAt` set to [now]. Used by the editor's
     *   "delete" action.
     */
    fun withControlRemoved(controlId: Int, now: Long): Profile =
        copy(controls = controls.filterNot { it.id == controlId }, updatedAt = now)

    companion object {
        /** The current schema version. Bumped on breaking changes. */
        const val CURRENT_VERSION: Int = 1

        /**
         * The factory for the "Elysium Nexus Default"
         * profile that ships with the app. It has a
         * single [CanonicalBinding.Neutralize] button
         * at the centre. The activity loads this on
         * first launch (when the database is empty)
         * and persists it.
         */
        fun defaultProfile(
            id: Int = 0,
            now: Long = System.currentTimeMillis()
        ): Profile = Profile(
            id = id,
            name = "Elysium Nexus Default",
            author = "system",
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect.CENTERED_SMALL,
                    binding = CanonicalBinding.Neutralize
                )
            ),
            version = CURRENT_VERSION,
            createdAt = now,
            updatedAt = now
        )
    }
}
