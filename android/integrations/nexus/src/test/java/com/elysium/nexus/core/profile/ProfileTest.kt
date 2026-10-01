package com.elysium.nexus.core.profile

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [Profile] — the document that holds the
 * user's [ControlElement]s plus metadata.
 */
class ProfileTest {

    private fun element(
        id: Int,
        binding: CanonicalBinding = CanonicalBinding.Button(CanonicalButton.South),
        bounds: NormalizedRect = NormalizedRect(0.1f * id, 0.1f, 0.1f, 0.1f)
    ) = ControlElement(
        id = id,
        type = ControlType.Button,
        visualBounds = bounds,
        binding = binding
    )

    @Test
    fun defaultProfileHasOneNeutralizeButton() {
        val p = Profile.defaultProfile(now = 1000L)
        assertEquals(0, p.id)
        assertEquals("Elysium Nexus Default", p.name)
        assertEquals(1, p.controls.size)
        assertEquals(CanonicalBinding.Neutralize, p.controls[0].binding)
        assertEquals(1000L, p.createdAt)
        assertEquals(1000L, p.updatedAt)
    }

    @Test
    fun blankNameIsRejected() {
        try {
            Profile(
                id = 0,
                name = "",
                author = "system",
                controls = emptyList(),
                createdAt = 0L,
                updatedAt = 0L
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun withControlAddedAppends() {
        val p = Profile.defaultProfile(now = 1000L)
        val newElement = element(id = 1, binding = CanonicalBinding.Stick(StickSide.Left))
        val updated = p.withControlAdded(newElement, now = 2000L)
        assertEquals(2, updated.controls.size)
        assertEquals(newElement, updated.controls[1])
        assertEquals(2000L, updated.updatedAt)
    }

    @Test
    fun withControlReplacedUpdates() {
        val p = Profile.defaultProfile(now = 1000L)
            .withControlAdded(element(id = 1), now = 1500L)
        val newBounds = NormalizedRect(0.6f, 0.6f, 0.1f, 0.1f)
        val updated = p.withControlReplaced(
            controlId = 0,
            updated = p.controls[0].copy(visualBounds = newBounds),
            now = 3000L
        )
        assertEquals(2, updated.controls.size)
        assertEquals(newBounds, updated.controls[0].visualBounds)
        assertEquals(3000L, updated.updatedAt)
    }

    @Test
    fun withControlRemovedDrops() {
        val p = Profile.defaultProfile(now = 1000L)
            .withControlAdded(element(id = 1), now = 1500L)
        val updated = p.withControlRemoved(controlId = 0, now = 3000L)
        assertEquals(1, updated.controls.size)
        assertEquals(1, updated.controls[0].id)
    }

    @Test
    fun updatedAtMustBeAtLeastCreatedAt() {
        try {
            Profile(
                id = 0,
                name = "Test",
                author = "tester",
                controls = emptyList(),
                createdAt = 2000L,
                updatedAt = 1000L
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
