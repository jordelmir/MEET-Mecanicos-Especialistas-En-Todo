package com.elysium369.meet.ui.screens.services

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Package sizes are a service contract, not a claim that a driver accepted the job. */
internal object CourierPackagePolicy {
    fun valid(vehicleKind: String, weightKg: Double?, lengthCm: Double?, widthCm: Double?, heightCm: Double?): Boolean {
        val limits = when (vehicleKind) {
            "MOTORCYCLE" -> listOf(10.0, 45.0, 35.0, 35.0)
            "CAR" -> listOf(20.0, 80.0, 60.0, 60.0)
            else -> return false
        }
        val values = listOf(weightKg, lengthCm, widthCm, heightCm)
        return values.all { it != null && it.isFinite() && it > 0.0 } &&
            values.zip(limits).all { (value, limit) -> value!! <= limit }
    }

    fun intake(vehicleKind: String, weightKg: Double, lengthCm: Double, widthCm: Double, heightCm: Double, description: String): JsonObject {
        require(valid(vehicleKind, weightKg, lengthCm, widthCm, heightCm))
        return buildJsonObject {
            put("vehicleKind", vehicleKind)
            put("weightKg", weightKg)
            put("lengthCm", lengthCm)
            put("widthCm", widthCm)
            put("heightCm", heightCm)
            put("detailed_specification", description)
        }
    }
}
