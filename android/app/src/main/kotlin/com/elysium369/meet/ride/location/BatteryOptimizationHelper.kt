package com.elysium369.meet.ride.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {
    /**
     * Checks if the app is currently whitelisted from battery optimization restrictions.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Returns an intent to request the user to exempt MEET from battery optimizations
     * so that foreground GPS tracking and dispatch alerts remain active while driving.
     */
    @SuppressLint("BatteryLife")
    fun createOptimizationExemptionIntent(context: Context): Intent? {
        if (isIgnoringBatteryOptimizations(context)) return null
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (_: Exception) {
            // Fallback to general battery settings if direct request intent is blocked
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
