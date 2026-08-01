package com.elysium369.meet.ride.notification

import android.Manifest
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
import androidx.core.content.edit
import com.elysium369.meet.MainActivity

class RideNotificationCoordinator(private val context: Context) {
    private val preferences = context.getSharedPreferences(
        "elysium_ride_notifications",
        Context.MODE_PRIVATE,
    )

    init {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Elysium Viajes",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Asignaciones y avisos operativos de viajes"
                enableVibration(true)
            },
        )
    }

    fun notifyIdleDriver(nowEpochMs: Long = System.currentTimeMillis()) {
        val last = preferences.getLong(KEY_IDLE_LAST, 0L)
        if (nowEpochMs - last < IDLE_COOLDOWN_MS) return
        if (notify(
                id = 7_001,
                title = "Sigues disponible",
                body = "No tienes un viaje activo. Elysium seguirá buscando solicitudes cercanas.",
            )
        ) {
            preferences.edit { putLong(KEY_IDLE_LAST, nowEpochMs) }
        }
    }

    fun notifyDestinationEtaSevenMinutes(tripId: String, etaSeconds: Long) {
        if (etaSeconds !in 360L..480L) return
        val key = "eta7:$tripId"
        if (preferences.getBoolean(key, false)) return
        if (notify(
                id = 7_100 + tripId.hashCode().and(0x0FFF),
                title = "Destino a unos 7 minutos",
                body = "Prepárate para finalizar. Este aviso corresponde al destino final, no a una parada.",
            )
        ) {
            preferences.edit { putBoolean(key, true) }
        }
    }

    private fun notify(id: Int, title: String, body: String): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    private companion object {
        const val CHANNEL_ID = "elysium_rides_operations"
        const val KEY_IDLE_LAST = "idle_driver_last"
        const val IDLE_COOLDOWN_MS = 30 * 60 * 1000L
    }
}
