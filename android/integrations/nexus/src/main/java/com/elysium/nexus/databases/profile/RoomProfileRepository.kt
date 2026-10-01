package com.elysium.nexus.databases.profile

import com.elysium.nexus.core.profile.ControlElement
import com.elysium.nexus.core.profile.Profile

/**
 * The Room-backed production implementation of
 * [ProfileRepository].
 *
 * The repository delegates every method to the
 * [ProfileDao]. The conversion from [Profile] /
 * [ControlElement] (domain) to [ProfileEntity] /
 * [ProfileControlEntity] (persistence) is in
 * [toEntity] / [toControlEntity] / [toProfile]; the
 * conversion is total and free of side effects.
 *
 * The class is open for testing (a test could subclass
 * and override the DAO), but the standard test path
 * uses [InMemoryProfileRepository] because the Room
 * database requires a real `Context` to open. The
 * `RoomProfileRepository` itself is exercised end-to-end
 * on the emulator in the §45 release-gate.
 *
 * ## Why `upsert` replaces the whole control set
 *
 * A `Profile` is a *document*; the editor modifies it
 * through `withControlReplaced` / `withControlAdded` /
 * `withControlRemoved`, which each return a new
 * `Profile` with the entire updated `controls` list.
 * The repository treats the document as the source of
 * truth: the new controls list replaces the old one in
 * a single transaction
 * ([ProfileDao.replaceControls]).
 *
 * This pattern is slightly heavier than "update the one
 * changed row", but it makes the editor's *intent*
 * obvious: a save is a save, not a delta. It also
 * sidesteps the "field-level race" where two editor
 * windows both update the same control.
 */
class RoomProfileRepository(
    private val dao: ProfileDao
) : ProfileRepository {

    override suspend fun upsert(profile: Profile) {
        dao.insertProfile(toEntity(profile))
        val rows = profile.controls.mapIndexed { index, control ->
            toControlEntity(profileId = profile.id, ordering = index, control = control)
        }
        dao.replaceControls(profileId = profile.id, controls = rows)
    }

    override suspend fun byId(id: Int): Profile? {
        val header = dao.profileById(id) ?: return null
        val rows = dao.controlsForProfile(id)
        return toProfile(header, rows)
    }

    override suspend fun all(): List<Profile> {
        val headers = dao.allProfiles()
        return headers.map { header ->
            val rows = dao.controlsForProfile(header.id)
            toProfile(header, rows)
        }
    }

    override suspend fun firstOrNull(): Profile? {
        val header = dao.firstProfile() ?: return null
        val rows = dao.controlsForProfile(header.id)
        return toProfile(header, rows)
    }

    override suspend fun count(): Int = dao.countProfiles()

    override suspend fun nextId(): Int = (dao.maxProfileId() ?: -1) + 1

    override suspend fun delete(id: Int) {
        dao.deleteProfile(id)
    }

    /**
     * Convert a [Profile] (domain) to its persistence
     * header shape.
     */
    private fun toEntity(profile: Profile): ProfileEntity = ProfileEntity(
        id = profile.id,
        name = profile.name,
        author = profile.author,
        version = profile.version,
        createdAt = profile.createdAt,
        updatedAt = profile.updatedAt
    )

    /**
     * Convert a [ControlElement] to its persistence
     * shape, materialising the `ordering` field from
     * the list index.
     */
    private fun toControlEntity(
        profileId: Int,
        ordering: Int,
        control: ControlElement
    ): ProfileControlEntity = ProfileControlEntity(
        profileId = profileId,
        controlId = control.id,
        type = control.type,
        binding = control.binding,
        visualBounds = control.visualBounds,
        hitBounds = control.hitBounds,
        zIndex = control.zIndex,
        rotation = control.rotation,
        opacity = control.opacity,
        ordering = ordering
    )

    /**
     * Convert a header + child rows back into the
     * domain [Profile]. The controls are returned in
     * `ordering` order, which is the draw order.
     */
    private fun toProfile(
        header: ProfileEntity,
        rows: List<ProfileControlEntity>
    ): Profile = Profile(
        id = header.id,
        name = header.name,
        author = header.author,
        controls = rows
            .sortedBy { it.ordering }
            .map { row ->
                ControlElement(
                    id = row.controlId,
                    type = row.type,
                    visualBounds = row.visualBounds,
                    hitBounds = row.hitBounds,
                    zIndex = row.zIndex,
                    rotation = row.rotation,
                    opacity = row.opacity,
                    binding = row.binding
                )
            },
        version = header.version,
        createdAt = header.createdAt,
        updatedAt = header.updatedAt
    )
}
