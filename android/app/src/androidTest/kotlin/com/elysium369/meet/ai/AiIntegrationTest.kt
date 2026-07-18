package com.elysium369.meet.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elysium369.meet.ai.data.AiProviderRegistry
import com.elysium369.meet.ai.data.AiSecureKeyStoreImpl
import com.elysium369.meet.ai.data.AiUsageTracker
import com.elysium369.meet.ai.data.AiPromptStore
import com.elysium369.meet.ai.data.AiRepositoryImpl
import com.elysium369.meet.ai.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration test that runs on-device and makes a live network call
 * to MiniMax using the debug API key defined in local.properties.
 *
 * Run with:
 *   ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elysium369.meet.ai.AiIntegrationTest
 */
@RunWith(AndroidJUnit4::class)
class AiIntegrationTest {

    @Test
    fun testRealMiniMaxAiCall() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Instantiate secure keystore and repository
        val keyStore = AiSecureKeyStoreImpl(appContext)
        val registry = AiProviderRegistry(keyStore)
        val usageTracker = AiUsageTracker()
        val promptStore = AiPromptStore()
        val repository = AiRepositoryImpl(registry, usageTracker, promptStore)

        // Verify provider registry contains MiniMax
        val provider = registry.getProvider("minimax")
        assertNotNull("MiniMax provider should be registered", provider)

        // Create AI request context
        val context = AiContext(
            vehicle = VehicleContext(
                make = "Toyota", model = "Corolla", year = 2018,
                engine = "1.8L 4cyl", transmission = "CVT",
                fuel = "Gasolina", vin = "INTEGRATIONTEST1",
                odometer = 85000.0
            ),
            obd = ObdContext(
                connected = false,
                activePidsCount = 0,
                dtcActiveCount = 1,
                batteryVoltage = 12.6f
            ),
            dtcs = listOf(DtcContext("P0171", "Activo")),
            livePids = emptyList(),
            manualAvailability = null,
            appModule = "INTEGRATION_TEST",
            locale = "es-MX",
            userRole = UserRole.MECHANIC,
            safetyMode = true
        )

        val request = AiRequest(
            feature = AiFeature.DIAGNOSTIC_DTC,
            providerId = "minimax",
            model = "MiniMax-M1",
            messages = listOf(
                AiMessage(AiRole.USER, "Hola! Explica muy brevemente en español qué es el código DTC P0171.")
            ),
            context = context,
            timeoutMs = 30_000L
        )

        // Execute live call
        println("Sending integration test request to MiniMax...")
        val result = repository.complete(request)

        assertTrue(
            "AI response should be successful: ${result.exceptionOrNull()?.message}",
            result.isSuccess
        )

        val response = result.getOrThrow()
        println("MiniMax response text: ${response.text}")
        assertNotNull("Response text should not be null", response.text)
        assertTrue("Response text should not be empty", response.text.isNotBlank())
        assertTrue("Response should mention mezla or aire/combustible",
            response.text.lowercase().contains("mezcla") ||
            response.text.lowercase().contains("aire") ||
            response.text.lowercase().contains("combustible") ||
            response.text.lowercase().contains("p0171")
        )
    }
}
