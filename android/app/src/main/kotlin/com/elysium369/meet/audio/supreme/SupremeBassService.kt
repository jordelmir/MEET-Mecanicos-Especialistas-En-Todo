package com.elysium369.meet.audio.supreme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SupremeBassState(val active: Boolean = false, val connected: Boolean = false, val message: String = "Desactivado")

object SupremeBassController {
    private val mutableState = MutableStateFlow(SupremeBassState())
    val state = mutableState.asStateFlow()
    internal fun publish(value: SupremeBassState) { mutableState.value = value }
    fun percentage(context: Context) = SupremeBoostPolicy.clamp(context.getSharedPreferences("supreme_bass_integrated", Context.MODE_PRIVATE).getInt("percentage", 100))
    fun save(context: Context, percent: Int) { context.getSharedPreferences("supreme_bass_integrated", Context.MODE_PRIVATE).edit().putInt("percentage", SupremeBoostPolicy.clamp(percent)).apply() }
    fun start(context: Context, percent: Int) {
        save(context, percent)
        runCatching {
            val intent = Intent(context, SupremeBassService::class.java).putExtra("percentage", SupremeBoostPolicy.clamp(percent))
            if (mutableState.value.active) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
        }.onFailure { publish(SupremeBassState(message = "No se pudo iniciar el efecto: ${it.javaClass.simpleName}")) }
    }
    fun stop(context: Context) { context.stopService(Intent(context, SupremeBassService::class.java)); publish(SupremeBassState()) }
}

class SupremeBassService : Service() {
    private var engine: SupremeAudioEngine? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("supreme_bass", "SupremeBass Neon", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val stop = PendingIntent.getService(this, 901, Intent(this, SupremeBassService::class.java).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "supreme_bass")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("SupremeBass Neon")
            .setContentText("Efecto de audio solicitado · toca Detener para apagar")
            .setOngoing(true).addAction(Notification.Action.Builder(null, "Detener", stop).build()).build()
        val foreground = runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(901, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(901, notification)
        }.isSuccess
        if (!foreground) {
            SupremeBassController.publish(SupremeBassState(message = "Android no permitió iniciar el servicio de audio"))
            stopSelf()
            return START_NOT_STICKY
        }
        if (engine == null) engine = SupremeAudioEngine(this) { message, connected ->
            SupremeBassController.publish(SupremeBassState(active = true, connected = connected, message = message))
        }
        engine?.start(intent.getIntExtra("percentage", 100))
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() { engine?.stop(); engine = null; SupremeBassController.publish(SupremeBassState()); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
