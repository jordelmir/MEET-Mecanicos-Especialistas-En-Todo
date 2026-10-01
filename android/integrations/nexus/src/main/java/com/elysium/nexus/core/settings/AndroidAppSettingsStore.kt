package com.elysium.nexus.core.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Android-backed [AppSettingsStore].
 *
 * The store persists each field of [AppSettings]
 * as a single `SharedPreferences` key. The keys
 * are namespaced with the `settings.` prefix so
 * the activity's `SharedPreferences` can host
 * other documents without collisions.
 *
 * The store reads the document on construction
 * (via [load]) and caches it in a [StateFlow];
 * the [current] getter is a hot read of the
 * cached value. [update] writes the new value to
 * the cache and to the `SharedPreferences`. The
 * flow is the source of truth for the UI.
 *
 * ## Why a class and not a top-level function
 *
 * The store has a single dependency ([Context])
 * and holds the `SharedPreferences` handle. The
 * class makes the lifecycle explicit: the
 * activity owns the store for the activity's
 * lifetime; `onDestroy` does not need to do
 * anything special (`SharedPreferences` is
 * managed by the framework).
 *
 * ## Why a `StateFlow` and not a `SharedFlow`
 *
 * The settings always have a current value. A
 * `SharedFlow` would force the UI to handle the
 * "no value yet" case; a `StateFlow` always
 * carries the latest.
 */
class AndroidAppSettingsStore(
    context: Context
) : AppSettingsStore {
    private val tag = "ElysiumNexus.Settings"

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val state: MutableStateFlow<AppSettings> = MutableStateFlow(load())

    override val current: AppSettings
        get() = state.value

    override val updates: StateFlow<AppSettings>
        get() = state.asStateFlow()

    override fun update(settings: AppSettings) {
        state.value = settings
        prefs.edit().apply {
            putFloat(KEY_LEFT_SENSITIVITY, settings.leftStickSensitivity)
            putFloat(KEY_RIGHT_SENSITIVITY, settings.rightStickSensitivity)
            putBoolean(KEY_INVERT_LEFT_X, settings.invertLeftX)
            putBoolean(KEY_INVERT_LEFT_Y, settings.invertLeftY)
            putBoolean(KEY_INVERT_RIGHT_X, settings.invertRightX)
            putBoolean(KEY_INVERT_RIGHT_Y, settings.invertRightY)
            putBoolean(KEY_HAPTICS, settings.hapticsEnabled)
            putBoolean(KEY_DARK_THEME, settings.darkTheme)
        }.apply()
        Log.i(tag, "AppSettings persisted: $settings")
    }

    /**
     * Read the document from `SharedPreferences`. The
     * function is total: missing keys fall back to
     * the [AppSettings] defaults; out-of-range
     * floats are clamped to the sensitivity range.
     */
    private fun load(): AppSettings {
        val left = prefs.getFloat(KEY_LEFT_SENSITIVITY, 1.0f)
            .coerceIn(AppSettings.MIN_SENSITIVITY, AppSettings.MAX_SENSITIVITY)
        val right = prefs.getFloat(KEY_RIGHT_SENSITIVITY, 1.0f)
            .coerceIn(AppSettings.MIN_SENSITIVITY, AppSettings.MAX_SENSITIVITY)
        return AppSettings(
            leftStickSensitivity = left,
            rightStickSensitivity = right,
            invertLeftX = prefs.getBoolean(KEY_INVERT_LEFT_X, false),
            invertLeftY = prefs.getBoolean(KEY_INVERT_LEFT_Y, false),
            invertRightX = prefs.getBoolean(KEY_INVERT_RIGHT_X, false),
            invertRightY = prefs.getBoolean(KEY_INVERT_RIGHT_Y, false),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, true)
        )
    }

    companion object {
        /** The `SharedPreferences` file name. */
        const val PREFS_NAME: String = "elysium_settings"

        private const val KEY_LEFT_SENSITIVITY: String = "settings.leftStickSensitivity"
        private const val KEY_RIGHT_SENSITIVITY: String = "settings.rightStickSensitivity"
        private const val KEY_INVERT_LEFT_X: String = "settings.invertLeftX"
        private const val KEY_INVERT_LEFT_Y: String = "settings.invertLeftY"
        private const val KEY_INVERT_RIGHT_X: String = "settings.invertRightX"
        private const val KEY_INVERT_RIGHT_Y: String = "settings.invertRightY"
        private const val KEY_HAPTICS: String = "settings.hapticsEnabled"
        private const val KEY_DARK_THEME: String = "settings.darkTheme"
    }
}
