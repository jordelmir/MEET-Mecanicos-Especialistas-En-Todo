package com.elysium369.meet.core.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquiredAtMs: Long = 0L
    private var telemetryJob: Job? = null
    private var watchdogJob: Job? = null
    
    val liveData: StateFlow<Map<String, Float>> get() = obdSession.liveData
    val connectionState: StateFlow<ObdState> get() = obdSession.state

    /**
     * Check whether the Bluetooth permissions required for
     * FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE are granted at runtime.
     * On SDK < 31 we only need ACCESS_FINE_LOCATION which is always
     * in the "anyOf" bucket, so we return true.
     */
    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val connect = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val scan = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        return connect || scan // "anyOf" — only one is needed
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vehicleId = intent?.getStringExtra("vehicle_id") ?: "unknown_vehicle"
        val adapterAddress = intent?.getStringExtra("adapter_address")
        
        // Crear canal ANTES de startForeground — requerido en Android 8+
        createNotificationChannel()
        acquireWakeLock()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasBluetoothPermissions()) {
                // Full connectedDevice foreground service — requires BT permissions at runtime
                startForeground(
                    NOTIF_ID,
                    buildNotification("Iniciando conexión..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                // Fallback: start as plain foreground service (no special type).
                // This keeps the service alive without crashing on missing permissions.
                startForeground(NOTIF_ID, buildNotification("Iniciando conexión..."))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermissions()) {
                    Log.w("ObdForegroundService",
                        "Started WITHOUT connectedDevice type — Bluetooth permissions not yet granted. " +
                        "Service will function but may be restricted by the OS.")
                }
            }
        } catch (e: SecurityException) {
            // Even with our guard, some OEMs throw on startForeground itself.
            // Fall back to the safest possible call.
            Log.e("ObdForegroundService", "SecurityException on startForeground, retrying without type", e)
            try {
                startForeground(NOTIF_ID, buildNotification("Conexión OBD activa"))
            } catch (e2: Exception) {
                Log.e("ObdForegroundService", "Could not start foreground at all, stopping", e2)
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            Log.e("ObdForegroundService", "Unexpected error on startForeground", e)
            try {
                startForeground(NOTIF_ID, buildNotification("Conexión OBD activa"))
            } catch (_: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        if (obdSession.state.value != ObdState.CONNECTED && !adapterAddress.isNullOrBlank()) {
            serviceScope.launch { obdSession.setTargetAddressSequentially(adapterAddress) }
        }

        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
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

        startContinuityWatchdog(adapterAddress)
        
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val now = System.currentTimeMillis()
            if (wakeLock?.isHeld == true && now - wakeLockAcquiredAtMs < WAKE_LOCK_RENEW_MS) return
            releaseWakeLock()
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ElysiumVanguard:ObdForegroundServiceWakeLock").apply {
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            wakeLockAcquiredAtMs = now
            Log.d("ObdForegroundService", "WakeLock acquired successfully")
        } catch (e: Exception) {
            Log.e("ObdForegroundService", "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("ObdForegroundService", "WakeLock released successfully")
                }
            }
        } catch (e: Exception) {
            Log.e("ObdForegroundService", "Failed to release WakeLock", e)
        } finally {
            wakeLock = null
            wakeLockAcquiredAtMs = 0L
        }
    }

    private fun startContinuityWatchdog(adapterAddress: String?) {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            var reconnectAttempts = 0
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                acquireWakeLock()

                val now = System.currentTimeMillis()
                val state = connectionState.value

                when (state) {
                    ObdState.CONNECTED -> {
                        val silenceMs = obdSession.liveDataSilenceMs(now)
                        if (silenceMs > LIVE_DATA_STALE_MS) {
                            updateNotification("Recuperando telemetría OBD...")
                            val recovered = obdSession.recoverFrozenLink("foreground watchdog: ${silenceMs}ms sin telemetría")
                            if (recovered) {
                                reconnectAttempts = 0
                            }
                        }
                    }
                    ObdState.ERROR, ObdState.DISCONNECTED -> {
                        if (!adapterAddress.isNullOrBlank()) {
                            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(8)
                            val backoffMs = (reconnectAttempts * 2_000L).coerceAtMost(20_000L)
                            updateNotification("Reconectando OBD en ${backoffMs / 1000}s...")
                            delay(backoffMs)
                            runCatching {
                                if (connectionState.value != ObdState.CONNECTED) {
                                    obdSession.setTargetAddressSequentially(adapterAddress)
                                    obdSession.connect()
                                    if (connectionState.value == ObdState.CONNECTED) {
                                        obdSession.startLivePolling()
                                        reconnectAttempts = 0
                                    }
                                }
                            }.onFailure {
                                Log.w("ObdForegroundService", "Watchdog reconnect failed: ${it.message}")
                            }
                        }
                    }
                    ObdState.CONNECTING, ObdState.NEGOTIATING -> Unit
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {}
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
            .setContentTitle("Elysium Vanguard")
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
        }
        telemetryJob?.cancel()
        watchdogJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val LIVE_DATA_STALE_MS = 18_000L
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = 24 * 60 * 1000L
    }
}
