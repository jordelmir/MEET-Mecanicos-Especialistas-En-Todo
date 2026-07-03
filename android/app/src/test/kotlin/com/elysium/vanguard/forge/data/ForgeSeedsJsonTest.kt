package com.elysium.vanguard.forge.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeSeedsJsonTest {
    private val loader = ForgeSeedLoader(context = null)

    private fun readSeed(name: String): String =
        java.io.File(
            "/Users/jordelmirsdevhome/Downloads/Web Apps/MEET Mecanicos Especialistas En Todo/android/app/src/main/assets/forge/$name"
        ).readText()

    @Test
    fun partsSeedParses() {
        val text = readSeed("forge_parts_seed.json")
        val result = runBlocking { loader.parseBundleText(text) }
        if (result.isFailure) {
            throw AssertionError("parse failed: ${result.exceptionOrNull()?.message}")
        }
        val docs = result.getOrThrow()
        assertEquals(9, docs.size)
        assertTrue(docs.all { it is ForgeArtifactDocument.PartDocument })
    }

    @Test
    fun assembliesSeedParses() {
        val text = readSeed("forge_assemblies_seed.json")
        val result = runBlocking { loader.parseBundleText(text) }
        if (result.isFailure) {
            throw AssertionError("parse failed: ${result.exceptionOrNull()?.message}")
        }
        val docs = result.getOrThrow()
        assertEquals(4, docs.size)
        assertTrue(docs.all { it is ForgeArtifactDocument.AssemblyDocument })
    }

    @Test
    fun materialsSeedParses() {
        val text = readSeed("forge_materials_seed.json")
        val result = runBlocking { loader.parseBundleText(text) }
        if (result.isFailure) {
            throw AssertionError("parse failed: ${result.exceptionOrNull()?.message}")
        }
        val docs = result.getOrThrow()
        assertEquals(17, docs.size)
        assertTrue(docs.all { it is ForgeArtifactDocument.MaterialDocument })
    }

    @Test
    fun manufacturingSeedParses() {
        val text = readSeed("forge_manufacturing_seed.json")
        val result = runBlocking { loader.parseBundleText(text) }
        if (result.isFailure) {
            throw AssertionError("parse failed: ${result.exceptionOrNull()?.message}")
        }
        val docs = result.getOrThrow()
        assertEquals(22, docs.size)
        assertTrue(docs.all { it is ForgeArtifactDocument.ProcessDocument })
    }
}
