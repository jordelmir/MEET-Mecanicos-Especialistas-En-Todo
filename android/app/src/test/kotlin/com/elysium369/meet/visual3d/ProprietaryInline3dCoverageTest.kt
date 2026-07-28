package com.elysium369.meet.visual3d

import com.elysium369.meet.core.catalog.PROPRIETARY_CATALOG_ENTITY_INDEX_ASSET
import com.elysium369.meet.core.catalog.ProprietaryCatalogParser
import com.elysium369.meet.visual3d.domain.semanticInline3dExperience
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProprietaryInline3dCoverageTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: error("Missing asset $path")

    @Test
    fun `every proprietary component has a deterministic inline semantic scene`() {
        val index = ProprietaryCatalogParser.decodeEntityIndex(
            asset(PROPRIETARY_CATALOG_ENTITY_INDEX_ASSET).readText(),
        )
        val components = index.entities.filter { it.recordRole == "COMPONENT" }
        val experiences = components.map(::semanticInline3dExperience)

        assertEquals(4_753, components.size)
        assertEquals(components.size, experiences.size)
        assertEquals(components.map { it.id }.toSet(), experiences.map { it.node.id }.toSet())
        assertTrue(experiences.all { it.normalizedName.isNotBlank() })
        assertTrue(experiences.all { it.node.seed != 0L })
    }
}
