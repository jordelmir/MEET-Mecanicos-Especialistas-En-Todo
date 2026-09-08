package com.elysium369.meet.ride.domain

import com.elysium369.meet.data.local.entities.DriverVerificationEntity
import com.elysium369.meet.data.local.entities.PassengerVerificationEntity
import com.elysium369.meet.ride.data.remote.TrustVerificationApplication
import java.time.Instant

/** Remote review is authority; unavailable profile fields stay explicitly absent. */
object RideDriverSessionRestoration {
    fun modeKey(actorId: String?): String? = actorId?.takeIf { it.isNotBlank() }
        ?.let { "ride_driver_mode:$it" }

    fun restore(
        actorId: String,
        application: TrustVerificationApplication,
        existing: DriverVerificationEntity?,
    ): DriverVerificationEntity? {
        if (actorId.isBlank() || application.applicantUserId != actorId ||
            application.serviceType != "RIDE_DRIVER" ||
            (existing != null && existing.driverId != actorId) ||
            application.status !in setOf("PENDING", "APPROVED", "REJECTED", "SUSPENDED", "REVOKED")
        ) return null
        fun timestamp(value: String?): Long? = value?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        }
        val submittedAt = timestamp(application.submittedAt) ?: return null
        val reviewedAt = timestamp(application.reviewedAt)
        val base = existing ?: DriverVerificationEntity(
            driverId = actorId,
            fullName = application.displayName,
            phone = application.phone.orEmpty(),
            email = application.applicantEmail.orEmpty(),
            dateOfBirth = "",
            vehicleMake = "", vehicleModel = "", vehicleYear = 0,
            vehicleColor = "", vehiclePlate = application.licenseReference.orEmpty(),
            vehicleSeats = 0,
            pathLicenciaFront = "", pathLicenciaBack = "",
            pathCedulaFront = "", pathCedulaBack = "", pathHojaDelincuencia = "",
            pathMarchamo = "", pathDekra = "", pathSeguro = "",
            pathSelfieProfile = "", pathSelfieWithCedula = "", pathSelfieWithLicencia = "",
            pathVehicleFront = "", pathVehicleBack = "", pathVehicleInterior = "",
            status = application.status, createdAt = submittedAt,
        )
        return base.copy(
            status = application.status,
            approvedAt = reviewedAt.takeIf { application.status == "APPROVED" },
            rejectionReason = application.decisionReason,
            updatedAt = reviewedAt ?: submittedAt,
        )
    }

    fun restorePassenger(
        actorId: String,
        application: TrustVerificationApplication,
        existing: PassengerVerificationEntity?,
    ): PassengerVerificationEntity? {
        if (actorId.isBlank() || application.applicantUserId != actorId ||
            application.serviceType != "PASSENGER" || application.profileReference != "primary" ||
            application.status !in setOf("PENDING", "APPROVED", "REJECTED", "SUSPENDED")
        ) return null
        val submittedAt = runCatching { Instant.parse(application.submittedAt).toEpochMilli() }.getOrNull()
            ?: return null
        val reviewedAt = application.reviewedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        val base = existing ?: PassengerVerificationEntity(
            passengerId = actorId,
            fullName = application.displayName,
            phone = application.phone.orEmpty(),
            pathProfilePhoto = "", pathCedulaFront = "", pathSelfieWithCedula = "",
            status = application.status,
            createdAt = submittedAt,
        )
        return base.copy(
            status = application.status,
            rejectionReason = application.decisionReason,
            approvedAt = reviewedAt.takeIf { application.status == "APPROVED" },
        )
    }
}
