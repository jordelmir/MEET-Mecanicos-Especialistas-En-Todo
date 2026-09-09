package com.elysium369.meet.ride.domain

import android.content.Context
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import com.elysium369.meet.data.local.dao.RideDao
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
    private val rideDao: RideDao,
    private val currentUserId: () -> String?,
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
        scope.launch(Dispatchers.IO) {
            val actor = currentUserId()
            val ride = rideDao.getRequestById(rideId)
            if (actor == null || ride?.passengerId != actor ||
                ride.serverState != "COMPLETED" || ride.currency != currency ||
                !RideTipPolicy.isValid(tipMinor, currency)
            ) {
                noticeEmitter("La propina requiere un viaje completado de tu cuenta y un importe válido.")
                return@launch
            }
            noticeEmitter("Registrando propina…")
            try {
                val response = SupabaseModule.client.postgrest.rpc(
                    function = "ride_submit_tip_v1",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("p_ride_id", JsonPrimitive(rideId))
                        put("p_tip_minor", JsonPrimitive(tipMinor))
                        put("p_currency", JsonPrimitive(currency))
                    },
                )
                val receipt = kotlinx.serialization.json.Json.parseToJsonElement(response.data).jsonObject
                check(receipt["ok"]?.jsonPrimitive?.booleanOrNull == true)
                check(receipt["tip_minor"]?.jsonPrimitive?.content?.toLongOrNull() == tipMinor)
                check(receipt["currency"]?.jsonPrimitive?.content == currency)
                rideDao.recordConfirmedTip(rideId, actor, tipMinor)
                if (currentUserId() == actor) {
                    noticeEmitter("Propina registrada: ${RideTipPolicy.display(tipMinor, currency)} $currency. Pago pendiente de confirmación.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (currentUserId() == actor) {
                    noticeEmitter("No se confirmó la propina. Puedes reintentar sin duplicarla.")
                }
            }
        }
    }
}
