package com.elysium369.meet.core.alerts

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertManagerTest {

    // Simple context wrapper mock that returns null on getSystemService
    private class FakeContext : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? {
            return null
        }
        override fun getApplicationContext(): Context {
            return this
        }
    }

    @Test
    fun testAlertCooldownPreventsFlooding() {
        val fakeContext = FakeContext()
        val manager = AlertManager(fakeContext, null) // Pass null voiceFeedbackManager to prevent NPE

        // Accumulator to capture emitted alerts
        val alertsList = mutableListOf<ObdAlert>()
        
        // Threshold configuration
        manager.maxTempThreshold = 100f

        // Coroutine flow collector mock
        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            manager.alerts.collect {
                alertsList.add(it)
            }
        }

        // First event over threshold -> should trigger and emit alert
        manager.processLiveData(mapOf("0105" to 105f), isEngineRunning = true)
        assertEquals(1, alertsList.size)
        assertEquals("Sobrecalentamiento", alertsList.first().title)

        // Second event immediately after (no time elapsed) -> should be blocked by cooldown
        manager.processLiveData(mapOf("0105" to 107f), isEngineRunning = true)
        assertEquals(1, alertsList.size) // Remains 1, block was successful!

        job.cancel()
    }

    @Test
    fun testVoltageAlertThresholdWarning() {
        val fakeContext = FakeContext()
        val manager = AlertManager(fakeContext, null)

        val alertsList = mutableListOf<ObdAlert>()
        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            manager.alerts.collect {
                alertsList.add(it)
            }
        }

        // Min voltage with engine off is 11.8V. 11.5V should trigger warning
        manager.processLiveData(mapOf("AT RV" to 11.5f), isEngineRunning = false)
        assertEquals(1, alertsList.size)
        assertEquals("Voltaje Bajo", alertsList.first().title)

        job.cancel()
    }
}
