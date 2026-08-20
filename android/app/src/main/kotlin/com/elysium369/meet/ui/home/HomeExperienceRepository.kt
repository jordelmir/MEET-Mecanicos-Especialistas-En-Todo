package com.elysium369.meet.ui.home

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface HomeExperienceRepository {
    val selectedExperience: StateFlow<HomeExperience>
    fun setExperience(experience: HomeExperience)
    fun getExperience(): HomeExperience
}

@Singleton
class DefaultHomeExperienceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : HomeExperienceRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _selectedExperience = MutableStateFlow(loadInitialExperience())
    override val selectedExperience: StateFlow<HomeExperience> = _selectedExperience.asStateFlow()

    private fun loadInitialExperience(): HomeExperience {
        val stored = prefs.getString(KEY_HOME_EXPERIENCE, null)
        return when (stored) {
            HomeExperience.ADAPTIVE.name -> HomeExperience.ADAPTIVE
            HomeExperience.CLASSIC.name -> HomeExperience.CLASSIC
            else -> HomeExperience.CLASSIC // Mandatory default
        }
    }

    override fun setExperience(experience: HomeExperience) {
        prefs.edit().putString(KEY_HOME_EXPERIENCE, experience.name).apply()
        _selectedExperience.value = experience
    }

    override fun getExperience(): HomeExperience {
        return _selectedExperience.value
    }

    companion object {
        private const val PREFS_NAME = "meet_prefs"
        private const val KEY_HOME_EXPERIENCE = "meet_home_experience_v1"
    }
}
