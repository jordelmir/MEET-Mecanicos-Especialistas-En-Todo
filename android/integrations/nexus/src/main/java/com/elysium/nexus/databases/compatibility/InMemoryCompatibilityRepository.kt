package com.elysium.nexus.databases.compatibility

import com.elysium.nexus.core.compat.CompatibilityResult
import com.elysium.nexus.core.compat.CompatibilityStatus
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * The in-memory implementation of
 * [CompatibilityRepository].
 *
 * The Room database is the production implementation,
 * but the Room layer requires a real `Context` to open
 * the SQLite file, so it cannot be unit-tested from the
 * JVM. The agent-memory rule is to "define a narrow
 * interface in the testable class and have a 5-line
 * hand-rolled impl for tests"; the in-memory
 * repository is the 5-line impl for the Room-backed
 * production repository.
 *
 * The in-memory implementation is also useful as a
 * *fallback* when the production code is running on a
 * device that does not have SQLite (e.g. an Android TV
 * with a corrupt database). The activity's Hilt graph
 * (Phase 1.1+) can supply the in-memory implementation
 * if the Room one fails to open.
 *
 * ## Thread safety
 *
 * The store is guarded by a [ReentrantReadWriteLock].
 * Reads (`byDevice`, `byStatus`, `latest`, `all`,
 * `count`, `statusBreakdown`) hold the read lock; the
 * single write (`add`) holds the write lock. This
 * matches the §31 "buffers acotados, no crear objetos
 * por cada muestra" rule for a small, mostly-read
 * database.
 */
class InMemoryCompatibilityRepository : CompatibilityRepository {

    private val records: MutableList<CompatibilityResult> = mutableListOf()
    private val lock = ReentrantReadWriteLock()

    override suspend fun add(record: CompatibilityResult) {
        lock.write {
            records.add(record)
        }
    }

    override suspend fun byDevice(deviceId: String): List<CompatibilityResult> = lock.read {
        records.filter { it.deviceId == deviceId }.toList()
    }

    override suspend fun byTarget(targetPlatform: String): List<CompatibilityResult> = lock.read {
        records.filter { it.targetPlatform == targetPlatform }.toList()
    }

    override suspend fun byStatus(status: CompatibilityStatus): List<CompatibilityResult> = lock.read {
        records.filter { it.status == status }.toList()
    }

    override suspend fun latest(
        deviceId: String,
        targetPlatform: String
    ): CompatibilityResult? = lock.read {
        records.lastOrNull {
            it.deviceId == deviceId && it.targetPlatform == targetPlatform
        }
    }

    override suspend fun all(): List<CompatibilityResult> = lock.read {
        records.toList()
    }

    override suspend fun count(): Int = lock.read {
        records.size
    }

    override suspend fun statusBreakdown(): Map<CompatibilityStatus, Int> = lock.read {
        val out = mutableMapOf<CompatibilityStatus, Int>()
        for (status in CompatibilityStatus.values()) {
            out[status] = 0
        }
        for (r in records) {
            out[r.status] = (out[r.status] ?: 0) + 1
        }
        out.toMap()
    }
}
