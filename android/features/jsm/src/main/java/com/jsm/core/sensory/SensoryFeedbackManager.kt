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
class SensoryFeedbackManager(context: Context) {

    private val vibratorManager: VibratorManager =
        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

    fun triggerPairingSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Onda física: Doble micro-clic ascendente para "Sincronizado"
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1.0f, 50)
                .compose()

            vibratorManager.vibrate(CombinedVibration.createParallel(effect))
        }
    }

    fun triggerDataChannelReceived() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Golpe muy sutil (Tactile feedback) simulando recepción de paquete
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f)
                .compose()

            vibratorManager.vibrate(CombinedVibration.createParallel(effect))
        }
    }
}
