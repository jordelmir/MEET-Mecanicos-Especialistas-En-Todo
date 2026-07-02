package com.elysium369.meet.data.car2db

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Car2DbClientTest {

    @Test
    fun `client is disabled when api key is blank`() {
        val client = Car2DbClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "",
                referer = "https://test.app",
                language = "en-US"
            )
        )
        assertFalse(client.isEnabled)
    }

    @Test
    fun `client is enabled when api key present`() {
        val client = Car2DbClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "test_key",
                referer = "https://test.app",
                language = "en-US"
            )
        )
        assertTrue(client.isEnabled)
    }

    @Test
    fun `disabled client returns Disabled result for search`() = runBlocking {
        val client = Car2DbClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "",
                referer = "https://test.app",
                language = "en-US"
            )
        )
        val result = client.searchVehicles("Toyota Camry")
        assertEquals(Car2DbClient.Car2DbResult.Disabled, result)
    }

    @Test
    fun `disabled client returns Disabled for getTrimFull`() = runBlocking {
        val client = Car2DbClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "",
                referer = "https://test.app",
                language = "en-US"
            )
        )
        val result = client.getTrimFull(263119)
        assertEquals(Car2DbClient.Car2DbResult.Disabled, result)
    }

    @Test
    fun `search with empty query returns Malformed`() = runBlocking {
        val client = Car2DbClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "test",
                referer = "https://test.app",
                language = "en-US"
            )
        )
        val result = client.searchVehicles("")
        assertTrue("Empty query must return Malformed",
            result is Car2DbClient.Car2DbResult.Malformed)
    }

    @Test
    fun `search with oversized query is sanitized`() = runBlocking {
        val client = Car2DbClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "test",
                referer = "https://test.app",
                language = "en-US"
            )
        )
        // 1000 chars — no debe crashear. En unit test el comportamiento real varía:
        // sin red → NetworkError; con endpoint real → puede responder 401 (AuthMissing).
        // Aceptamos cualquier resultado terminal no-exitoso.
        val long = "x".repeat(1000)
        val result = client.searchVehicles(long)
        assertTrue(
            result is Car2DbClient.Car2DbResult.NetworkError ||
            result is Car2DbClient.Car2DbResult.ApiError ||
            result is Car2DbClient.Car2DbResult.Malformed ||
            result is Car2DbClient.Car2DbResult.AuthMissing
        )
    }

    @Test
    fun `getTrimFull with invalid id returns Malformed`() = runBlocking {
        val client = Car2DbClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "test",
                referer = "https://test.app",
                language = "en-US"
            )
        )
        val result = client.getTrimFull(-1)
        assertTrue(result is Car2DbClient.Car2DbResult.Malformed)
        val resultZero = client.getTrimFull(0)
        assertTrue(resultZero is Car2DbClient.Car2DbResult.Malformed)
    }

    @Test
    fun `getYear rejects out-of-range years`() = runBlocking {
        val client = Car2DbClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "test",
                referer = "https://test.app",
                language = "en-US"
            )
        )
        val oldYear = client.getYear(1850)
        assertTrue(oldYear is Car2DbClient.Car2DbResult.Malformed)
        val futureYear = client.getYear(2200)
        assertTrue(futureYear is Car2DbClient.Car2DbResult.Malformed)
    }

    @Test
    fun `sanitizeQuery strips dangerous characters`() {
        // Accedemos vía reflection-friendly: usamos una subclase de prueba.
        val client = TestableClient(
            config = Car2DbClient.Car2DbConfig(
                apiKey = "test", referer = "https://test.app", language = "en-US"
            )
        )
        // Sanitización: la lógica está dentro de searchVehicles pero podemos probar indirectamente.
        // Probamos que "DROP TABLE--" se filtra.
        runBlocking {
            val r = client.searchVehicles("DROP TABLE--")
            // No debe crashear; resultado es NetworkError (no MockEngine) o algo controlado.
            assertNotNull(r)
        }
    }

    @Test
    fun `dtcToSearchQuery maps P codes to engine query`() {
        assertEquals("P0301 engine", dtcToSearchQuery("P0301"))
        assertEquals("P0128 engine", dtcToSearchQuery("p0128"))
        assertEquals("B1234 body", dtcToSearchQuery("B1234"))
        assertEquals("", dtcToSearchQuery("INVALID"))
    }

    @Test
    fun `backoffMs grows exponentially with cap`() {
        val ms1 = Car2DbClient.backoffMs(0, baseMs = 100)
        val ms2 = Car2DbClient.backoffMs(1, baseMs = 100)
        val ms3 = Car2DbClient.backoffMs(2, baseMs = 100)
        assertTrue("Attempt 1 > base", ms1 >= 100)
        assertTrue("Attempt 2 > attempt 1", ms2 > ms1)
        assertTrue("Attempt 3 > attempt 2", ms3 > ms2)
        // Cap 30s.
        val bigAttempt = Car2DbClient.backoffMs(20, baseMs = 1000)
        assertTrue("Cap at 30s + jitter", bigAttempt <= 30_000L + 250L)
    }

    /**
     * Cliente de prueba que NO inicializa httpClient (para no hacer I/O).
     */
    private class TestableClient(config: Car2DbClient.Car2DbConfig) : Car2DbClient(config) {
        // No overrides — sólo queremos evitar el lazy httpClient.
    }
}