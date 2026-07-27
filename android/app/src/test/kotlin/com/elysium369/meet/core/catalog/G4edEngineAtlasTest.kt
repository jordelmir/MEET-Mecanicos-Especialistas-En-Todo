package com.elysium369.meet.core.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G4edEngineAtlasTest {
    private fun asset(): File = listOf(
        File("src/main/assets/$G4ED_ENGINE_ATLAS_ASSET"),
        File("app/src/main/assets/$G4ED_ENGINE_ATLAS_ASSET"),
        File("android/app/src/main/assets/$G4ED_ENGINE_ATLAS_ASSET"),
    ).firstOrNull(File::isFile) ?: error("Missing G4ED engine atlas")

    private val raw by lazy { asset().readText() }
    private val atlas by lazy { G4edEngineAtlasParser.decode(raw) }

    @Test
    fun `android contract loads all 420 traceable elements`() {
        assertEquals(420, atlas.elements.size)
        assertEquals((1..420).toList(), atlas.elements.map { it.ordinal })
        assertEquals(20, atlas.sections.size)
        assertEquals(333, atlas.statistics.directlySellableCount)
        assertEquals(G4ED_ENGINE_ATLAS_SOURCE_SHA256, atlas.source.sha256)
        assertEquals(G4ED_ENGINE_ATLAS_CONTENT_SHA256, atlas.contentSha256)
        assertFalse(atlas.geometryPolicy.oemClaim)
    }

    @Test
    fun `integrated regions redirect commerce to a parent`() {
        val cylinderOne = atlas.elements.single { it.ordinal == 2 }
        val mainJournal = atlas.elements.single { it.ordinal == 28 }

        listOf(cylinderOne, mainJournal).forEach { element ->
            assertEquals("INTEGRATED_FEATURE", element.elementKind)
            assertEquals("SEMANTIC_REGION", element.visual.renderStrategy)
            assertFalse(element.commerce.directlySellable)
            assertTrue(element.commerce.redirectToParent)
            assertTrue(element.parentCanonicalId != null)
        }
    }

    @Test
    fun `cvvt remains physically conditional`() {
        atlas.elements.filter { it.ordinal in 106..109 }.forEach { element ->
            assertEquals("CONDITIONAL_VARIANT", element.elementKind)
            assertEquals(
                "PENDING_PHYSICAL_CONFIRMATION",
                element.applicability.installedState,
            )
            assertTrue("INSPECT_CYLINDER_HEAD" in element.evidenceRequirements)
        }
    }

    @Test
    fun `search is accent insensitive and supports commerce filters`() {
        val crankResults = G4edEngineAtlasEngine.search(
            elements = atlas.elements,
            query = "ciguenal",
        )
        val sellableOnly = G4edEngineAtlasEngine.search(
            elements = atlas.elements,
            query = "munones",
            directlySellableOnly = true,
        )

        assertTrue(crankResults.any { it.ordinal == 27 })
        assertTrue(crankResults.any { it.ordinal == 28 })
        assertTrue(sellableOnly.isEmpty())
    }

    @Test
    fun `parser rejects any injected OEM geometry claim`() {
        val overstated = raw.replaceFirst(
            "\"oemClaim\": false",
            "\"oemClaim\": true",
        )

        val failure = runCatching { G4edEngineAtlasParser.decode(overstated) }
        assertTrue(failure.isFailure)
    }
}
