package com.elysium369.meet.ride.domain

import android.content.Context
import android.content.SharedPreferences
import com.elysium369.meet.BuildConfig
import java.security.MessageDigest
import java.time.Instant

/**
 * Dev/QA bypass wrapper for RidePresenceChallenge.
 * ONLY active when BuildConfig.DEBUG == true.
 * Allows automated flow testing without requiring a real face blink.
 */
object RidePresenceBypassWrapper {

    private const val PREFS_NAME = "ride_presence_prefs"
    private const val KEY_BYPASS_ENABLED = "flow_test_bypass"
    private const val PRESENCE_PREFS = "elysium_ride_driver_presence"

    fun isBypassAvailable(): Boolean = BuildConfig.DEBUG

    fun enableBypass(context: Context) {
        if (!isBypassAvailable()) return
        getPrefs(context).edit()
            .putBoolean(KEY_BYPASS_ENABLED, true)
            .apply()
    }

    fun disableBypass(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_BYPASS_ENABLED, false)
            .apply()
    }

    fun isBypassActive(context: Context): Boolean {
        if (!isBypassAvailable()) return false
        return getPrefs(context).getBoolean(KEY_BYPASS_ENABLED, false)
    }

    /**
     * Stamps the presence verification in the ride driver presence prefs
     * so that RideDriverPresencePolicy.requiresChallenge() returns false for 12 hours.
     */
    fun stampPresenceVerification(context: Context, ownerId: String) {
        if (!isBypassAvailable()) return
        val presenceKey = "last_verified_at:$ownerId"
        context.getSharedPreferences(PRESENCE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(presenceKey, System.currentTimeMillis())
            .apply()
    }

    fun completeChallenge(): String {
        val syntheticData = buildString {
            append("flow_test_bypass:")
            append(Instant.now().toEpochMilli())
            append(":")
            append(System.nanoTime())
            append(":")
            append("face_count=1;tracking_id=0;yaw=0.0;pitch=0.0;left_eye_open=0.95;right_eye_open=0.95")
        }
        return sha256(syntheticData)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
