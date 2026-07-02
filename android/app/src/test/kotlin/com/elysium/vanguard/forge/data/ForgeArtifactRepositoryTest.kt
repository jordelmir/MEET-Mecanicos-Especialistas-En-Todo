package com.elysium.vanguard.forge.data

import com.elysium.vanguard.forge.domain.DimensionSet
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.SafetyClassification
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeArtifactRepositoryTest {

    private val repo = ForgeArtifactRepository()

    private fun samplePart(id: String = "p1"): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = id, name = "Test Part $id", artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.EDUCATIONAL
        ),
        dimensions = DimensionSet(lengthMm = 10.0, widthMm = 10.0, heightMm = 10.0)
    )

    @Test
    fun `savePart stores part`() = runBlocking {
        val part = samplePart()
        repo.savePart(part)
        assertEquals(part, repo.getPart("p1"))
    }

    @Test
    fun `deleteArtifact removes part`() = runBlocking {
        repo.savePart(samplePart())
        repo.deleteArtifact("p1")
        assertNull(repo.getPart("p1"))
    }

    @Test
    fun `exportToText and importFromText round trip`() {
        runBlocking {
            repo.savePart(samplePart("exported"))
        }
        val text = repo.exportToText("exported")
        assertNotNull(text)
        // Importar en una instancia limpia.
        val freshRepo = ForgeArtifactRepository()
        val result = freshRepo.importFromText(text!!)
        assertTrue(result.isSuccess)
        assertNotNull(freshRepo.getPart("exported"))
    }

    @Test
    fun `search sanitizes input and matches by tag`() = runBlocking {
        repo.savePart(samplePart("brake_disc_x"))
        val results = repo.search("brake")
        assertTrue("Search must find the part", results.any { it.id == "brake_disc_x" })
    }

    @Test
    fun `search ignores path-traversal-like input`() {
        val results = repo.search("../../etc/passwd")
        // No debe crashear; resultado vacío.
        assertTrue(results.isEmpty() || results.all { !it.id.contains("..") })
    }
}