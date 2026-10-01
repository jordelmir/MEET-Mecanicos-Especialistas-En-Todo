package com.elysium.nexus.databases.profile

import com.elysium.nexus.core.profile.Profile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test


/**
 * Tests for [InMemoryProfileRepository] — the JVM-testeable
 * stand-in for the future Room implementation.
 */
class InMemoryProfileRepositoryTest {

    @Test
    fun emptyRepositoryHasZeroCount() = runTest {
        val repo = InMemoryProfileRepository()
        assertEquals(0, repo.count())
        assertNull(repo.firstOrNull())
    }

    @Test
    fun upsertInsertsNewProfile() = runTest {
        val repo = InMemoryProfileRepository()
        val p = Profile.defaultProfile(id = 0, now = 1000L)
        repo.upsert(p)
        assertEquals(1, repo.count())
        assertEquals(p, repo.byId(0))
    }

    @Test
    fun upsertReplacesExistingProfile() = runTest {
        val repo = InMemoryProfileRepository()
        val p1 = Profile.defaultProfile(id = 0, now = 1000L)
        val p2 = p1.copy(name = "Renamed", updatedAt = 2000L)
        repo.upsert(p1)
        repo.upsert(p2)
        assertEquals(1, repo.count())
        assertEquals("Renamed", repo.byId(0)?.name)
    }

    @Test
    fun byIdReturnsNullForMissing() = runTest {
        val repo = InMemoryProfileRepository()
        assertNull(repo.byId(42))
    }

    @Test
    fun firstOrNullReturnsTheFirstProfile() = runTest {
        val repo = InMemoryProfileRepository()
        repo.upsert(Profile.defaultProfile(id = 1, now = 1000L))
        repo.upsert(Profile.defaultProfile(id = 2, now = 2000L))
        val first = repo.firstOrNull()
        assertNotNull(first)
        // Insertion order: 1, 2. firstOrNull returns the
        // first inserted (id = 1).
        assertEquals(1, first!!.id)
    }

    @Test
    fun allReturnsAllProfiles() = runTest {
        val repo = InMemoryProfileRepository()
        repo.upsert(Profile.defaultProfile(id = 1, now = 1000L))
        repo.upsert(Profile.defaultProfile(id = 2, now = 2000L))
        repo.upsert(Profile.defaultProfile(id = 3, now = 3000L))
        assertEquals(3, repo.all().size)
        assertEquals(listOf(1, 2, 3), repo.all().map { it.id })
    }

    @Test
    fun nextIdIsZeroOnEmpty() = runTest {
        val repo = InMemoryProfileRepository()
        assertEquals(0, repo.nextId())
    }

    @Test
    fun nextIdIsMaxPlusOne() = runTest {
        val repo = InMemoryProfileRepository()
        repo.upsert(Profile.defaultProfile(id = 0, now = 0L))
        repo.upsert(Profile.defaultProfile(id = 5, now = 0L))
        repo.upsert(Profile.defaultProfile(id = 3, now = 0L))
        assertEquals(6, repo.nextId())
    }

    @Test
    fun deleteRemovesProfile() = runTest {
        val repo = InMemoryProfileRepository()
        repo.upsert(Profile.defaultProfile(id = 0, now = 0L))
        repo.upsert(Profile.defaultProfile(id = 1, now = 0L))
        repo.delete(0)
        assertEquals(1, repo.count())
        assertNull(repo.byId(0))
        assertNotNull(repo.byId(1))
    }

    @Test
    fun deleteMissingIdIsNoOp() = runTest {
        val repo = InMemoryProfileRepository()
        repo.upsert(Profile.defaultProfile(id = 0, now = 0L))
        repo.delete(99)
        assertEquals(1, repo.count())
    }
}
