package com.elysium.nexus.core.profile

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [ProfileActions].
 *
 * The actions are the §15 "duplicate" and
 * "rename" pure operations on a [Profile]. The
 * test suite covers the happy path, the
 * control-id namespace shift on duplicate, and
 * the rename validation.
 */
class ProfileActionsTest {

    @Test
    fun `duplicate produces a copy with a new id and fresh timestamps`() {
        val source = Profile.defaultProfile(now = 0L)
        val duplicate = ProfileActions.duplicate(source, newId = 100, now = 5000L)
        assertEquals(100, duplicate.id)
        assertEquals(5000L, duplicate.createdAt)
        assertEquals(5000L, duplicate.updatedAt)
        assertEquals("Elysium Nexus Default (copy)", duplicate.name)
    }

    @Test
    fun `duplicate preserves every control's type, bounds, rotation, opacity, and binding`() {
        val source = Profile(
            id = 7,
            name = "Custom",
            author = "tester",
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Stick,
                    visualBounds = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
                    hitBounds = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
                    zIndex = 0,
                    rotation = 0.5f,
                    opacity = 0.7f,
                    binding = CanonicalBinding.Stick(StickSide.Left)
                ),
                ControlElement(
                    id = 1,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.6f, 0.6f, 0.1f, 0.1f),
                    hitBounds = NormalizedRect(0.6f, 0.6f, 0.1f, 0.1f),
                    zIndex = 1,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Button(CanonicalButton.North)
                )
            ),
            createdAt = 0L,
            updatedAt = 0L
        )
        val duplicate = ProfileActions.duplicate(source, newId = 100, now = 0L)
        // The number of controls is preserved.
        assertEquals(source.controls.size, duplicate.controls.size)
        // Every control's "shape" is preserved
        // (type, bounds, rotation, opacity, binding)
        // — only the id changes.
        for (i in source.controls.indices) {
            val s = source.controls[i]
            val d = duplicate.controls[i]
            assertEquals(s.type, d.type)
            assertEquals(s.visualBounds, d.visualBounds)
            assertEquals(s.hitBounds, d.hitBounds)
            assertEquals(s.zIndex, d.zIndex)
            assertEquals(s.rotation, d.rotation, 0.0001f)
            assertEquals(s.opacity, d.opacity, 0.0001f)
            assertEquals(s.binding, d.binding)
        }
    }

    @Test
    fun `duplicate assigns new ids to every control so editing the duplicate does not touch the source`() {
        val source = Profile(
            id = 3,
            name = "Editable",
            author = "tester",
            controls = (0..4).map { i ->
                ControlElement(
                    id = i,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.1f * i, 0.1f * i, 0.1f, 0.1f),
                    hitBounds = NormalizedRect(0.1f * i, 0.1f * i, 0.1f, 0.1f),
                    zIndex = i,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Neutralize
                )
            },
            createdAt = 0L,
            updatedAt = 0L
        )
        val duplicate = ProfileActions.duplicate(source, newId = 100, now = 0L)
        // The control ids are shifted: source's
        // control id `i` becomes
        // `i + source.id * 1000`. For source id 3
        // and source control ids 0..4, the
        // duplicate's control ids are 3000..3004.
        val duplicateIds = duplicate.controls.map { it.id }
        assertEquals(listOf(3000, 3001, 3002, 3003, 3004), duplicateIds)
        // No duplicate control id is also a
        // source control id.
        for (id in duplicateIds) {
            assertTrue(
                "Duplicate control id $id collides with a source id",
                source.controls.none { it.id == id }
            )
        }
    }

    @Test
    fun `rename changes the name and updates updatedAt`() {
        val source = Profile.defaultProfile(now = 0L)
        val renamed = ProfileActions.rename(source, newName = "My Build", now = 1000L)
        assertEquals("My Build", renamed.name)
        assertEquals(1000L, renamed.updatedAt)
        // createdAt is preserved.
        assertEquals(source.createdAt, renamed.createdAt)
    }

    @Test
    fun `rename rejects a blank name`() {
        val source = Profile.defaultProfile(now = 0L)
        var threw = false
        try {
            ProfileActions.rename(source, newName = "", now = 0L)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected IllegalArgumentException for blank name", threw)
    }

    @Test
    fun `rename rejects a whitespace-only name`() {
        val source = Profile.defaultProfile(now = 0L)
        var threw = false
        try {
            ProfileActions.rename(source, newName = "   ", now = 0L)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected IllegalArgumentException for whitespace name", threw)
    }

    @Test
    fun `duplicate of a duplicate produces a third profile with non-colliding ids`() {
        val source = Profile.defaultProfile(now = 0L)
        val dup1 = ProfileActions.duplicate(source, newId = 100, now = 0L)
        val dup2 = ProfileActions.duplicate(dup1, newId = 200, now = 0L)
        // The three profiles have distinct ids.
        assertNotEquals(source.id, dup1.id)
        assertNotEquals(dup1.id, dup2.id)
        assertNotEquals(source.id, dup2.id)
        // The control ids of dup2 are shifted by
        // `dup1.id * 1000` from dup1's control ids.
        // dup1's control ids are shifted by
        // `source.id * 1000` from the source's
        // control ids. So dup2's control ids are
        // `source.id * 1000 + control.id` from
        // `dup1.id * 1000`. The default profile's
        // source id is 0; the source control id is
        // 0; dup1.id is 100; so dup2's control id
        // is 100 * 1000 + 0 = 100000.
        val sourceControlId = source.controls.first().id
        val dup2ControlId = dup2.controls.first().id
        assertEquals(
            sourceControlId + 100_000,
            dup2ControlId
        )
    }
}
