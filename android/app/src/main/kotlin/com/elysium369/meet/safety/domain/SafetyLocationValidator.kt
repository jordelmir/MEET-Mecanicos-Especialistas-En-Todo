package com.elysium369.meet.safety.domain

data class SafetyLocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMs: Long,
    val provider: String?,
)

object SafetyLocationValidator {

    fun validate(sample: SafetyLocationSample): Result<SafetyLocationSample> {
        if (sample.latitude !in -90.0..90.0) {
            return Result.failure(
                IllegalArgumentException("Invalid latitude")
            )
        }

        if (sample.longitude !in -180.0..180.0) {
            return Result.failure(
                IllegalArgumentException("Invalid longitude")
            )
        }

        if (!sample.accuracyMeters.isFinite() || sample.accuracyMeters <= 0f) {
            return Result.failure(
                IllegalArgumentException("Invalid accuracy")
            )
        }

        return Result.success(sample)
    }
}
