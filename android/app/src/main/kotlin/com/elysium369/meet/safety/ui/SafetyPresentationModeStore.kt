package com.elysium369.meet.safety.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the active presentation mode of the platform.
 *
 * Implements ADR-003 and the Parliamentary Presentation Mandate for the
 * Legislative Assembly of Costa Rica.
 *
 * Allows toggling between:
 * - [PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY]: Isolates the entire experience
 *   to Elysium Safety (Citizen Security, Digital Evidence, Territorial Analysis & Public Integrity),
 *   completely hiding automotive, mechanic, OBD, rides, and marketplace modules.
 * - [PresentationMode.FULL_PLATFORM_OPERATING_SYSTEM]: Full operating system preserving
 *   100% of all platform modules and capabilities intact.
 */
class SafetyPresentationModeStore(
    private val context: Context? = null,
    initialMode: PresentationMode = PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY,
) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        prefs?.getString(KEY_MODE, null)?.let { saved ->
            runCatching { PresentationMode.valueOf(saved) }.getOrNull()
        } ?: initialMode
    )
    val mode: StateFlow<PresentationMode> = _mode.asStateFlow()

    fun getMode(): PresentationMode = _mode.value

    fun isInstitutionalMode(): Boolean = _mode.value == PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY

    fun setMode(newMode: PresentationMode) {
        _mode.value = newMode
        prefs?.edit()?.putString(KEY_MODE, newMode.name)?.apply()
    }

    fun toggleMode(): PresentationMode {
        val next = if (_mode.value == PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY) {
            PresentationMode.FULL_PLATFORM_OPERATING_SYSTEM
        } else {
            PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY
        }
        setMode(next)
        return next
    }

    companion object {
        const val PREFS_NAME = "elysium_safety_presentation_prefs"
        const val KEY_MODE = "active_presentation_mode"

        @Volatile
        private var instance: SafetyPresentationModeStore? = null

        fun getInstance(context: Context? = null): SafetyPresentationModeStore {
            return instance ?: synchronized(this) {
                instance ?: SafetyPresentationModeStore(context?.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
