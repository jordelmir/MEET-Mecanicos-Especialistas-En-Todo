package com.elysium.nexus.databases.profile

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ProfileConverters] — the Room
 * `TypeConverter` for the profile database.
 *
 * The converters are the persistence boundary of the
 * domain model; the tests assert that every domain
 * value round-trips losslessly through the converter.
 * A future contributor who adds a new
 * [CanonicalBinding] variant gets a failing test
 * until they add a `parseBinding` branch.
 */
class ProfileConvertersTest {

    // ---- ControlType ----

    @Test
    fun controlTypeRoundTripsByName() {
        for (type in ControlType.values()) {
            val asString = ProfileConverters.fromControlType(type)
            assertEquals(type.name, asString)
            assertEquals(type, ProfileConverters.toControlType(asString))
        }
    }

    // ---- NormalizedRect ----

    @Test
    fun normalizedRectRoundTripsWithFourFloats() {
        val rect = NormalizedRect(x = 0.25f, y = 0.5f, width = 0.1f, height = 0.2f)
        val serialized = ProfileConverters.fromRect(rect)
        val parts = serialized.split(",")
        assertEquals(4, parts.size)
        val parsed = ProfileConverters.toRect(serialized)
        assertEquals(rect, parsed)
    }

    @Test
    fun normalizedRectHandlesZeroAndOneEdges() {
        val rect = NormalizedRect(x = 0f, y = 0f, width = 1f, height = 1f)
        val parsed = ProfileConverters.toRect(ProfileConverters.fromRect(rect))
        assertEquals(rect, parsed)
    }

    // ---- CanonicalBinding ----

    @Test
    fun neutralizeRoundTripsWithNoArg() {
        val asString = ProfileConverters.fromBinding(CanonicalBinding.Neutralize)
        assertEquals("Neutralize", asString)
        assertEquals(CanonicalBinding.Neutralize, ProfileConverters.toBinding(asString))
    }

    @Test
    fun buttonBindingRoundTripsForEveryCanonicalButton() {
        for (button in CanonicalButton.values()) {
            val binding = CanonicalBinding.Button(button)
            val serialized = ProfileConverters.fromBinding(binding)
            assertEquals("Button:${button.ordinal}", serialized)
            assertEquals(binding, ProfileConverters.toBinding(serialized))
        }
    }

    @Test
    fun stickBindingRoundTripsForBothSides() {
        for (side in StickSide.values()) {
            val binding = CanonicalBinding.Stick(side)
            val serialized = ProfileConverters.fromBinding(binding)
            assertEquals("Stick:${side.name}", serialized)
            assertEquals(binding, ProfileConverters.toBinding(serialized))
        }
    }

    @Test
    fun triggerBindingRoundTripsForBothSides() {
        for (side in StickSide.values()) {
            val binding = CanonicalBinding.Trigger(side)
            val serialized = ProfileConverters.fromBinding(binding)
            assertEquals("Trigger:${side.name}", serialized)
            assertEquals(binding, ProfileConverters.toBinding(serialized))
        }
    }

    // ---- Failure modes ----

    @Test(expected = IllegalArgumentException::class)
    fun unknownBindingTagThrows() {
        ProfileConverters.toBinding("NoSuchTag:x")
    }

    @Test(expected = IllegalArgumentException::class)
    fun buttonBindingRejectsMissingArg() {
        ProfileConverters.toBinding("Button")
    }

    @Test(expected = IllegalArgumentException::class)
    fun buttonBindingRejectsOrdinalOutOfRange() {
        ProfileConverters.toBinding("Button:99")
    }

    @Test(expected = IllegalArgumentException::class)
    fun stickBindingRejectsMissingArg() {
        ProfileConverters.toBinding("Stick")
    }

    @Test(expected = IllegalArgumentException::class)
    fun triggerBindingRejectsMissingArg() {
        ProfileConverters.toBinding("Trigger")
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalizeRejectsExtraArg() {
        ProfileConverters.toBinding("Neutralize:extra")
    }

    // ---- The closed set: future bindings will be caught
    //      by the `when` exhaustiveness check in `fromBinding`.

    @Test
    fun bindingFormatDocumentedExhaustively() {
        // The four canonical forms:
        assertTrue(ProfileConverters.fromBinding(CanonicalBinding.Neutralize).startsWith("Neutralize"))
        assertTrue(ProfileConverters.fromBinding(CanonicalBinding.Button(CanonicalButton.South)).startsWith("Button:"))
        assertTrue(ProfileConverters.fromBinding(CanonicalBinding.Stick(StickSide.Left)).startsWith("Stick:"))
        assertTrue(ProfileConverters.fromBinding(CanonicalBinding.Trigger(StickSide.Right)).startsWith("Trigger:"))
    }
}
