package com.elysium.nexus.core.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the last-sent AC state per device template.
 *
 * When the user opens the AC control screen, the
 * screen reads the last-known state (temperature,
 * mode, fan speed) instead of resetting to defaults.
 * Every adjustment writes the new state immediately.
 *
 * The store uses SharedPreferences with a
 * JSON-like pipe-delimited format per device.
 */
class AcStateStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The last-known AC state for a device, or null
     * if no state has been sent yet.
     */
    fun get(templateId: String): AcState? {
        val raw = prefs.getString(templateId, null) ?: return null
        return parse(raw)
    }

    /**
     * Persist the AC state for a device.
     */
    fun set(templateId: String, state: AcState) {
        prefs.edit().putString(templateId, state.serialize()).apply()
    }

    /**
     * Clear the state for a specific device.
     */
    fun clear(templateId: String) {
        prefs.edit().remove(templateId).apply()
    }

    /**
     * Clear all AC states.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun parse(raw: String): AcState? = AcState.parse(raw)

    companion object {
        private const val PREFS_NAME = "elysium_ac_state"
    }
}

/**
 * A snapshot of the last-known AC state.
 */
data class AcState(
    val temperature: Int = 24,
    val mode: Int = 1,
    val fanSpeed: Int = 0,
    val powerOn: Boolean = true
) {
    fun serialize(): String =
        "t=$temperature|m=$mode|f=$fanSpeed|p=${if (powerOn) 1 else 0}"

    companion object {
        fun parse(raw: String): AcState? {
            val map = mutableMapOf<String, String>()
            for (part in raw.split("|")) {
                val eq = part.indexOf('=')
                if (eq > 0) {
                    map[part.substring(0, eq)] = part.substring(eq + 1)
                }
            }
            return try {
                AcState(
                    temperature = map["t"]?.toIntOrNull() ?: 24,
                    mode = map["m"]?.toIntOrNull() ?: 1,
                    fanSpeed = map["f"]?.toIntOrNull() ?: 0,
                    powerOn = map["p"]?.toIntOrNull() != 0
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}
