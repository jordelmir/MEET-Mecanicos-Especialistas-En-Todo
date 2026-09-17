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

class RideNotificationCoordinator(private val context: Context, private val ownerUserId: String?) {
    private val preferences = context.getSharedPreferences(
        "elysium_ride_notifications_${ownerUserId ?: "signed_out"}",
        Context.MODE_PRIVATE,
    )

    init {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Elysium Viajes",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Asignaciones y avisos operativos de viajes"
                enableVibration(true)
            },
        )
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                DISPATCH_CHANNEL_ID,
                "MEET Despacho y Alertas Prioritarias",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notificaciones urgentes de solicitudes, ofertas y viajes en curso"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
        )
    }

    fun notifyIncomingRideDispatch(tripId: String, pickupAddress: String, destAddress: String, priceCrc: Double) {
        val title = "🚨 NUEVO VIAJE: ₡${priceCrc.toInt()} CRC"
        val body = "Recogida: $pickupAddress\nDestino: $destAddress"
        notify(
            id = 7_200 + (tripId.hashCode() and 0x3FFF_FFFF),
            title = title,
            body = body,
            channelId = DISPATCH_CHANNEL_ID,
            category = NotificationCompat.CATEGORY_CALL,
        )
    }

    fun notifyOfferAccepted(tripId: String, passengerName: String, priceCrc: Double) {
        val title = "🎉 ¡OFERTA ACEPTADA! 🚕"
        val body = "$passengerName aceptó tu tarifa de ₡${priceCrc.toInt()} CRC. Abre MEET para iniciar la ruta."
        notify(
            id = 7_300 + (tripId.hashCode() and 0x3FFF_FFFF),
            title = title,
            body = body,
            channelId = DISPATCH_CHANNEL_ID,
            category = NotificationCompat.CATEGORY_EVENT,
        )
    }

    fun notifyNewChatMessage(tripId: String, senderName: String, messageText: String) {
        val title = "💬 Mensaje de $senderName"
        notify(
            id = 7_400 + (tripId.hashCode() and 0x3FFF_FFFF),
            title = title,
            body = messageText,
            channelId = DISPATCH_CHANNEL_ID,
            category = NotificationCompat.CATEGORY_MESSAGE,
        )
    }

    fun notifyDriverArrived(tripId: String, pickupAddress: String) {
        val key = "arrived:$tripId"
        if (ownerUserId.isNullOrBlank()) return
        if (preferences.getBoolean(key, false)) return
        if (notify(
                id = 7_500 + (tripId.hashCode() and 0x3FFF_FFFF),
                title = "📍 ¡Tu chofer ha llegado!",
                body = "Tu conductor está esperando en: $pickupAddress. Comparte el PIN de abordaje únicamente con él.",
                channelId = DISPATCH_CHANNEL_ID,
                category = NotificationCompat.CATEGORY_EVENT,
            )
        ) {
            preferences.edit { putBoolean(key, true) }
        }
    }

    fun notifyTripCompleted(tripId: String, totalFare: String) {
        val key = "completed:$tripId"
        if (ownerUserId.isNullOrBlank()) return
        if (preferences.getBoolean(key, false)) return
        if (notify(
                id = 7_600 + (tripId.hashCode() and 0x3FFF_FFFF),
                title = "🎉 ¡Viaje completado!",
                body = "Has llegado a tu destino. Total: $totalFare. Califica el servicio desde la app.",
                channelId = CHANNEL_ID,
                category = NotificationCompat.CATEGORY_STATUS,
            )
        ) {
            preferences.edit { putBoolean(key, true) }
        }
    }

    fun notifyTripCancelled(tripId: String, cancelledBy: String, reason: String) {
        val key = "cancelled:$tripId"
        if (ownerUserId.isNullOrBlank()) return
        if (preferences.getBoolean(key, false)) return
        if (notify(
                id = 7_700 + (tripId.hashCode() and 0x3FFF_FFFF),
                title = "❌ Viaje cancelado",
                body = "Cancelado por $cancelledBy${if (reason.isNotBlank()) ": $reason" else ""}. Puedes solicitar un nuevo viaje.",
                channelId = DISPATCH_CHANNEL_ID,
                category = NotificationCompat.CATEGORY_EVENT,
            )
        ) {
            preferences.edit { putBoolean(key, true) }
        }
    }

    fun notifyIdleDriver(nowEpochMs: Long = System.currentTimeMillis()) {
        if (ownerUserId.isNullOrBlank()) return
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
        if (ownerUserId.isNullOrBlank()) return
        if (etaSeconds !in 360L..480L) return
        val key = "eta7:$tripId"
        if (preferences.getBoolean(key, false)) return
        if (notify(
                // Keep the complete 31-bit hash space; a 12-bit mask caused
                // unrelated trips to replace each other's notifications.
                id = 7_100 + (tripId.hashCode() and 0x3FFF_FFFF),
                title = "Destino a unos 7 minutos",
                body = "Prepárate para finalizar. Este aviso corresponde al destino final, no a una parada.",
            )
        ) {
            preferences.edit { putBoolean(key, true) }
        }
    }

    private fun notify(
        id: Int,
        title: String,
        body: String,
        channelId: String = CHANNEL_ID,
        category: String? = null,
    ): Boolean {
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
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (category != null) {
            builder.setCategory(category)
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
        return true
    }

    private companion object {
        const val CHANNEL_ID = "elysium_rides_operations"
        const val DISPATCH_CHANNEL_ID = "elysium_rides_dispatch_high_priority"
        const val KEY_IDLE_LAST = "idle_driver_last"
        const val IDLE_COOLDOWN_MS = 30 * 60 * 1000L
    }
}
