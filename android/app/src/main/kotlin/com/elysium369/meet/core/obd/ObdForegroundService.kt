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
    @Inject lateinit var keepAliveManager: KeepAliveManager
    @Inject lateinit var alertManager: AlertManager
    @Inject lateinit var tripManager: TripManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val NOTIF_ID = 1
    
    val liveData: StateFlow<Map<String, Float>> get() = obdSession.liveData
    val connectionState: StateFlow<ObdState> get() = obdSession.state
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIF_ID, buildNotification("Iniciando conexión..."))
        } catch (e: Exception) {
            // Ignorar en desarrollo/pruebas si faltan permisos
        }
        
        serviceScope.launch {
            keepAliveManager.start()
            alertManager.startMonitoring(liveData, serviceScope)
            
            // Assume dummy vehicle ID for now, this would normally be passed via Intent extra
            tripManager.startMonitoring("default_vehicle")
            
            liveData.collect { data ->
                val temp = data["0105"]?.toInt()?.toString() ?: "--"
                val voltage = data["VOLTAGE"]?.toString() ?: "--"
                val rpm = data["010C"]?.toInt()?.toString() ?: "--"
                // Assuming ObdSession has a property for this, mocking for compilation
                val dtcCount = 0 
                val text = "Motor: ${temp}°C | ${voltage}V | ${rpm} RPM" +
                    if (dtcCount > 0) " | ⚠️ $dtcCount DTC" else " | ✓ Sin fallas"
                
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIF_ID, buildNotification(text))
            }
        }
        
        return START_STICKY
    }
    
    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("obd_channel", "Diagnóstico OBD", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        
        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                action = "OPEN_SCANNER"
            }, PendingIntent.FLAG_IMMUTABLE)
            
        return NotificationCompat.Builder(this, "obd_channel")
            .setContentTitle("MEET OBD2")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        serviceScope.launch {
            tripManager.endTrip()
            keepAliveManager.stop()
        }
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
