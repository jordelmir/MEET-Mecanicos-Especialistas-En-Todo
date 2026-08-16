package com.elysium369.meet.domain.diagnostics

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Stable, owner-scoped identity for a VIN observed directly from an ECU. */
object VinVehicleIdentity {
    private val forbiddenCharacters = Regex("[IOQ]")
    private val validCharacters = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    fun normalize(rawVin: String?): String? {
        val clean = rawVin?.trim()?.uppercase() ?: return null
        if (!validCharacters.matches(clean) || forbiddenCharacters.containsMatchIn(clean)) return null
        return clean
    }

    fun stableVehicleId(ownerId: String, rawVin: String): String {
        require(ownerId.isNotBlank()) { "Owner identity is required" }
        val vin = requireNotNull(normalize(rawVin)) { "A valid 17-character VIN is required" }
        return UUID.nameUUIDFromBytes(
            "meet:ecu-vin:v1:$ownerId:$vin".toByteArray(StandardCharsets.UTF_8),
        ).toString()
    }
}
