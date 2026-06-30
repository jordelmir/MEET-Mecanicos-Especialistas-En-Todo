package com.elysium369.meet.core.audio

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandManagerTest {

    // Simple AudioManager fake to bypass non-null cast in VoiceFeedbackManager init block
    private class FakeAudioManager : android.media.AudioManager()

    // Context Wrapper Fake supplying minimal services to avoid NPE using Java reflection
    private class FakeContext(private val lang: String) : ContextWrapper(null) {
        private val fakeAudioManager by lazy {
            try {
                // Try obtaining the empty constructor reflexively
                val constructor = android.media.AudioManager::class.java.getDeclaredConstructor()
                constructor.isAccessible = true
                constructor.newInstance()
            } catch (e: Exception) {
                try {
                    // Fallback to the context-based constructor reflexively
                    val constructor = android.media.AudioManager::class.java.getDeclaredConstructor(Context::class.java)
                    constructor.isAccessible = true
                    constructor.newInstance(this)
                } catch (ex: Exception) {
                    null
                }
            }
        }
        
        override fun getSystemService(name: String): Any? {
            if (name == Context.AUDIO_SERVICE) return fakeAudioManager
            return null
        }
        override fun getApplicationContext(): Context {
            return this
        }
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return FakeSharedPreferences(lang)
        }
        override fun getPackageName(): String {
            return "com.elysium369.meet.test"
        }
    }

    private class FakeSharedPreferences(private val lang: String) : SharedPreferences {
        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
        override fun getString(key: String?, defValue: String?): String? {
            if (key == "app_language") return lang
            return "es"
        }
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = emptySet()
        override fun getInt(key: String?, defValue: Int): Int = 0
        override fun getLong(key: String?, defValue: Long): Long = 0L
        override fun getFloat(key: String?, defValue: Float): Float = 0f
        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            if (key == "voice_feedback_enabled") return true
            return false
        }
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private class FakeVoiceFeedbackManager(context: Context) : VoiceFeedbackManager(context) {
        var lastSpeakSpoken: String? = null
        override fun speak(es: String, en: String, queueMode: Int) {
            lastSpeakSpoken = es
        }
    }

    @Test
    fun testProcessSpokenTextWithWakeWordSpanish() {
        val fakeContext = FakeContext("es")
        val fakeVoice = FakeVoiceFeedbackManager(fakeContext)
        val manager = VoiceCommandManager(fakeContext, fakeVoice, Dispatchers.Unconfined)

        var recognizedCommand: VoiceCommand? = null
        manager.onCommandRecognized = { command ->
            recognizedCommand = command
        }

        // Call the internal command evaluation method reflexively
        val method = VoiceCommandManager::class.java.getDeclaredMethod("processSpokenText", String::class.java)
        method.isAccessible = true

        // Command with wake word: "elysium temperatura"
        method.invoke(manager, "elysium temperatura")

        assertEquals(VoiceCommand.SAY_TEMPERATURE, recognizedCommand)
    }

    @Test
    fun testProcessSpokenTextWithWakeWordEnglish() {
        val fakeContext = FakeContext("en")
        val fakeVoice = FakeVoiceFeedbackManager(fakeContext)
        val manager = VoiceCommandManager(fakeContext, fakeVoice, Dispatchers.Unconfined)

        var recognizedCommand: VoiceCommand? = null
        manager.onCommandRecognized = { command ->
            recognizedCommand = command
        }

        val method = VoiceCommandManager::class.java.getDeclaredMethod("processSpokenText", String::class.java)
        method.isAccessible = true

        // Command with wake word in English: "elysium clear codes"
        method.invoke(manager, "elysium clear codes")

        assertEquals(VoiceCommand.CLEAR_DTCS, recognizedCommand)
        assertEquals("Borrando códigos de falla de la ECU.", fakeVoice.lastSpeakSpoken)
    }
}
