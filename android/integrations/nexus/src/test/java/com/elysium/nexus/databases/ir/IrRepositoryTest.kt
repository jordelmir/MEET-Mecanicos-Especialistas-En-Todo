package com.elysium.nexus.databases.ir

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for [InMemoryIrRepository].
 */
class IrRepositoryTest {

    private lateinit var repo: InMemoryIrRepository

    @Before
    fun setup() {
        repo = InMemoryIrRepository()
    }

    @Test
    fun saveReturnsId() = runTest {
        val entity = makeEntity(label = "TV Power")
        val id = repo.save(entity)
        assertTrue(id > 0)
    }

    @Test
    fun byIdReturnsSavedEntity() = runTest {
        val entity = makeEntity(label = "TV Power")
        val id = repo.save(entity)
        val fetched = repo.byId(id)
        assertNotNull(fetched)
        assertEquals("TV Power", fetched!!.label)
    }

    @Test
    fun byIdReturnsNullForMissing() = runTest {
        assertNull(repo.byId(999))
    }

    @Test
    fun byTemplateIdReturnsMatching() = runTest {
        repo.save(makeEntity(label = "Power", templateId = "tv-samsung"))
        repo.save(makeEntity(label = "Vol Up", templateId = "tv-samsung"))
        repo.save(makeEntity(label = "AC On", templateId = "ac-daikin"))
        val samsung = repo.byTemplateId("tv-samsung")
        assertEquals(2, samsung.size)
    }

    @Test
    fun allReturnsAll() = runTest {
        repo.save(makeEntity(label = "A"))
        repo.save(makeEntity(label = "B"))
        repo.save(makeEntity(label = "C"))
        assertEquals(3, repo.all().size)
    }

    @Test
    fun countReturnsCorrectCount() = runTest {
        assertEquals(0, repo.count())
        repo.save(makeEntity(label = "A"))
        repo.save(makeEntity(label = "B"))
        assertEquals(2, repo.count())
    }

    @Test
    fun deleteByIdRemovesEntity() = runTest {
        val id = repo.save(makeEntity(label = "A"))
        assertEquals(1, repo.count())
        repo.deleteById(id)
        assertEquals(0, repo.count())
    }

    @Test
    fun deleteByTemplateIdRemovesAllForTemplate() = runTest {
        repo.save(makeEntity(label = "A", templateId = "tv"))
        repo.save(makeEntity(label = "B", templateId = "tv"))
        repo.save(makeEntity(label = "C", templateId = "ac"))
        repo.deleteByTemplateId("tv")
        assertEquals(1, repo.count())
        assertEquals("ac", repo.all().first().templateId)
    }

    @Test
    fun deleteAllRemovesEverything() = runTest {
        repo.save(makeEntity(label = "A"))
        repo.save(makeEntity(label = "B"))
        repo.deleteAll()
        assertEquals(0, repo.count())
    }

    @Test
    fun saveWithExistingIdReplaces() = runTest {
        val id = repo.save(makeEntity(label = "Original"))
        repo.save(makeEntity(id = id, label = "Updated"))
        val fetched = repo.byId(id)
        assertEquals("Updated", fetched!!.label)
    }

    private fun makeEntity(
        id: Long = 0,
        label: String = "Test",
        templateId: String = "test-device",
        protocolName: String = "Nec",
        address: Int = 0x01,
        command: Int = 0x02
    ) = LearnedIrCommandEntity(
        id = id,
        label = label,
        templateId = templateId,
        protocolName = protocolName,
        address = address,
        command = command,
        carrierHz = 38000,
        rawPattern = "9000,4500,560,560",
        confidence = 0.9f,
        capturedAtMs = System.currentTimeMillis()
    )
}
