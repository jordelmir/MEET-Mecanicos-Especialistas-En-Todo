package com.elysium369.meet.core.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.elysium369.meet.MainActivity
import com.elysium369.meet.core.alerts.AlertManager
import com.elysium369.meet.core.trips.TripManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@AndroidEntryPoint
class ObdForegroundService : Service() {

    @Inject lateinit var obdSession: ObdSession
    @Inject lateinit var alertManager: AlertManager
    @Inject lateinit var tripManager: TripManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val NOTIF_ID = 1
    private val CHANNEL_ID = "obd_channel"
    
    val liveData: StateFlow<Map<String, Float>> get() = obdSession.liveData
    val connectionState: StateFlow<ObdState> get() = obdSession.state
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vehicleId = intent?.getStringExtra("vehicle_id") ?: "unknown_vehicle"
        
        // Crear canal ANTES de startForeground — requerido en Android 8+
        createNotificationChannel()
        
        try {
            startForeground(NOTIF_ID, buildNotification("Iniciando conexión..."))
        } catch (e: Exception) {
            // Ignorar en desarrollo/pruebas si faltan permisos
        }
        
        serviceScope.launch {
            alertManager.startMonitoring(liveData, serviceScope)
            
            val sessionId = java.util.UUID.randomUUID().toString()
            tripManager.startMonitoring(vehicleId, sessionId)
            
            var lastUpdate = 0L
            liveData.collect { data ->
                if (data.isEmpty()) return@collect
                
                val now = System.currentTimeMillis()
                // Throttling: solo actualizar notificación cada 3 segundos para ahorrar batería
                // A MENOS que haya una alerta crítica de AlertManager
                val isCritical = data["0105"]?.let { it > 115f } ?: false // Sobrecalentamiento > 115C
                
                if (now - lastUpdate > 3000 || isCritical) {
                    val temp = data["0105"]?.toInt()?.toString() ?: "--"
                    val rpm = data["010C"]?.toInt()?.toString() ?: "--"
                    val speed = data["010D"]?.toInt()?.toString() ?: "--"
                    val alertText = if (isCritical) "⚠️ ¡SOBRECALENTAMIENTO! " else ""
                    val text = "${alertText}Motor: ${temp}°C | ${rpm} RPM | ${speed} km/h"
                    
                    try {
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager?.notify(NOTIF_ID, buildNotification(text))
                        lastUpdate = now
                    } catch (_: Exception) {}
                }
            }
        }
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Diagnóstico OBD", 
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Estado de la conexión OBD2"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                action = "OPEN_SCANNER"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MEET OBD2")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    override fun onDestroy() {
        // Ejecutar cleanup ANTES de cancelar el scope
        // (la versión anterior lanzaba una coroutine y luego cancelaba el scope
        // inmediatamente, lo cual hacía que el cleanup nunca se ejecutara)
        runBlocking(Dispatchers.IO) {
            try {
                tripManager.endTrip()
            } catch (_: Exception) {}
            // KeepAlive se detiene con obdSession.disconnect()
        }
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
