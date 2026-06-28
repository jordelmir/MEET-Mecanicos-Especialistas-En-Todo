package com.elysium369.meet.core.copilot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.elysium369.meet.core.alerts.AlertSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val CHANNEL_ID = "meet_copilot_alerts"
    private val NOTIFICATION_ID = 4099

    init {
        createNotificationChannel()
    }

    fun startListeningToEvents() {
        if (job != null) return
        Log.i("NotificationService", "Starting Elysium Vanguard Copilot NotificationService...")
        job = scope.launch {
            eventBus.events.collect { event ->
                showNotification(event)
            }
        }
    }

    fun stopListeningToEvents() {
        job?.cancel()
        job = null
        Log.i("NotificationService", "Stopped Elysium Vanguard Copilot NotificationService.")
    }

    private fun showNotification(event: CopilotEvent) {
        try {
            val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            val lang = prefs.getString("app_language", "es") ?: "es"
            
            val title = when (event.severity) {
                AlertSeverity.CRITICAL -> if (lang == "en") "CRITICAL SAFETY ALERT" else "ALERTA DE SEGURIDAD CRÍTICA"
                AlertSeverity.WARNING -> if (lang == "en") "PREVENTIVE WARNING" else "ADVERTENCIA PREVENTIVA"
                else -> if (lang == "en") "VEHICLE STATUS INFO" else "INFORMACIÓN DEL VEHÍCULO"
            }
            
            val message = if (lang == "en") event.messageEn else event.messageEs

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning) // fallback system icon
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(
                    if (event.severity == AlertSeverity.CRITICAL) NotificationCompat.PRIORITY_HIGH 
                    else NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(true)

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e("NotificationService", "Failed to display alert notification", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Elysium Vanguard Copilot Alerts"
            val descriptionText = "Notifications for vehicle diagnostics and safety alerts"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
