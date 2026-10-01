package com.elysium.nexus.databases.profile

import com.elysium.nexus.core.profile.Profile
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * The in-memory implementation of [ProfileRepository].
 *
 * Phase 1.1 ships this implementation. Phase 1.2
 * introduces the Room-backed
 * [RoomProfileRepository]; the interface stays the
 * same, only the implementation changes.
 *
 * ## Why an in-memory store in 1.1
 *
 * The 1.1 deliverable is the data model + the
 * `EditorCanvas` Compose composable + the wiring into
 * `MainActivity`. The persistence layer (Room) is
 * added in 1.2. The in-memory store is the seam: the
 * production wiring uses Room in 1.2, the JVM tests
 * use this in-memory impl. For 1.1 the activity's
 * "default profile" lives in this in-memory store; the
 * state does not survive a process death. The editor
 * is the focus of 1.1; persistence is the focus of 1.2.
 *
 * ## Thread safety
 *
 * The store is guarded by a [ReentrantReadWriteLock].
 * Reads (`byId`, `all`, `firstOrNull`, `count`) hold
 * the read lock; `upsert` holds the write lock. A
 * profile list is small (typically 1) so the lock is
 * uncontended in practice.
 */
class InMemoryProfileRepository : ProfileRepository {

    private val profiles: MutableList<Profile> = mutableListOf()
    private val lock = ReentrantReadWriteLock()

    override suspend fun upsert(profile: Profile) {
        lock.write {
            val existing = profiles.indexOfFirst { it.id == profile.id }
            if (existing >= 0) {
                profiles[existing] = profile
            } else {
                profiles.add(profile)
            }
        }
    }

    override suspend fun byId(id: Int): Profile? = lock.read {
        profiles.firstOrNull { it.id == id }
    }

    override suspend fun all(): List<Profile> = lock.read {
        profiles.toList()
    }

    override suspend fun firstOrNull(): Profile? = lock.read {
        profiles.firstOrNull()
    }

    override suspend fun count(): Int = lock.read {
        profiles.size
    }

    override suspend fun nextId(): Int = lock.read {
        (profiles.maxOfOrNull { it.id } ?: -1) + 1
    }

    override suspend fun delete(id: Int) {
        lock.write {
            profiles.removeAll { it.id == id }
        }
    }
}
