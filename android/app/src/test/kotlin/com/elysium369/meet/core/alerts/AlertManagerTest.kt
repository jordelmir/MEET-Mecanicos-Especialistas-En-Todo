package com.elysium369.meet.core.alerts

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.elysium369.meet.core.audio.VoiceFeedbackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertManagerTest {

    // Helper context implementation using Android's native ContextWrapper to bypass compile dependencies issues
    private class FakeContext : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? {
            return null
        }
        override fun getApplicationContext(): Context {
            return this
        }
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return FakeSharedPreferences()
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
        override fun getString(key: String?, defValue: String?): String? = "es"
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = emptySet()
        override fun getInt(key: String?, defValue: Int): Int = 0
        override fun getLong(key: String?, defValue: Long): Long = 0L
        override fun getFloat(key: String?, defValue: Float): Float = 0f
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = true
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    // Inherit from open VoiceFeedbackManager and pass our fake context
    private class FakeVoiceFeedbackManager(context: Context) : VoiceFeedbackManager(context) {
        var speakCount = 0
        override fun speak(es: String, en: String, queueMode: Int) {
            speakCount++
        }
    }

    @Test
    fun testAlertCooldownPreventsFlooding() {
        val fakeContext = FakeContext()
        val fakeVoice = FakeVoiceFeedbackManager(fakeContext)
        val manager = AlertManager(fakeContext, fakeVoice)

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
        val fakeContext = FakeContext()
        val fakeVoice = FakeVoiceFeedbackManager(fakeContext)
        val manager = AlertManager(fakeContext, fakeVoice)

        // Min voltage with engine off is 11.8V. 11.5V should trigger warning
        manager.processLiveData(mapOf("AT RV" to 11.5f), isEngineRunning = false)
        assertEquals(1, fakeVoice.speakCount)
    }
}
