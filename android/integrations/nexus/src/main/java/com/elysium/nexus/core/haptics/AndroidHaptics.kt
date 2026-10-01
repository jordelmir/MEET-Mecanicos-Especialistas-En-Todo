package com.elysium.nexus.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission

/**
 * The Android implementation of [Haptics].
 *
 * `MASTER_ORDER.md` §27 says the project shall
 * support local haptics: button, stick limit,
 * trigger click, error, connection, profile
 * change, recenter. The Android adapter maps
 * each [HapticEvent] to a `VibrationEffect` (or
 * the legacy `Vibrator.vibrate(long)` API on
 * pre-Android 26).
 *
 * ## Why `VibrationEffect` over `Vibrate(long)`
 *
 * `VibrationEffect` is the modern API (Android
 * 26+). It allows specifying amplitude (the
 * "strength" of the vibration) and shape (a
 * predefined waveform). The legacy
 * `Vibrator.vibrate(long)` API only takes a
 * duration, which is what the `VibrationEffect`
 * defaults to when amplitude is not specified.
 *
 * `VibrationEffect.createOneShot(durationMs,
 * DEFAULT_AMPLITUDE)` is the standard "single
 * buzz" call. For stronger / softer events, the
 * adapter scales the amplitude (e.g. 50% for
 * "button tap", 100% for "error").
 */
class AndroidHaptics(
    context: Context
) : Haptics {

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    override fun fire(event: HapticEvent) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val (durationMs, amplitude) = mapEvent(event)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                durationMs,
                amplitude
            )
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    private fun mapEvent(event: HapticEvent): Pair<Long, Int> = when (event) {
        HapticEvent.ButtonTap -> 20L to 128 // 20ms, 50% amplitude
        HapticEvent.ButtonLongPress -> 40L to 192 // 40ms, 75% amplitude
        HapticEvent.StickEdge -> 15L to 96 // short, light
        HapticEvent.TriggerClick -> 25L to 192 // trigger click is louder
        HapticEvent.Error -> 100L to 255 // error is the loudest, longest
        HapticEvent.TransportConnected -> 30L to 160
        HapticEvent.TransportDisconnected -> 30L to 160
        HapticEvent.ProfileChanged -> 25L to 160
        HapticEvent.Recentered -> 35L to 192
    }
}
