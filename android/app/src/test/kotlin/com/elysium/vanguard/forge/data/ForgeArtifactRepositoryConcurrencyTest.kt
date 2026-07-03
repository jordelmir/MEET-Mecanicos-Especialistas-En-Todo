package com.elysium.vanguard.forge.data

import com.elysium.vanguard.forge.domain.DimensionSet
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.SafetyClassification
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de concurrencia para [ForgeArtifactRepository.savePart] / [deleteArtifact].
 *
 * Verifica que el `Mutex.withLock` interno serializa correctamente operaciones
 * concurrentes — múltiples coroutines guardando al mismo tiempo no deben perder
 * escrituras ni corromper el estado in-memory.
 */
class ForgeArtifactRepositoryConcurrencyTest {

    private fun samplePart(id: String, version: Int = 1): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = id,
            name = "Part $id v$version",
            artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.EDUCATIONAL,
            version = version
        ),
        dimensions = DimensionSet(lengthMm = 10.0, widthMm = 10.0, heightMm = 10.0)
    )

    @Test
    fun `concurrent savePart does not lose writes`() = runBlocking {
        val repo = ForgeArtifactRepository()
        // 50 coroutines guardan a la vez, cada una con su propio ID unico.
        val writes = (0 until 50).map { i ->
            async { repo.savePart(samplePart("p_$i", version = 1)) }
        }
        awaitAll(*writes.toTypedArray())
        // Despues de todas las writes, el StateFlow debe tener 50 parts.
        assertEquals(50, repo.parts.value.size)
    }

    @Test
    fun `concurrent updates to same id keep last-write-wins`() = runBlocking {
        val repo = ForgeArtifactRepository()
        // 10 coroutines actualizan el MISMO id con versions diferentes (1..10).
        val writes = (0 until 10).map { i ->
            async { repo.savePart(samplePart("p_hot", version = i + 1)) }
        }
        awaitAll(*writes.toTypedArray())
        // Solo debe quedar 1 part (el id es el mismo).
        assertEquals(1, repo.parts.value.size)
        // El version del final debe ser uno de los escritos — no necesariamente
        // el último (el orden no es determinista con Mutex), pero debe estar
        // en el rango [1, 10].
        val version = repo.parts.value["p_hot"]?.artifact?.version
        assertNotNull("Part debe existir", version)
        assertTrue(
            "version ${version} fuera de rango esperado [1, 10]",
            version!! in 1..10
        )
    }

    @Test
    fun `concurrent save and delete result in consistent state`() = runBlocking {
        val repo = ForgeArtifactRepository()
        // Pre-poblar 20 parts.
        (0 until 20).forEach { i -> repo.savePart(samplePart("p_init_$i", version = 2)) }
        // Ahora: 10 coroutines hacen savePart sobre p_init_0..9 (sobrescribiendo),
        // 10 coroutines hacen deleteArtifact sobre p_init_10..19 (eliminando).
        val writes = (0 until 10).map { i ->
            async { repo.savePart(samplePart("p_init_$i", version = 99)) }
        }
        val deletes = (10 until 20).map { i ->
            async { repo.deleteArtifact("p_init_$i") }
        }
        awaitAll(*(writes + deletes).toTypedArray())
        // Tras todo: p_init_0..9 sobreviven (con version 99), p_init_10..19 eliminados.
        assertEquals(10, repo.parts.value.size)
        (0 until 10).forEach { i ->
            val p = repo.parts.value["p_init_$i"]
            assertNotNull("p_init_$i debe sobrevivir", p)
            assertEquals(
                "p_init_$i debe tener version 99",
                99,
                p!!.artifact.version
            )
        }
        (10 until 20).forEach { i ->
            assertNull(
                "p_init_$i debe haber sido eliminado",
                repo.parts.value["p_init_$i"]
            )
        }
    }

    @Test
    fun `delete on non-existent id is no-op`() = runBlocking {
        val repo = ForgeArtifactRepository()
        // No parts. Delete sobre id que no existe no debe lanzar excepcion.
        repo.deleteArtifact("nonexistent")
        assertEquals(0, repo.parts.value.size)
    }
}
