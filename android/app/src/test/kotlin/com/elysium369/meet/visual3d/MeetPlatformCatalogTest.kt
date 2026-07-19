package com.elysium369.meet.visual3d

import com.elysium369.meet.visual3d.domain.MeetPlatformCatalog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetPlatformCatalogTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path")
    ).firstOrNull(File::isFile) ?: error("Missing MEET platform asset: $path")

    @Test
    fun `current platform remains permanent and nine original platforms are selectable`() {
        val profiles = MeetPlatformCatalog.profiles
        assertEquals(10, profiles.size)
        assertTrue(profiles.first().permanent)
        assertEquals("origin", profiles.first().id)
        assertEquals(9, profiles.count { it.originalMeetDesign })
        assertEquals(profiles.size, profiles.map { it.id }.distinct().size)
        assertEquals(profiles.size, profiles.map { it.assetPath }.distinct().size)
        assertFalse(profiles.drop(1).any { it.displayName.contains("RAM", ignoreCase = true) })
    }

    @Test
    fun `every selectable platform is a valid nonempty GLB 2 asset`() {
        MeetPlatformCatalog.profiles.forEach { profile ->
            val model = asset(profile.assetPath)
            val header = model.inputStream().use { input -> ByteArray(12).also(input::read) }
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("glTF", header.copyOfRange(0, 4).decodeToString())
            assertEquals(2, buffer.getInt(4))
            assertEquals(model.length(), buffer.getInt(8).toLong())
            assertTrue("${profile.id} must contain visible geometry", model.length() > 10_000L)
        }
    }
}
