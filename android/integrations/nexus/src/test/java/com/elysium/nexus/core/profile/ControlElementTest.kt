package com.elysium.nexus.core.profile

import com.elysium.nexus.core.model.CanonicalButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests for [ControlElement] + [Profile] — the §15
 * data model.
 */
class ControlElementTest {

    @Test
    fun controlElementDefaultsAreSane() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect.CENTERED_SMALL,
            binding = CanonicalBinding.Button(CanonicalButton.South)
        )
        assertEquals(0, c.id)
        assertEquals(ControlType.Button, c.type)
        assertEquals(0f, c.rotation, 0f)
        assertEquals(1f, c.opacity, 0f)
        assertEquals(c.visualBounds, c.hitBounds) // default = visual
    }

    @Test
    fun negativeIdIsRejected() {
        try {
            ControlElement(
                id = -1,
                type = ControlType.Button,
                visualBounds = NormalizedRect.CENTERED_SMALL,
                binding = CanonicalBinding.Neutralize
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun outOfRangeRotationIsRejected() {
        try {
            ControlElement(
                id = 0,
                type = ControlType.Button,
                visualBounds = NormalizedRect.CENTERED_SMALL,
                binding = CanonicalBinding.Neutralize,
                rotation = 400f
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun outOfRangeOpacityIsRejected() {
        try {
            ControlElement(
                id = 0,
                type = ControlType.Button,
                visualBounds = NormalizedRect.CENTERED_SMALL,
                binding = CanonicalBinding.Neutralize,
                opacity = 1.5f
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun movedByClampsToBounds() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.5f, 0.5f, 0.2f, 0.2f),
            binding = CanonicalBinding.Neutralize
        )
        // Move by +0.5 in x — would push x to 1.0, which
        // would make x + width = 1.2 > 1. Clamp to x = 0.8.
        val moved = c.movedBy(dx = 0.5f, dy = 0f)
        assertEquals(0.8f, moved.visualBounds.x, 1e-6f)
        assertEquals(0.5f, moved.visualBounds.y, 1e-6f)
        assertEquals(0.2f, moved.visualBounds.width, 1e-6f)
    }

    @Test
    fun movedByNegativeClampsToZero() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f),
            binding = CanonicalBinding.Neutralize
        )
        val moved = c.movedBy(dx = -0.5f, dy = 0f)
        assertEquals(0f, moved.visualBounds.x, 1e-6f)
    }

    @Test
    fun movedByPreservesSize() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.3f, 0.3f, 0.15f, 0.15f),
            binding = CanonicalBinding.Neutralize
        )
        val moved = c.movedBy(dx = 0.1f, dy = 0.1f)
        assertEquals(0.15f, moved.visualBounds.width, 1e-6f)
        assertEquals(0.15f, moved.visualBounds.height, 1e-6f)
    }

    @Test
    fun resizedGrowsAndShrinks() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.2f, 0.2f, 0.2f, 0.2f),
            binding = CanonicalBinding.Neutralize
        )
        val grown = c.resized(newWidth = 0.4f, newHeight = 0.3f)
        assertEquals(0.4f, grown.visualBounds.width, 1e-6f)
        assertEquals(0.3f, grown.visualBounds.height, 1e-6f)
        assertEquals(0.2f, grown.visualBounds.x, 1e-6f) // position preserved
        assertEquals(0.2f, grown.visualBounds.y, 1e-6f)
    }

    @Test
    fun resizedClampsToMinimumDimension() {
        // The 1.3 spec says: a control's minimum
        // visible dimension is 5% of the parent. A
        // pinch-to-zero would otherwise produce an
        // invisible control.
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.5f, 0.5f, 0.1f, 0.1f),
            binding = CanonicalBinding.Neutralize
        )
        val shrunk = c.resized(newWidth = 0.001f, newHeight = 0.001f)
        assertEquals(0.05f, shrunk.visualBounds.width, 1e-6f)
        assertEquals(0.05f, shrunk.visualBounds.height, 1e-6f)
    }

    @Test
    fun resizedClampsToMaxAxisMinusPosition() {
        // A control at (0.8, 0.8) cannot be wider
        // than 0.2 (parent = 1.0). A 1.0 width
        // request clamps to 0.2.
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.8f, 0.8f, 0.1f, 0.1f),
            binding = CanonicalBinding.Neutralize
        )
        val grown = c.resized(newWidth = 1.0f, newHeight = 1.0f)
        assertEquals(0.2f, grown.visualBounds.width, 1e-6f)
        assertEquals(0.2f, grown.visualBounds.height, 1e-6f)
    }

    @Test
    fun rotatedNormalisesTo0_360() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect.CENTERED_SMALL,
            binding = CanonicalBinding.Neutralize,
            rotation = 0f
        )
        val r1 = c.rotated(newRotation = 720f) // 2 full turns
        assertEquals(0f, r1.rotation, 1e-6f)
        val r2 = c.rotated(newRotation = -90f)
        assertEquals(270f, r2.rotation, 1e-6f)
        val r3 = c.rotated(newRotation = 45f)
        assertEquals(45f, r3.rotation, 1e-6f)
    }

    @Test
    fun rotatedPreservesBounds() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.2f, 0.2f, 0.1f, 0.1f),
            binding = CanonicalBinding.Neutralize
        )
        val r = c.rotated(newRotation = 90f)
        assertEquals(c.visualBounds, r.visualBounds)
    }
}
