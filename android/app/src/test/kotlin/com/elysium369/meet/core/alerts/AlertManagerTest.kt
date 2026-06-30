package com.elysium369.meet.core.alerts

import android.content.Context
import com.elysium369.meet.core.audio.VoiceFeedbackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.Mockito.anyString

class AlertManagerTest {

    private class FakeVoiceFeedbackManager : VoiceFeedbackManager(mock(Context::class.java)) {
        var speakCount = 0
        override fun speak(spanishText: String, englishText: String) {
            speakCount++
        }
    }

    @Test
    fun testAlertCooldownPreventsFlooding() {
        val mockContext = mock(Context::class.java)
        val fakeVoice = FakeVoiceFeedbackManager()
        val manager = AlertManager(mockContext, fakeVoice)

        // Threshold configuration
        manager.maxTempThreshold = 100f

        // First event over threshold -> should trigger
        manager.processLiveData(mapOf("0105" to 105f), isEngineRunning = true)
        assertEquals(1, fakeVoice.speakCount)

        // Second event immediately after (no time elapsed) -> should be blocked by cooldown
        manager.processLiveData(mapOf("0105" to 107f), isEngineRunning = true)
        assertEquals(1, fakeVoice.speakCount) // Remains 1, block was successful!
    }

    @Test
    fun testVoltageAlertThresholdChange() {
        val mockContext = mock(Context::class.java)
        val fakeVoice = FakeVoiceFeedbackManager()
        val manager = AlertManager(mockContext, fakeVoice)

        // Min voltage with engine off is 11.8V. 11.5V should trigger warning
        manager.processLiveData(mapOf("AT RV" to 11.5f), isEngineRunning = false)
        assertEquals(1, fakeVoice.speakCount)
    }
}
