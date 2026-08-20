package com.elysium369.meet.ui.home

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HomeExperienceRepositoryTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = data[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) temp[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                data.putAll(temp)
                return true
            }
            override fun apply() {
                data.putAll(temp)
            }
        }
    }

    private class FakeContext(private val fakePrefs: FakeSharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = fakePrefs
        override fun getApplicationContext(): Context = this
    }

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var fakeContext: Context

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        fakeContext = FakeContext(fakePrefs)
    }

    @Test
    fun testDefaultExperienceIsClassic() {
        val repository = DefaultHomeExperienceRepository(fakeContext)
        assertEquals(HomeExperience.CLASSIC, repository.getExperience())
        assertEquals(HomeExperience.CLASSIC, repository.selectedExperience.value)
    }

    @Test
    fun testSetExperiencePersistsAndUpdatesFlow() {
        val repository = DefaultHomeExperienceRepository(fakeContext)
        repository.setExperience(HomeExperience.ADAPTIVE)

        assertEquals("ADAPTIVE", fakePrefs.getString("meet_home_experience_v1", null))
        assertEquals(HomeExperience.ADAPTIVE, repository.selectedExperience.value)
    }

    @Test
    fun testStoredAdaptiveExperienceLoadedCorrectly() {
        fakePrefs.edit().putString("meet_home_experience_v1", "ADAPTIVE").commit()
        val repository = DefaultHomeExperienceRepository(fakeContext)

        assertEquals(HomeExperience.ADAPTIVE, repository.getExperience())
    }
}
