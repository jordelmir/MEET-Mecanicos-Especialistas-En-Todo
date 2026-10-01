package com.elysium.nexus.fabric.infrared

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * §6 Debug file logger for IR telemetry.
 *
 * MagicOS (Honor) ships encrypted logcat for app processes (`(HKS)` prefixes), so
 * production `Log.d/w/e` from this process is unreadable via `adb logcat`. This file
 * logger writes the same telemetry into the app's sandbox (`files/elysium-ir.log`)
 * which is readable on a debuggable build via:
 *
 *     adb shell run-as com.elysium.nexus.controller cat files/elysium-ir.log
 *
 * Debug-only telemetry: transmit results, carrier, duration, fingerprint decisions.
 * Released builds never write the file (isDebug check is deliberate).
 */
object FileLog {

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var enabled: Boolean = false

    fun initialize(context: Context) {
        try {
            enabled = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val dir = context.filesDir
            if (dir != null) {
                logFile = File(dir, "elysium-ir.log")
                d("BOOT debug=$enabled filesDir=$dir")
            }
        } catch (e: Throwable) {
            enabled = false
        }
    }

    fun d(message: String) {
        if (!enabled) return
        try {
            val file = logFile ?: return
            val line = "${System.currentTimeMillis()} [D] $message\n"
            FileOutputStream(file, true).use { fos ->
                fos.write(line.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // File logging must never crash the transmitter path.
        }
    }

    fun clear() {
        try {
            logFile?.delete()
        } catch (e: Exception) {
            // ignore
        }
    }
}