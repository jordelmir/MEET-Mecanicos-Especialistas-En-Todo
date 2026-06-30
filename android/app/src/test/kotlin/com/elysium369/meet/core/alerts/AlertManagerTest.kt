package com.elysium369.meet.core.alerts

import android.content.Context
import com.elysium369.meet.core.audio.VoiceFeedbackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AlertManagerTest {

    // Now legal and will compile because VoiceFeedbackManager is marked as 'open'
    private class FakeVoiceFeedbackManager(context: Context) : VoiceFeedbackManager(context) {
        var speakCount = 0
        override fun speak(es: String, en: String, queueMode: Int) {
            speakCount++
        }
    }

    @Test
    fun testAlertCooldownPreventsFlooding() {
        val mockContext = mock(Context::class.java)
        val fakeVoice = FakeVoiceFeedbackManager(mockContext)
        val manager = AlertManager(mockContext, fakeVoice)

        // Threshold configuration
        manager.maxTempThreshold = 100f

        // First event over threshold -> should trigger and speak
        manager.processLiveData(mapOf("0105" to 105f), isEngineRunning = true)
        assertEquals(1, fakeVoice.speakCount)

        // Second event immediately after (no time elapsed) -> should be blocked by cooldown and not speak again
        manager.processLiveData(mapOf("0105" to 107f), isEngineRunning = true)
        assertEquals(1, fakeVoice.speakCount) // Speak count remains 1
    }

    @Test
    fun testVoltageAlertThresholdWarning() {
        val mockContext = mock(Context::class.java)
        val fakeVoice = FakeVoiceFeedbackManager(mockContext)
        val manager = AlertManager(mockContext, fakeVoice)

        // Min voltage with engine off is 11.8V. 11.5V should trigger warning
        manager.processLiveData(mapOf("AT RV" to 11.5f), isEngineRunning = false)
        assertEquals(1, fakeVoice.speakCount)
    }
}
