package com.elysium.nexus.core.profile

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for [ProfileJson] — the §15 profile
 * document's JSON serialiser.
 *
 * The tests verify:
 *  - Every [ControlType] round-trips.
 *  - Every [CanonicalBinding] variant round-trips.
 *  - Every [StickSide] round-trips.
 *  - Every [CanonicalButton] round-trips.
 *  - An unrecognised `schemaVersion` throws.
 *  - An unrecognised binding kind throws.
 *  - The metadata (id, name, author, version,
 *    timestamps) round-trips.
 *  - The control ordering is preserved.
 *  - Custom rotation / opacity values round-trip
 *    (not just the defaults).
 */
class ProfileJsonTest {

    @Test
    fun defaultProfileRoundTrips() {
        val original = Profile.defaultProfile(id = 0, now = 1000L)
        val json = ProfileJson.toJson(original)
        val reloaded = ProfileJson.fromJson(json)
        assertEquals(original, reloaded)
    }

    @Test
    fun emptyProfileRoundTrips() {
        val original = Profile(
            id = 42,
            name = "empty",
            author = "tester",
            controls = emptyList(),
            version = 1,
            createdAt = 1000L,
            updatedAt = 2000L
        )
        val reloaded = ProfileJson.fromJson(ProfileJson.toJson(original))
        assertEquals(original, reloaded)
    }

    @Test
    fun everyControlTypeRoundTrips() {
        for (type in ControlType.values()) {
            val profile = Profile(
                id = 0,
                name = "type-$type",
                author = "tester",
                controls = listOf(
                    ControlElement(
                        id = 0,
                        type = type,
                        visualBounds = NormalizedRect.CENTERED_SMALL,
                        binding = when (type) {
                            ControlType.Button -> CanonicalBinding.Neutralize
                            ControlType.Stick -> CanonicalBinding.Stick(StickSide.Left)
                            ControlType.Trigger -> CanonicalBinding.Trigger(StickSide.Left)
                            ControlType.Dpad -> CanonicalBinding.Neutralize
                            ControlType.Touchpad -> CanonicalBinding.Neutralize
                        }
                    )
                ),
                createdAt = 0L,
                updatedAt = 0L
            )
            val reloaded = ProfileJson.fromJson(ProfileJson.toJson(profile))
            assertEquals(type, reloaded.controls[0].type)
        }
    }

    @Test
    fun everyBindingVariantRoundTrips() {
        val buttons = listOf(
            CanonicalBinding.Neutralize,
            CanonicalBinding.Button(CanonicalButton.South),
            CanonicalBinding.Stick(StickSide.Left),
            CanonicalBinding.Trigger(StickSide.Right)
        )
        for (binding in buttons) {
            val profile = Profile(
                id = 0,
                name = "binding-test",
                author = "tester",
                controls = listOf(
                    ControlElement(
                        id = 0,
                        type = ControlType.Button,
                        visualBounds = NormalizedRect.CENTERED_SMALL,
                        binding = binding
                    )
                ),
                createdAt = 0L,
                updatedAt = 0L
            )
            val reloaded = ProfileJson.fromJson(ProfileJson.toJson(profile))
            assertEquals(binding, reloaded.controls[0].binding)
        }
    }

    @Test
    fun everyStickSideRoundTrips() {
        for (side in StickSide.values()) {
            val profile = Profile(
                id = 0,
                name = "side-$side",
                author = "tester",
                controls = listOf(
                    ControlElement(
                        id = 0,
                        type = ControlType.Stick,
                        visualBounds = NormalizedRect.CENTERED_SMALL,
                        binding = CanonicalBinding.Stick(side)
                    )
                ),
                createdAt = 0L,
                updatedAt = 0L
            )
            val reloaded = ProfileJson.fromJson(ProfileJson.toJson(profile))
            assertEquals(side, (reloaded.controls[0].binding as CanonicalBinding.Stick).side)
        }
    }

    @Test
    fun everyCanonicalButtonRoundTrips() {
        for (button in CanonicalButton.values()) {
            val profile = Profile(
                id = 0,
                name = "button-$button",
                author = "tester",
                controls = listOf(
                    ControlElement(
                        id = 0,
                        type = ControlType.Button,
                        visualBounds = NormalizedRect.CENTERED_SMALL,
                        binding = CanonicalBinding.Button(button)
                    )
                ),
                createdAt = 0L,
                updatedAt = 0L
            )
            val reloaded = ProfileJson.fromJson(ProfileJson.toJson(profile))
            assertEquals(
                button,
                (reloaded.controls[0].binding as CanonicalBinding.Button).button
            )
        }
    }

    @Test
    fun controlOrderingIsPreserved() {
        val profile = Profile(
            id = 0,
            name = "ordering",
            author = "tester",
            controls = (0 until 5).map { i ->
                ControlElement(
                    id = i,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(
                        x = 0.1f * i,
                        y = 0.1f * i,
                        width = 0.1f,
                        height = 0.1f
                    ),
                    binding = CanonicalBinding.Neutralize
                )
            },
            createdAt = 0L,
            updatedAt = 0L
        )
        val reloaded = ProfileJson.fromJson(ProfileJson.toJson(profile))
        assertEquals(profile.controls.map { it.id }, reloaded.controls.map { it.id })
        // The bounds for each control also match.
        reloaded.controls.forEachIndexed { i, c ->
            assertEquals(profile.controls[i].visualBounds, c.visualBounds)
        }
    }

    @Test
    fun customRotationAndOpacityRoundTrip() {
        val original = ControlElement(
            id = 0,
            type = ControlType.Stick,
            visualBounds = NormalizedRect(0.2f, 0.3f, 0.15f, 0.2f),
            hitBounds = NormalizedRect(0.1f, 0.2f, 0.25f, 0.3f), // bigger than visual
            zIndex = 5,
            rotation = 137.5f,
            opacity = 0.42f,
            binding = CanonicalBinding.Stick(StickSide.Right)
        )
        val profile = Profile(
            id = 0,
            name = "custom",
            author = "tester",
            controls = listOf(original),
            createdAt = 0L,
            updatedAt = 0L
        )
        val reloaded = ProfileJson.fromJson(ProfileJson.toJson(profile))
        val reloadedControl = reloaded.controls[0]
        assertEquals(137.5f, reloadedControl.rotation, 1e-5f)
        assertEquals(0.42f, reloadedControl.opacity, 1e-5f)
        assertEquals(original.hitBounds, reloadedControl.hitBounds)
        assertEquals(5, reloadedControl.zIndex)
    }

    @Test
    fun metadataRoundTrips() {
        val original = Profile(
            id = 99,
            name = "metadata-test",
            author = "jor",
            controls = emptyList(),
            version = 7,
            createdAt = 1700000000000L,
            updatedAt = 1800000000000L
        )
        val reloaded = ProfileJson.fromJson(ProfileJson.toJson(original))
        assertEquals(99, reloaded.id)
        assertEquals("metadata-test", reloaded.name)
        assertEquals("jor", reloaded.author)
        assertEquals(7, reloaded.version)
        assertEquals(1700000000000L, reloaded.createdAt)
        assertEquals(1800000000000L, reloaded.updatedAt)
    }

    @Test
    fun toJsonProducesSchemaVersionField() {
        val json = ProfileJson.toJson(Profile.defaultProfile(now = 0L))
        val obj = JSONObject(json)
        assertEquals(ProfileJson.SCHEMA_VERSION, obj.getInt("schemaVersion"))
    }

    @Test
    fun unsupportedSchemaVersionIsRejected() {
        val bad = """
            {
              "schemaVersion": 99,
              "id": 0,
              "name": "x",
              "author": "x",
              "version": 1,
              "createdAt": 0,
              "updatedAt": 0,
              "controls": []
            }
        """.trimIndent()
        try {
            ProfileJson.fromJson(bad)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun unknownBindingKindIsRejected() {
        val bad = """
            {
              "schemaVersion": 1,
              "id": 0,
              "name": "x",
              "author": "x",
              "version": 1,
              "createdAt": 0,
              "updatedAt": 0,
              "controls": [
                {
                  "id": 0,
                  "type": "Button",
                  "visualBounds": { "x": 0.4, "y": 0.4, "width": 0.2, "height": 0.2 },
                  "hitBounds": { "x": 0.4, "y": 0.4, "width": 0.2, "height": 0.2 },
                  "zIndex": 0,
                  "rotation": 0.0,
                  "opacity": 1.0,
                  "binding": { "kind": "Mystery" }
                }
              ]
            }
        """.trimIndent()
        try {
            ProfileJson.fromJson(bad)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun profileEqualityHoldsAfterRoundTrip() {
        val original = Profile(
            id = 5,
            name = "test",
            author = "tester",
            controls = (0 until 3).map { i ->
                ControlElement(
                    id = i,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.1f * i, 0.1f, 0.05f, 0.05f),
                    binding = CanonicalBinding.Neutralize,
                    rotation = 45f * i,
                    opacity = 1f - 0.1f * i
                )
            },
            version = 2,
            createdAt = 1000L,
            updatedAt = 2000L
        )
        val reloaded = ProfileJson.fromJson(ProfileJson.toJson(original))
        // The data class equality holds.
        assertEquals(original, reloaded)
        // The data is *not* the same instance (proves
        // the round-trip is a real serialise + parse,
        // not a no-op).
        assertNotEquals(System.identityHashCode(original), System.identityHashCode(reloaded))
    }

    @Test
    fun jsonIsHumanReadable() {
        // Sanity check: the JSON contains the
        // expected field names. A future schema
        // migration that silently renames a field
        // would fail this test.
        val json = ProfileJson.toJson(Profile.defaultProfile(now = 0L))
        listOf("schemaVersion", "id", "name", "author", "version", "createdAt", "updatedAt", "controls").forEach { key ->
            assertTrue("JSON should contain '$key': $json", json.contains("\"$key\""))
        }
    }
}
