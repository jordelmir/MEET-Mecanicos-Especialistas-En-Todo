package com.elysium.vanguard.core.trash

import android.content.Context

/**
 * PHASE 1.3 — User-configurable trash settings.
 *
 * Persisted in SharedPreferences because:
 *   - settings are simple (1 int + 1 bool).
 *   - no relational data; Room would be overkill.
 *   - reads happen on every Worker invocation so we want a synchronous store.
 */
object TrashConfig {

    private const val PREFS = "elysium_trash_config"
    private const val KEY_RETENTION_DAYS = "retention_days"
    private const val KEY_AUTO_PURGE_ENABLED = "auto_purge_enabled"

    const val DEFAULT_RETENTION_DAYS = 30
    val ALLOWED_RETENTION_DAYS = listOf(7, 14, 30, 90, 365)

    fun retentionDays(context: Context): Int {
        return prefs(context).getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
    }

    fun setRetentionDays(context: Context, days: Int) {
        require(days in ALLOWED_RETENTION_DAYS) { "Invalid retention: $days" }
        prefs(context).edit().putInt(KEY_RETENTION_DAYS, days).apply()
    }

    fun isAutoPurgeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_PURGE_ENABLED, true)
    }

    fun setAutoPurgeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_PURGE_ENABLED, enabled).apply()
        if (enabled) TrashAutoPurgeWorker.schedule(context)
        else TrashAutoPurgeWorker.cancel(context)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
