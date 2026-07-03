package com.elysium.vanguard.forge.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests del [JsonFileStore] genérico + [JsonFileStoreBridge] para Part.
 *
 * Usa un directorio temporal para no contaminar el filesystem real.
 */
class JsonFileStoreTest {

    private lateinit var tempDir: File
    private lateinit var storeFile: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("jsonfilestore-test").toFile()
        storeFile = File(tempDir, "test.json")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `load returns null when file does not exist`() = runBlocking {
        val store = JsonFileStore<String>(
            file = storeFile,
            serializer = { it },
            deserializer = { it }
        )
        assertNull(store.load())
    }

    @Test
    fun `persist creates file then load returns it`() = runBlocking {
        val data = "hello world"
        val store = JsonFileStore<String>(
            file = storeFile,
            serializer = { it },
            deserializer = { it }
        )
        store.persist(data)
        assertTrue("File debe existir tras persist", storeFile.exists())
        val loaded = store.load()
        assertEquals(data, loaded)
    }

    @Test
    fun `persist overwrites existing file`() = runBlocking {
        val store = JsonFileStore<String>(
            file = storeFile,
            serializer = { it },
            deserializer = { it }
        )
        store.persist("first")
        store.persist("second")
        assertEquals("second", store.load())
    }

    @Test
    fun `clear removes file`() = runBlocking {
        val store = JsonFileStore<String>(
            file = storeFile,
            serializer = { it },
            deserializer = { it }
        )
        store.persist("something")
        assertTrue(storeFile.exists())
        store.clear()
        assertFalse(storeFile.exists())
    }

    @Test
    fun `clear on missing file is no-op`() = runBlocking {
        val store = JsonFileStore<String>(
            file = storeFile,
            serializer = { it },
            deserializer = { it }
        )
        // Sin file previo, no debe crashear.
        store.clear()
        assertFalse(storeFile.exists())
    }

    @Test
    fun `persist creates parent directory if missing`() = runBlocking {
        val nestedFile = File(tempDir, "deep/nested/path/test.json")
        val store = JsonFileStore<String>(
            file = nestedFile,
            serializer = { it },
            deserializer = { it }
        )
        store.persist("test")
        assertTrue(nestedFile.exists())
        assertEquals("test", store.load())
    }

    @Test
    fun `load returns null when deserializer throws`() = runBlocking {
        // Forzamos contenido arbitrario y un deserializer que SIEMPRE lanza.
        storeFile.parentFile.mkdirs()
        storeFile.writeText("arbitrary content that the deserializer will reject")
        val store = JsonFileStore<String>(
            file = storeFile,
            serializer = { it },
            // Deserializer que rechaza cualquier input.
            deserializer = { _ -> throw IllegalArgumentException("forced failure") }
        )
        // El try-catch interno debe convertir la excepcion en null.
        assertNull(store.load())
    }

    @Test
    fun `load handles empty file`() = runBlocking {
        // File existe pero vacio.
        storeFile.parentFile.mkdirs()
        storeFile.writeText("")
        val store = JsonFileStore<String>(
            file = storeFile,
            serializer = { it },
            deserializer = { it }
        )
        assertNull(store.load())
    }
}