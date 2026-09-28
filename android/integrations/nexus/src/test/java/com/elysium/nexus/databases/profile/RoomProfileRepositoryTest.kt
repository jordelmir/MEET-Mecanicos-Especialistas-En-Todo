package com.elysium.nexus.databases.profile

import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlElement
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RoomProfileRepository] using a
 * [FakeProfileDao] as the persistence layer.
 *
 * The Room database requires a real Android `Context`
 * to open. The unit-test path substitutes the fake
 * DAO; the production code path uses the Room-
 * generated implementation. The repository's
 * contract is the same in both: the tests assert
 * the *conversion* between domain and persistence
 * shapes, and the *ordering* invariant (controls are
 * returned in `ordering ASC` order, which is the
 * draw order).
 */
class RoomProfileRepositoryTest {

    @Test
    fun emptyRepositoryHasZeroCount() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
        assertEquals(0, repo.count())
        assertNull(repo.firstOrNull())
    }

    @Test
    fun upsertInsertsProfileAndControls() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
        val profile = Profile.defaultProfile(id = 0, now = 1000L)
        repo.upsert(profile)
        assertEquals(1, repo.count())
        assertEquals(profile, repo.byId(0))
    }

    @Test
    fun upsertReplacesExistingProfile() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
        val p1 = Profile.defaultProfile(id = 0, now = 1000L)
        val p2 = p1.copy(name = "Renamed", updatedAt = 2000L)
        repo.upsert(p1)
        repo.upsert(p2)
        assertEquals(1, repo.count())
        assertEquals("Renamed", repo.byId(0)?.name)
    }

    @Test
    fun upsertReplacesAllControlsForProfile() = runTest {
        // The "replace all controls" semantic is the
        // core of the editor's "save" path: a save
        // replaces the whole list with the editor's
        // current state, not deltas.
        val repo = RoomProfileRepository(FakeProfileDao())
        val p1 = Profile.defaultProfile(id = 0, now = 1000L)
        repo.upsert(p1)
        assertEquals(1, repo.byId(0)?.controls?.size)

        val p2 = p1.copy(
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect.CENTERED_SMALL,
                    binding = CanonicalBinding.Neutralize
                ),
                ControlElement(
                    id = 1,
                    type = ControlType.Stick,
                    visualBounds = NormalizedRect.CENTERED_SMALL,
                    binding = CanonicalBinding.Stick(com.elysium.nexus.core.engine.StickSide.Left)
                )
            ),
            updatedAt = 2000L
        )
        repo.upsert(p2)
        val byId = repo.byId(0)!!
        assertEquals(2, byId.controls.size)
        assertEquals(ControlType.Stick, byId.controls[1].type)
    }

    @Test
    fun byIdReturnsNullForMissing() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
        assertNull(repo.byId(42))
    }

    @Test
    fun firstOrNullReturnsTheFirstProfile() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
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
        val repo = RoomProfileRepository(FakeProfileDao())
        repo.upsert(Profile.defaultProfile(id = 1, now = 1000L))
        repo.upsert(Profile.defaultProfile(id = 2, now = 2000L))
        repo.upsert(Profile.defaultProfile(id = 3, now = 3000L))
        val all = repo.all()
        assertEquals(3, all.size)
        assertEquals(listOf(1, 2, 3), all.map { it.id })
    }

    @Test
    fun controlsAreReturnedInOrderingAsc() = runTest {
        // The editor saves in the order the user added
        // the controls. A save with a different
        // ordering (e.g. a drag that re-orders via a
        // future "bring to front" action) must come
        // back in the right order on read.
        val repo = RoomProfileRepository(FakeProfileDao())
        val p = Profile(
            id = 0,
            name = "ordering-test",
            author = "tester",
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect.CENTERED_SMALL,
                    binding = CanonicalBinding.Neutralize
                ),
                ControlElement(
                    id = 1,
                    type = ControlType.Stick,
                    visualBounds = NormalizedRect.CENTERED_SMALL,
                    binding = CanonicalBinding.Stick(com.elysium.nexus.core.engine.StickSide.Left)
                ),
                ControlElement(
                    id = 2,
                    type = ControlType.Trigger,
                    visualBounds = NormalizedRect.CENTERED_SMALL,
                    binding = CanonicalBinding.Trigger(com.elysium.nexus.core.engine.StickSide.Right)
                )
            ),
            createdAt = 1000L,
            updatedAt = 2000L
        )
        repo.upsert(p)
        val byId = repo.byId(0)!!
        assertEquals(listOf(0, 1, 2), byId.controls.map { it.id })
    }

    @Test
    fun controlMetadataRoundTrips() = runTest {
        // Every non-id field of a control is
        // persisted and re-read. This is the
        // "we lost a value on save" smoke test.
        val repo = RoomProfileRepository(FakeProfileDao())
        val rect = NormalizedRect(x = 0.1f, y = 0.2f, width = 0.3f, height = 0.4f)
        val original = ControlElement(
            id = 7,
            type = ControlType.Stick,
            visualBounds = rect,
            hitBounds = rect,
            zIndex = 5,
            rotation = 90f,
            opacity = 0.5f,
            binding = CanonicalBinding.Stick(com.elysium.nexus.core.engine.StickSide.Right)
        )
        repo.upsert(
            Profile(
                id = 0,
                name = "meta",
                author = "tester",
                controls = listOf(original),
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )
        val reloaded = repo.byId(0)!!.controls.single()
        assertEquals(original, reloaded)
    }

    @Test
    fun profilesFromDifferentIdsAreIsolated() = runTest {
        // Two profiles in the same database; their
        // control lists do not cross-contaminate.
        val repo = RoomProfileRepository(FakeProfileDao())
        repo.upsert(
            Profile(
                id = 1,
                name = "p1",
                author = "tester",
                controls = listOf(
                    ControlElement(
                        id = 0,
                        type = ControlType.Button,
                        visualBounds = NormalizedRect.CENTERED_SMALL,
                        binding = CanonicalBinding.Neutralize
                    )
                ),
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        repo.upsert(
            Profile(
                id = 2,
                name = "p2",
                author = "tester",
                controls = listOf(
                    ControlElement(
                        id = 0,
                        type = ControlType.Stick,
                        visualBounds = NormalizedRect.CENTERED_SMALL,
                        binding = CanonicalBinding.Stick(com.elysium.nexus.core.engine.StickSide.Left)
                    ),
                    ControlElement(
                        id = 1,
                        type = ControlType.Trigger,
                        visualBounds = NormalizedRect.CENTERED_SMALL,
                        binding = CanonicalBinding.Trigger(com.elysium.nexus.core.engine.StickSide.Right)
                    )
                ),
                createdAt = 2000L,
                updatedAt = 2000L
            )
        )
        val p1 = repo.byId(1)!!
        val p2 = repo.byId(2)!!
        assertEquals(1, p1.controls.size)
        assertEquals(ControlType.Button, p1.controls[0].type)
        assertEquals(2, p2.controls.size)
        assertEquals(ControlType.Stick, p2.controls[0].type)
        assertEquals(ControlType.Trigger, p2.controls[1].type)
    }

    @Test
    fun emptyProfileRoundTrips() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
        val empty = Profile(
            id = 0,
            name = "empty",
            author = "tester",
            controls = emptyList(),
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repo.upsert(empty)
        val reloaded = repo.byId(0)!!
        assertTrue(reloaded.controls.isEmpty())
    }

    @Test
    fun nextIdIsZeroOnEmpty() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
        assertEquals(0, repo.nextId())
    }

    @Test
    fun nextIdIsMaxPlusOne() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
        repo.upsert(Profile.defaultProfile(id = 0, now = 0L))
        repo.upsert(Profile.defaultProfile(id = 5, now = 0L))
        repo.upsert(Profile.defaultProfile(id = 3, now = 0L))
        assertEquals(6, repo.nextId())
    }

    @Test
    fun deleteRemovesProfile() = runTest {
        val repo = RoomProfileRepository(FakeProfileDao())
        repo.upsert(Profile.defaultProfile(id = 0, now = 0L))
        repo.upsert(Profile.defaultProfile(id = 1, now = 0L))
        repo.delete(0)
        assertEquals(1, repo.count())
        assertNull(repo.byId(0))
        assertNotNull(repo.byId(1))
    }

    @Test
    fun deleteCascadesControls() = runTest {
        // The Room foreign key is `CASCADE` on
        // `profile_control`. A delete on the parent
        // row removes every child row. The fake DAO
        // mirrors this semantics (its
        // `deleteProfile` clears the controls map
        // for that profileId).
        val fake = FakeProfileDao()
        val repo = RoomProfileRepository(fake)
        repo.upsert(Profile.defaultProfile(id = 0, now = 0L))
        repo.upsert(
            Profile(
                id = 1,
                name = "p1",
                author = "tester",
                controls = listOf(
                    ControlElement(
                        id = 0,
                        type = ControlType.Button,
                        visualBounds = NormalizedRect.CENTERED_SMALL,
                        binding = CanonicalBinding.Neutralize
                    )
                ),
                createdAt = 0L,
                updatedAt = 0L
            )
        )
        repo.delete(1)
        // Profile 0's controls are untouched; profile 1's controls are gone.
        assertEquals(1, fake.controlCount())
        // The remaining control row belongs to profile 0.
        val remaining = fake.allControlRows()
        assertEquals(1, remaining.size)
        assertEquals(0, remaining[0].profileId)
    }
}
