package com.elysium.nexus.ui.wifi

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers the last ADB TV the user connected to, so
 * the "CONTROL UNIVERSAL · WI-FI" section auto-reconnects
 * on the next visit ("a la primera" en uso repetido).
 *
 * Tiny single-slot store; the IP is validated at
 * connect time, a stale value just falls back to the
 * scan list.
 */
class AdbTvMemory(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastTvIp(): String? =
        prefs.getString(KEY_LAST_TV, null)?.takeIf { it.isNotBlank() }

    fun setLastTv(ip: String) {
        prefs.edit().putString(KEY_LAST_TV, ip).apply()
    }

    companion object {
        private const val PREFS_NAME = "elysium_adb_tv"
        private const val KEY_LAST_TV = "last_ip"
    }
}