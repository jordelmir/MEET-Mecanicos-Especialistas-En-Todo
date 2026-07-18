package com.elysium369.meet.ai

import com.elysium369.meet.ai.data.SecretRedactor
import com.elysium369.meet.ai.data.AiJsonRepair
import com.elysium369.meet.ai.domain.*
import com.elysium369.meet.ai.providers.openai_compatible.SseStreamParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the multi-provider AI engine.
 * Run with: ./gradlew :app:testDebugUnitTest --tests "com.elysium369.meet.ai.*"
 */
class AiEngineTests {

    // ================================================================
    //  1. SECRET REDACTION
    // ================================================================

    @Test
    fun `SecretRedactor removes OpenAI-style sk- keys`() {
        val input = "Error with key sk-abcdefghij1234567890abcdefghij"
        val redacted = SecretRedactor.redact(input)
        assertFalse("sk- key should be redacted", redacted.contains("sk-abcdefghij"))
        assertTrue("Redaction marker should be present", redacted.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun `SecretRedactor removes Google AIza keys`() {
        val input = "Using key AIzaSyAbCdEfGhIjKlMnOpQrStUvWxYz0123456"
        val redacted = SecretRedactor.redact(input)
        assertFalse("AIza key should be redacted", redacted.contains("AIzaSy"))
        assertTrue(redacted.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun `SecretRedactor removes Bearer tokens`() {
        val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature"
        val redacted = SecretRedactor.redact(input)
        assertFalse("Bearer token should be redacted", redacted.contains("eyJhbGci"))
    }

    @Test
    fun `SecretRedactor removes Telegram bot tokens`() {
        val input = "Bot token: 1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ-abcdefghi"
        val redacted = SecretRedactor.redact(input)
        assertFalse("Telegram token should be redacted", redacted.contains("1234567890:ABC"))
    }

    @Test
    fun `SecretRedactor removes generic api_key patterns`() {
        val input = "api_key=sk_test_abcdefghijklmnopqrst"
        val redacted = SecretRedactor.redact(input)
        assertTrue(redacted.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun `SecretRedactor preserves non-secret text`() {
        val input = "El código DTC P0171 indica mezcla pobre. Verificar sensor MAF."
        val redacted = SecretRedactor.redact(input)
        assertEquals("Non-secret text should be unchanged", input, redacted)
    }

    // ================================================================
    //  2. AI ROLE SERIALIZATION
    // ================================================================

    @Test
    fun `AiRole toString produces lowercase for OpenAI API compatibility`() {
        assertEquals("system", AiRole.SYSTEM.toString())
        assertEquals("user", AiRole.USER.toString())
        assertEquals("assistant", AiRole.ASSISTANT.toString())
    }

    // ================================================================
    //  3. SSE STREAM PARSER
    // ================================================================

    @Test
    fun `SseStreamParser extracts token from valid data line`() {
        val line = """data: {"choices":[{"delta":{"content":"Hola"}}]}"""
        val token = SseStreamParser.parseLine(line)
        assertEquals("Hola", token)
    }

    @Test
    fun `SseStreamParser returns null for DONE signal`() {
        val token = SseStreamParser.parseLine("data: [DONE]")
        assertNull(token)
    }

    @Test
    fun `SseStreamParser returns null for non-data lines`() {
        assertNull(SseStreamParser.parseLine(": keep-alive"))
        assertNull(SseStreamParser.parseLine(""))
        assertNull(SseStreamParser.parseLine("event: message"))
    }

    @Test
    fun `SseStreamParser handles malformed JSON gracefully`() {
        val token = SseStreamParser.parseLine("data: {broken json")
        assertNull("Malformed JSON should return null", token)
    }

    // ================================================================
    //  4. JSON REPAIR
    // ================================================================

    @Test
    fun `AiJsonRepair extracts JSON from markdown code blocks`() {
        val input = """Here is the analysis:
```json
{"dtc": "P0171", "severity": "medium"}
```
End of analysis."""
        val result = AiJsonRepair.repairAndParse(input)
        assertNotNull("Should extract JSON from markdown", result)
        assertEquals("P0171", result!!.get("dtc")?.toString()?.trim('"'))
    }

    @Test
    fun `AiJsonRepair fixes trailing commas`() {
        val input = """{"dtc": "P0171", "severity": "medium",}"""
        val result = AiJsonRepair.repairAndParse(input)
        assertNotNull("Should fix trailing comma", result)
    }

    @Test
    fun `AiJsonRepair returns null for non-JSON text`() {
        val input = "This is just plain diagnostic text without any JSON."
        val result = AiJsonRepair.repairAndParse(input)
        assertNull("Non-JSON text should return null", result)
    }

    // ================================================================
    //  5. AI ERROR MESSAGES
    // ================================================================

    @Test
    fun `AiError MissingApiKey provides Spanish user-facing message`() {
        val error = AiError.MissingApiKey
        assertNotNull(error.message)
        assertTrue(error.message!!.contains("API key"))
    }

    @Test
    fun `AiError HttpFailure 401 maps to invalid key message`() {
        val error = AiError.HttpFailure(401, "unauthorized")
        assertTrue(error.message!!.contains("inválida") || error.message!!.contains("revocada"))
    }

    @Test
    fun `AiError HttpFailure 429 maps to rate limit message`() {
        val error = AiError.HttpFailure(429, "too many requests")
        assertTrue(error.message!!.contains("Límite") || error.message!!.contains("uso"))
    }

    // ================================================================
    //  6. DOMAIN MODEL INTEGRITY
    // ================================================================

    @Test
    fun `AiRequest defaults are correct`() {
        val context = AiContext(
            vehicle = null, obd = null, dtcs = emptyList(),
            livePids = emptyList(), manualAvailability = null,
            appModule = "TEST", locale = "es-MX",
            userRole = UserRole.MECHANIC
        )
        val request = AiRequest(
            feature = AiFeature.DIAGNOSTIC_DTC,
            providerId = "minimax",
            model = "MiniMax-M1",
            messages = listOf(AiMessage(AiRole.USER, "Test")),
            context = context
        )
        assertEquals(0.2, request.temperature, 0.001)
        assertEquals(1600, request.maxTokens)
        assertFalse(request.jsonMode)
        assertFalse(request.stream)
        assertEquals(60_000L, request.timeoutMs)
    }

    @Test
    fun `AiFeature enum has all 15 required features`() {
        assertEquals(15, AiFeature.values().size)
        assertNotNull(AiFeature.valueOf("DIAGNOSTIC_DTC"))
        assertNotNull(AiFeature.valueOf("AI_COPILOT"))
        assertNotNull(AiFeature.valueOf("LIVE_PID_ANALYSIS"))
        assertNotNull(AiFeature.valueOf("OSCILLOSCOPE_ANALYSIS"))
        assertNotNull(AiFeature.valueOf("GAUGE_MARKET"))
    }

    @Test
    fun `AiCapability enum has all 5 capabilities`() {
        assertEquals(5, AiCapability.values().size)
        assertNotNull(AiCapability.valueOf("CHAT"))
        assertNotNull(AiCapability.valueOf("STREAMING"))
        assertNotNull(AiCapability.valueOf("JSON_MODE"))
    }

    @Test
    fun `AiContext defaults safetyMode to true`() {
        val context = AiContext(
            vehicle = null, obd = null, dtcs = emptyList(),
            livePids = emptyList(), manualAvailability = null,
            appModule = "TEST", locale = "es-MX",
            userRole = UserRole.MECHANIC
        )
        assertTrue("safetyMode should default to true", context.safetyMode)
    }
}
