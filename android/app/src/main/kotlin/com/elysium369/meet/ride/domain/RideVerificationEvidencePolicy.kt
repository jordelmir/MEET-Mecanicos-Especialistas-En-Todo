package com.elysium369.meet.ride.domain

data class VerificationFileEvidence(
    val label: String,
    val path: String,
    val byteCount: Long,
)

data class RideVerificationEvidenceResult(
    val issues: List<String>,
) {
    val isReady: Boolean
        get() = issues.isEmpty()
}

/**
 * Business rules for granting ride access during the local pilot.
 *
 * This layer deliberately validates only evidence presence and basic shape.
 * It does not claim biometric matching, document authenticity, ownership, or
 * government verification. Those remain separate remote/manual review stages.
 */
object RideVerificationEvidencePolicy {
    private const val PASSENGER_REQUIRED_FILE_COUNT = 3
    private const val DRIVER_REQUIRED_FILE_COUNT = 14
    private val isoDatePattern = Regex("""\d{4}-\d{2}-\d{2}""")
    private val simpleEmailPattern =
        Regex("""^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")

    fun evaluatePassenger(
        fullName: String,
        phone: String,
        files: List<VerificationFileEvidence>,
    ): RideVerificationEvidenceResult {
        val issues = mutableListOf<String>()
        validateIdentity(fullName = fullName, phone = phone, issues = issues)
        validateFiles(
            files = files,
            requiredCount = PASSENGER_REQUIRED_FILE_COUNT,
            issues = issues,
        )
        return RideVerificationEvidenceResult(issues.distinct())
    }

    fun evaluateDriver(
        fullName: String,
        phone: String,
        email: String,
        dateOfBirth: String,
        vehicleMake: String,
        vehicleModel: String,
        vehicleYear: Int,
        vehicleColor: String,
        vehiclePlate: String,
        vehicleSeats: Int,
        currentYear: Int,
        files: List<VerificationFileEvidence>,
    ): RideVerificationEvidenceResult {
        val issues = mutableListOf<String>()
        validateIdentity(fullName = fullName, phone = phone, issues = issues)
        if (!simpleEmailPattern.matches(email.trim())) issues += "INVALID_EMAIL"
        if (!isoDatePattern.matches(dateOfBirth.trim())) issues += "INVALID_DATE_OF_BIRTH"
        if (vehicleMake.isBlank()) issues += "MISSING_VEHICLE_MAKE"
        if (vehicleModel.isBlank()) issues += "MISSING_VEHICLE_MODEL"
        if (vehicleColor.isBlank()) issues += "MISSING_VEHICLE_COLOR"
        if (vehiclePlate.isBlank()) issues += "MISSING_VEHICLE_PLATE"
        if (vehicleSeats !in 1..16) issues += "INVALID_VEHICLE_SEATS"
        if (vehicleYear !in 1900..(currentYear + 1)) issues += "INVALID_VEHICLE_YEAR"
        validateFiles(
            files = files,
            requiredCount = DRIVER_REQUIRED_FILE_COUNT,
            issues = issues,
        )
        return RideVerificationEvidenceResult(issues.distinct())
    }

    private fun validateIdentity(
        fullName: String,
        phone: String,
        issues: MutableList<String>,
    ) {
        if (fullName.trim().length < 3) issues += "INVALID_NAME"
        val phoneDigits = phone.filter(Char::isDigit)
        if (phoneDigits.length !in 8..15) issues += "INVALID_PHONE"
    }

    private fun validateFiles(
        files: List<VerificationFileEvidence>,
        requiredCount: Int,
        issues: MutableList<String>,
    ) {
        if (files.size != requiredCount) issues += "REQUIRED_FILE_COUNT:$requiredCount"
        files.forEach { file ->
            when {
                file.path.isBlank() -> issues += "MISSING_FILE:${file.label}"
                file.byteCount <= 0L -> issues += "EMPTY_FILE:${file.label}"
            }
        }
    }
}
