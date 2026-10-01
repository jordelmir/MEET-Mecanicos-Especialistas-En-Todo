package com.elysium.vanguard.recordshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import com.elysium.vanguard.recordshield.service.UploadWorker

/**
 * ============================================================================
 * BootReceiver — Restart Upload Workers After Device Reboot
 * ============================================================================
 *
 * Why: If the device reboots mid-recording, pending chunks are still in
 * the Room database. This receiver schedules the periodic upload worker
 * on boot to ensure those chunks eventually reach the cloud.
 * ============================================================================
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device rebooted — scheduling evidence upload sweep")
            UploadWorker.schedulePeriodicUpload(context)
            UploadWorker.enqueueImmediate(context)
        }
    }
}
