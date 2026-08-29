package com.elysium369.meet.ride.data.remote

import java.util.Locale

/**
 * Canonical identifiers shared with the server verification contracts.
 * Local provider profile aliases never become authorization by themselves.
 */
object ServiceVerificationTypePolicy {
    private val legacySubmissionTypes = setOf(
        "PASSENGER",
        "RIDE_DRIVER",
        "TOW_TRUCK",
        "MECHANIC",
        "PARTS_STORE",
        "SERVICE_PROVIDER",
    )

    val capabilityTypes: Set<String> = setOf(
        "RIDE_DRIVER",
        "TOW_TRUCK",
        "MECHANIC",
        "PARTS_STORE",
        "SERVICE_PROVIDER",
        "WORKSHOP",
        "LAWYER",
        "NOTARY",
        "PROPERTY_BROKER",
        "PROPERTY_SELLER",
        "FUEL_STATION_STAFF",
        "FLEET_OPERATOR",
    )

    fun canonicalLegacyType(rawValue: String): String? {
        val normalized = rawValue.trim().uppercase(Locale.ROOT)
        val canonical = when (normalized) {
            "DRIVER", "RIDE" -> "RIDE_DRIVER"
            "TOW", "TOW_DRIVER", "TOW_PROVIDER" -> "TOW_TRUCK"
            "PART_STORE", "STORE" -> "PARTS_STORE"
            else -> normalized
        }
        return canonical.takeIf(legacySubmissionTypes::contains)
    }

    fun canonicalCapability(rawValue: String): String? {
        val legacy = canonicalLegacyType(rawValue)
        if (legacy != null && legacy != "PASSENGER") return legacy
        return rawValue.trim().uppercase(Locale.ROOT).takeIf(capabilityTypes::contains)
    }
}
