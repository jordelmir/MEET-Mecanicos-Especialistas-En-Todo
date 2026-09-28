package com.jsm.core.foldable

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Motor inyector del comportamiento físico del Honor Magic V2.
 * Rastrea silenciosamente si la bisagra (Hinge) está abierta o cerrada, propagando este estado.
 */
class FoldStateListener(private val activity: Activity) {

    private val windowInfoTracker = WindowInfoTracker.getOrCreate(activity)

    enum class Posture {
        FOLDED,     // Modo teléfono cerrado convencional
        HALF_OPENED,// Modo laptop/tienda
        UNFOLDED,   // Pantalla Tablet 100% abierta
        UNKNOWN
    }

    /**
     * Devuelve un Flow reactivo para consumir el estado global del hardware.
     */
    fun monitorDevicePosture(): Flow<Posture> {
        return windowInfoTracker.windowLayoutInfo(activity)
            .map { layoutInfo ->
                val foldFeature = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()

                when {
                    foldFeature == null -> Posture.UNKNOWN
                    foldFeature.isSeparating -> Posture.HALF_OPENED
                    foldFeature.state == FoldingFeature.State.FLAT -> Posture.UNFOLDED
                    else -> Posture.FOLDED
                }
            }
    }

    // El resultado de "monitorDevicePosture().collect { }" se deberá pasar
    // al RTCDataChannel y serializar con nuestro DisplayState.proto
}
