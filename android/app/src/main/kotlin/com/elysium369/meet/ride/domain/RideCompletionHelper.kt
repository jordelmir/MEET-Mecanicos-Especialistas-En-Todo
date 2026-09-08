package com.elysium369.meet.ride.domain

import android.content.Context
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * RideCompletionHelper — extracted from ObdViewModel ride section.
 * Handles ride completion operations: tip submission, GPS forensic trail export.
 *
 * Strangler migration: this is the first extraction. ObdViewModel delegates to this class.
 * Each function preserves exact behavior — SharedFlow emission via [noticeEmitter].
 */
class RideCompletionHelper(
    private val scope: CoroutineScope,
    private val context: Context,
    private val noticeEmitter: suspend (String) -> Unit,
) {
    fun exportGpsForensicTrail(rideId: String) {
        scope.launch {
            noticeEmitter("Generando reporte GPS forense…")
            val trail = com.elysium369.meet.ride.location.GpsTrailRecorder.loadTrail(context, rideId)
            if (trail == null || trail.points.isEmpty()) {
                noticeEmitter("No hay datos GPS registrados para este viaje.")
                return@launch
            }
            val result = com.elysium369.meet.ride.location.GpsTrailPdfExporter.exportPdf(context, trail)
            if (result != null) {
                com.elysium369.meet.ride.location.GpsTrailPdfExporter.sharePdf(context, result)
                noticeEmitter(
                    "Reporte GPS forense generado: ${result.pointCount} puntos, " +
                        "hash ${result.integrityHash.take(16)}…",
                )
            } else {
                noticeEmitter("Error al generar el reporte GPS.")
            }
        }
    }

    fun submitTip(rideId: String, tipMinor: Long, currency: String) {
        scope.launch {
            noticeEmitter("Enviando propina…")
            runCatching {
                SupabaseModule.client.postgrest.rpc(
                    function = "ride_submit_tip_v1",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("p_ride_id", JsonPrimitive(rideId))
                        put("p_tip_minor", JsonPrimitive(tipMinor))
                        put("p_currency", JsonPrimitive(currency))
                    },
                )
            }.onSuccess {
                noticeEmitter("Propina de $tipMinor $currency enviada. ¡Gracias!")
            }.onFailure {
                noticeEmitter("Error al enviar propina: ${it.message?.take(80)}")
            }
        }
    }
}
