package com.elysium369.meet.core.geo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.elysium369.meet.MainActivity

/**
 * ══════════════════════════════════════════════════════════════════════
 *  A U R A   S E N T I N E L   N O T I F I C A T I O N   E N G I N E
 *  ──────────────────────────────────────────────────────────────
 *  Heads-Up, tactical proximity & safety alerts:
 *  - Dispatches high-priority tactile alerts when ETA <= 3 min.
 *  - Emits Aura Sentinel Boarding/Service PIN to lockscreen.
 *  - Dispatches discrete SOS Beacon activations.
 * ══════════════════════════════════════════════════════════════════════
 */
object AuraSentinelNotificationCoordinator {

    const val PROXIMITY_CHANNEL_ID = "aura_sentinel_proximity"
    const val EMERGENCY_CHANNEL_ID = "aura_sentinel_emergency"

    fun initChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val proximityChannel = NotificationChannel(
            PROXIMITY_CHANNEL_ID,
            "Aura Sentinel — Proximidad y Encuentro Seguro",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas tácticas cuando el especialista o grúa está a menos de 3 minutos."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 450)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val emergencyChannel = NotificationChannel(
            EMERGENCY_CHANNEL_ID,
            "Aura Sentinel — Baliza de Emergencia SOS",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Canal crítico para alertas de asistencia y refugios seguros."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(proximityChannel)
        manager.createNotificationChannel(emergencyChannel)
    }

    fun notifyArrivalImminent(
        context: Context,
        requestId: String,
        securityPin: String,
        etaMinutes: Int
    ) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "elysium_services")
            putExtra("requestId", requestId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚡ AURA SENTINEL: Llegada Inminente (~$etaMinutes min)"
        val body = "El especialista está por llegar al punto. Tu PIN de seguridad es: $securityPin. No intervengas sin validar identidad."

        val builder = NotificationCompat.Builder(context, PROXIMITY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                9_100 + (requestId.hashCode() and 0x0FFF_FFFF),
                builder.build()
            )
        }
    }

    fun notifyEmergencyBeaconActivated(
        context: Context,
        beaconId: String,
        locationLabel: String
    ) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "elysium_services")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            beaconId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "🛡️ Baliza de Seguridad Aura Sentinel Activa"
        val body = "Registro seguro y anónimo en: $locationLabel. Rutas a estaciones de auxilio disponibles en el radar."

        val builder = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                9_900 + (beaconId.hashCode() and 0x0FFF_FFFF),
                builder.build()
            )
        }
    }
}
