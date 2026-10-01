package com.jsm.core.sensory

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * Gestor Háptico de Primera Categoría para Android.
 * Evita la vibración barata genérica, dictaminando patrones de micro-clics y confirmaciones premium.
 */
class SensoryFeedbackManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun triggerPairingSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            triggerPairingSuccessS()
        } else {
            triggerLegacyFeedback()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun triggerPairingSuccessS() {
        val manager = appContext.getSystemService(VibratorManager::class.java)

        val effect = VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                0.5f,
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                1f,
                50,
            )
            .compose()

        manager?.vibrate(
            CombinedVibration.createParallel(effect)
        )
    }

    fun triggerDataChannelReceived() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            triggerDataChannelReceivedS()
        } else {
            triggerLegacyFeedback()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun triggerDataChannelReceivedS() {
        val manager = appContext.getSystemService(VibratorManager::class.java)

        val effect = VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                0.3f,
            )
            .compose()

        manager?.vibrate(CombinedVibration.createParallel(effect))
    }

    @Suppress("DEPRECATION")
    private fun triggerLegacyFeedback() {
        val vibrator =
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

        vibrator?.vibrate(
            VibrationEffect.createOneShot(
                35L,
                VibrationEffect.DEFAULT_AMPLITUDE,
            )
        )
    }
}
