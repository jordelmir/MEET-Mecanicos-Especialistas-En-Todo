package com.elysium.nexus.core.profile

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [ProfileShareBuilder].
 *
 * The builder is the pure half of the §15 export
 * story. The Android half is exercised on-device
 * (Phase 1.17 ships the share intent; the
 * emulator verification confirms the FileProvider
 * URI is reachable).
 */
class ProfileShareBuilderTest {

    @Test
    fun `build emits the schema version 1 MIME type`() {
        val share = ProfileShareBuilder.build(Profile.defaultProfile(now = 0L))
        assertEquals("application/vnd.elysium.profile+json", share.mimeType)
        assertEquals(ProfileShareBuilder.MIME_TYPE, share.mimeType)
    }

    @Test
    fun `build filename carries the profile id and a slug of the name`() {
        val profile = Profile(
            id = 7,
            name = "FPS Pack",
            author = "tester",
            controls = emptyList(),
            createdAt = 0L,
            updatedAt = 0L
        )
        val share = ProfileShareBuilder.build(profile)
        assertEquals("elysium-profile-7-fps-pack.json", share.filename)
    }

    @Test
    fun `build filename drops non-ASCII characters and runs of separators`() {
        val profile = Profile(
            id = 1,
            name = "  Ácción  Éxtraña!  ",
            author = "tester",
            controls = emptyList(),
            createdAt = 0L,
            updatedAt = 0L
        )
        val share = ProfileShareBuilder.build(profile)
        // The slug is lower-case ASCII; non-ASCII
        // letters are dropped (the leading 'Á' is
        // dropped silently because the slug buffer
        // is empty, so it leaves no separator; the
        // 'ó' and 'ñ' in the middle are replaced by
        // a single dash); runs of separators
        // collapse; leading / trailing whitespace
        // is trimmed. The resulting slug is
        // "cci-n-xtra-a".
        assertEquals("elysium-profile-1-cci-n-xtra-a.json", share.filename)
    }

    @Test
    fun `build filename uses untitled when the slug is empty`() {
        // The profile name must be non-blank per
        // Profile.init, but a name made of pure
        // non-alphanumerics slugs to empty. The
        // builder falls back to "untitled" so the
        // share still has a usable filename.
        val profile = Profile(
            id = 1,
            name = "!!!",
            author = "tester",
            controls = emptyList(),
            createdAt = 0L,
            updatedAt = 0L
        )
        val share = ProfileShareBuilder.build(profile)
        assertEquals("elysium-profile-1-untitled.json", share.filename)
    }

    @Test
    fun `build content is the ProfileJson serialisation`() {
        val profile = Profile(
            id = 3,
            name = "Stick demo",
            author = "tester",
            controls = listOf(
                ControlElement(
                    id = 11,
                    type = ControlType.Stick,
                    visualBounds = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f),
                    hitBounds = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Stick(StickSide.Left)
                )
            ),
            createdAt = 100L,
            updatedAt = 200L
        )
        val share = ProfileShareBuilder.build(profile)
        // The content round-trips through ProfileJson.
        val roundTripped = ProfileJson.fromJson(share.content)
        assertEquals(profile, roundTripped)
    }

    @Test
    fun `build content is non-empty for an empty profile`() {
        val profile = Profile.defaultProfile(now = 0L)
        val share = ProfileShareBuilder.build(profile)
        // The serialiser emits a valid empty-controls
        // document; the content is non-empty (schema
        // version, name, id, …).
        assertTrue(share.content.isNotEmpty())
        assertTrue(share.content.contains("\"schemaVersion\":"))
        assertTrue(share.content.contains("\"controls\":"))
    }

    @Test
    fun `slugOf caps at 32 characters`() {
        val longName = "A".repeat(100)
        val slug = ProfileShareBuilder.slugOf(longName)
        assertEquals(32, slug.length)
    }

    @Test
    fun `slugOf collapses runs of separators`() {
        assertEquals("a-b-c", ProfileShareBuilder.slugOf("a   b___c"))
        assertEquals("a-b", ProfileShareBuilder.slugOf("a---b"))
        assertEquals("a-b", ProfileShareBuilder.slugOf("a   ___   b"))
    }

    @Test
    fun `slugOf strips leading and trailing separators`() {
        assertEquals("hello", ProfileShareBuilder.slugOf("  hello  "))
        assertEquals("hello", ProfileShareBuilder.slugOf("---hello---"))
    }

    @Test
    fun `slugOf returns untitled for a name with no alphanumerics`() {
        assertEquals("untitled", ProfileShareBuilder.slugOf(""))
        assertEquals("untitled", ProfileShareBuilder.slugOf("   "))
        assertEquals("untitled", ProfileShareBuilder.slugOf("---"))
    }

    @Test
    fun `filenameFor matches the build output`() {
        val profile = Profile(
            id = 42,
            name = "RPG Build",
            author = "tester",
            controls = emptyList(),
            createdAt = 0L,
            updatedAt = 0L
        )
        val share = ProfileShareBuilder.build(profile)
        val filename = ProfileShareBuilder.filenameFor(profile)
        assertEquals(filename, share.filename)
        assertEquals("elysium-profile-42-rpg-build.json", filename)
    }

    @Test
    fun `round trip profile with every binding variant`() {
        val profile = Profile(
            id = 99,
            name = "All bindings",
            author = "tester",
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.1f, 0.1f, 0.1f, 0.1f),
                    hitBounds = NormalizedRect(0.1f, 0.1f, 0.1f, 0.1f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Neutralize
                ),
                ControlElement(
                    id = 1,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.2f, 0.1f, 0.1f, 0.1f),
                    hitBounds = NormalizedRect(0.2f, 0.1f, 0.1f, 0.1f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Button(CanonicalButton.South)
                ),
                ControlElement(
                    id = 2,
                    type = ControlType.Stick,
                    visualBounds = NormalizedRect(0.3f, 0.1f, 0.2f, 0.2f),
                    hitBounds = NormalizedRect(0.3f, 0.1f, 0.2f, 0.2f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Stick(StickSide.Right)
                ),
                ControlElement(
                    id = 3,
                    type = ControlType.Trigger,
                    visualBounds = NormalizedRect(0.6f, 0.1f, 0.1f, 0.2f),
                    hitBounds = NormalizedRect(0.6f, 0.1f, 0.1f, 0.2f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Trigger(StickSide.Left)
                )
            ),
            createdAt = 0L,
            updatedAt = 0L
        )
        val share = ProfileShareBuilder.build(profile)
        val roundTripped = ProfileJson.fromJson(share.content)
        assertEquals(profile, roundTripped)
    }
}
