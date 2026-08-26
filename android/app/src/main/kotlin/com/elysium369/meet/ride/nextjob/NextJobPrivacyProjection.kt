package com.elysium369.meet.ride.nextjob

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Passenger-facing projection for chained dispatch.
 * STRICT PRIVACY CONTRACT: Contains zero details of previous passenger's location, route, or identity.
 */
@Serializable
data class NextJobPrivacyProjection(
    @SerialName("is_chained_service") val isChainedService: Boolean = false,
    @SerialName("status") val statusRaw: String = "RESERVED",
    @SerialName("available_in_seconds") val availableInSeconds: Int = 0,
    @SerialName("pickup_eta_seconds") val pickupEtaSeconds: Int = 0,
    @SerialName("notice_es") val noticeEs: String = "El conductor está finalizando otro servicio cercano",
) {
    val status: NextJobStatus get() = NextJobStatus.fromString(statusRaw)

    val availableInMinutes: Int get() = ((availableInSeconds + 59) / 60).coerceAtLeast(1)
    val pickupEtaMinutes: Int get() = ((pickupEtaSeconds + 59) / 60).coerceAtLeast(1)

    val formattedPassengerBanner: String
        get() {
            return if (isChainedService && status == NextJobStatus.RESERVED) {
                "Conductor finalizando servicio anterior · Disponible en ~$availableInMinutes min (Llegada: ~$pickupEtaMinutes min)"
            } else {
                ""
            }
        }
}
