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
import org.mockito.Mockito.never

class AlertManagerTest {

    @Test
    fun testAlertCooldownPreventsFlooding() {
        val mockContext = mock(Context::class.java)
        val mockVoice = mock(VoiceFeedbackManager::class.java)
        val manager = AlertManager(mockContext, mockVoice)

        // Threshold configuration
        manager.maxTempThreshold = 100f

        // First event over threshold -> should trigger and speak
        manager.processLiveData(mapOf("0105" to 105f), isEngineRunning = true)
        verify(mockVoice, times(1)).speak(anyString(), anyString())

        // Second event immediately after (no time elapsed) -> should be blocked by cooldown and not speak again
        manager.processLiveData(mapOf("0105" to 107f), isEngineRunning = true)
        verify(mockVoice, times(1)).speak(anyString(), anyString()) // Speak count remains 1
    }

    @Test
    fun testVoltageAlertThresholdWarning() {
        val mockContext = mock(Context::class.java)
        val mockVoice = mock(VoiceFeedbackManager::class.java)
        val manager = AlertManager(mockContext, mockVoice)

        // Min voltage with engine off is 11.8V. 11.5V should trigger warning
        manager.processLiveData(mapOf("AT RV" to 11.5f), isEngineRunning = false)
        verify(mockVoice, times(1)).speak(anyString(), anyString())
    }
}
