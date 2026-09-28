package com.elysium.nexus.databases.profile

import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A JVM-testeable fake of [ProfileDao].
 *
 * Room's generated implementation requires a real
 * `Context` (it opens a SQLite file). For unit tests
 * we substitute a fake that holds the rows in memory
 * but speaks the *same* interface as the real DAO.
 * The production [RoomProfileRepository] is the
 * thing under test; the fake is a test double for
 * its dependency.
 *
 * The fake's contract mirrors the real DAO's
 * semantics:
 *
 *  - `insertProfile` is REPLACE-on-conflict: a row
 *    with the same `id` overwrites the existing one.
 *  - `insertControls` is REPLACE-on-conflict on
 *    `(profileId, controlId)`.
 *  - `replaceControls` is `delete-then-insert`, all
 *    within the call (Room wraps the real one in a
 *    `@Transaction`).
 *  - `controlsForProfile` returns rows ordered by
 *    `ordering ASC` (matching the real query).
 *
 * Thread safety: the fake uses a
 * [java.util.concurrent.locks.ReentrantReadWriteLock]
 * with the same read/write pattern as
 * [InMemoryProfileRepository]. The Room DAO is
 * single-threaded by default (Room dispatches I/O to
 * its executor); the lock is a defensive guard for
 * tests that mix producers and consumers.
 */
class FakeProfileDao : ProfileDao {

    private val profiles: MutableMap<Int, ProfileEntity> = linkedMapOf()
    private val controls: MutableMap<Pair<Int, Int>, ProfileControlEntity> = linkedMapOf()
    private val lock = ReentrantReadWriteLock()

    override suspend fun insertProfile(entity: ProfileEntity) {
        lock.write { profiles[entity.id] = entity }
    }

    override suspend fun profileById(id: Int): ProfileEntity? = lock.read {
        profiles[id]
    }

    override suspend fun allProfiles(): List<ProfileEntity> = lock.read {
        profiles.values.toList()
    }

    override suspend fun firstProfile(): ProfileEntity? = lock.read {
        profiles.values.firstOrNull()
    }

    override suspend fun countProfiles(): Int = lock.read {
        profiles.size
    }

    override suspend fun maxProfileId(): Int? = lock.read {
        profiles.keys.maxOrNull()
    }

    override suspend fun deleteProfile(id: Int) {
        lock.write {
            profiles.remove(id)
            controls.keys.removeAll { it.first == id }
        }
    }

    override suspend fun insertControl(entity: ProfileControlEntity) {
        lock.write {
            controls[entity.profileId to entity.controlId] = entity
        }
    }

    override suspend fun insertControls(entities: List<ProfileControlEntity>) {
        lock.write {
            for (e in entities) {
                controls[e.profileId to e.controlId] = e
            }
        }
    }

    override suspend fun deleteControlsForProfile(profileId: Int) {
        lock.write {
            controls.keys.removeAll { it.first == profileId }
        }
    }

    override suspend fun controlsForProfile(profileId: Int): List<ProfileControlEntity> = lock.read {
        controls.values
            .filter { it.profileId == profileId }
            .sortedBy { it.ordering }
    }

    override suspend fun replaceControls(profileId: Int, controls: List<ProfileControlEntity>) {
        lock.write {
            this.controls.keys.removeAll { it.first == profileId }
            for (e in controls) {
                this.controls[e.profileId to e.controlId] = e
            }
        }
    }

    // ---- Helpers for tests ----

    /** The number of profile rows currently in the fake. */
    fun profileCount(): Int = lock.read { profiles.size }

    /** The number of control rows currently in the fake. */
    fun controlCount(): Int = lock.read { controls.size }

    /** A snapshot of every control row, for assertions. */
    fun allControlRows(): List<ProfileControlEntity> = lock.read {
        controls.values.toList()
    }
}

/** A minimal factory for tests. */
internal fun makeProfileEntity(
    id: Int = 0,
    name: String = "test",
    author: String = "tester",
    version: Int = 1,
    createdAt: Long = 0L,
    updatedAt: Long = 0L
): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    author = author,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** A minimal factory for tests. */
internal fun makeControlEntity(
    profileId: Int = 0,
    controlId: Int = 0,
    type: ControlType = ControlType.Button,
    binding: CanonicalBinding = CanonicalBinding.Neutralize,
    visualBounds: NormalizedRect = NormalizedRect.CENTERED_SMALL,
    hitBounds: NormalizedRect = visualBounds,
    zIndex: Int = 0,
    rotation: Float = 0f,
    opacity: Float = 1f,
    ordering: Int = 0
): ProfileControlEntity = ProfileControlEntity(
    profileId = profileId,
    controlId = controlId,
    type = type,
    binding = binding,
    visualBounds = visualBounds,
    hitBounds = hitBounds,
    zIndex = zIndex,
    rotation = rotation,
    opacity = opacity,
    ordering = ordering
)
