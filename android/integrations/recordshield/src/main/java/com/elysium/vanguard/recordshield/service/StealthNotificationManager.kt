package com.elysium.vanguard.recordshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import androidx.core.app.NotificationCompat
import com.elysium.vanguard.recordshield.R
import com.elysium.vanguard.recordshield.RecordShieldApplication

/**
 * ============================================================================
 * StealthNotificationManager — Invisible Foreground Service Notifications
 * ============================================================================
 *
 * WHY STEALTH:
 *   Android REQUIRES a foreground service notification for camera+mic access.
 *   However, we can minimize its visibility:
 *
 *   1. IMPORTANCE_MIN: No sound, no vibration, no heads-up popup
 *   2. Empty/minimal content: Looks like a system process
 *   3. No custom icon: Uses default Android icon
 *   4. Positioned at bottom: Below all other notifications
 *   5. Cannot be swiped (setOngoing): Prevents user from accidentally dismissing
 *
 * Android 14+ Behavior:
 *   - The notification is MANDATORY for foreground service types
 *   - It will always be visible in the notification tray
 *   - But with IMPORTANCE_MIN, it's the LEAST visible possible
 *   - No sound, no vibration, no popup, no lock screen visibility
 *
 * OPSEC Considerations:
 *   - Notification text is generic ("System service")
 *   - No recording-specific information in the notification
 *   - No custom branding that reveals the app's purpose
 *   - If user wants ZERO notification, they must disable it via ADB:
 *     adb shell appops set com.elysium.vanguard.recordshield SYSTEM_ALERT_WINDOW allow
 * ============================================================================
 */
object StealthNotificationManager {

    private const val TAG = "StealthNotification"

    /**
     * Create the ultra-low-importance notification channel.
     * Must be called in Application.onCreate().
     */
    fun createStealthChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val stealthChannel = NotificationChannel(
            RecordShieldApplication.RECORDING_CHANNEL_ID,
            "Grabación Elysium",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Muestra cuando Elysium está grabando audio o video"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Channel 2: Upload — MIN importance
        val uploadChannel = NotificationChannel(
            RecordShieldApplication.UPLOAD_CHANNEL_ID,
            "Subida de grabaciones Elysium",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Muestra el progreso de subida de grabaciones"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(stealthChannel)
        manager.createNotificationChannel(uploadChannel)
    }

    /**
     * Build a stealth foreground service notification.
     *
     * This notification is REQUIRED by Android for camera+mic foreground services.
     * It's designed to be as invisible as possible while still satisfying the OS.
     */
    fun buildStealthNotification(
        context: Context,
        text: String = "Active"
    ): Notification {
        return NotificationCompat.Builder(context, RecordShieldApplication.RECORDING_CHANNEL_ID)
            .setContentTitle("Elysium está grabando")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // Generic system icon
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true) // Cannot be swiped away
            .setLocalOnly(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // No heads-up, no vibrate, no lights
            .build()
    }

    /**
     * Build a minimal notification for the upload worker.
     */
    fun buildUploadNotification(
        context: Context,
        text: String = "Syncing data"
    ): Notification {
        return NotificationCompat.Builder(context, RecordShieldApplication.UPLOAD_CHANNEL_ID)
            .setContentTitle("Elysium está subiendo grabaciones")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setLocalOnly(true)
            .build()
    }

    /**
     * Update the stealth notification text without making it visible.
     * Called from RecordingService to update chunk count.
     */
    fun updateStealthNotification(
        context: Context,
        notificationId: Int,
        text: String
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = buildStealthNotification(context, text)
        manager.notify(notificationId, notification)
    }

    /**
     * Cancel a specific notification.
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)
    }
}
