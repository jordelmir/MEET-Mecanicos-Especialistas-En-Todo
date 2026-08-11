package com.elysium369.meet.core.obd

/**
 * Gate for coding, resets and adaptations.
 *
 * Generic manufacturer guesses are intentionally not executable. A future
 * implementation must resolve a signed capability pack by vehicle + ECU,
 * execute through ActiveDiagnosticSafetyKernel and verify a typed response.
 */
class AdvancedDiagnosticManager(
    @Suppress("UNUSED_PARAMETER") private val obdSession: ObdSession,
) {
    data class Availability(
        val executable: Boolean,
        val reason: String,
    )

    fun availability(): Availability = Availability(
        executable = false,
        reason = "Requiere paquete OEM firmado y compatibilidad ECU verificable.",
    )

    suspend fun resetOilService(manufacturer: String): Boolean = blocked("oil-reset", manufacturer)

    suspend fun registerBattery(manufacturer: String, capacityAh: Int): Boolean =
        blocked("battery-registration-$capacityAh", manufacturer)

    suspend fun resetEPB(manufacturer: String, open: Boolean): Boolean =
        blocked(if (open) "epb-open" else "epb-close", manufacturer)

    suspend fun calibrateSAS(manufacturer: String): Boolean = blocked("sas-calibration", manufacturer)

    suspend fun relearnThrottle(manufacturer: String): Boolean = blocked("throttle-relearn", manufacturer)

    suspend fun regenerateDPF(manufacturer: String): Boolean = blocked("dpf-regeneration", manufacturer)

    suspend fun performCoding(featureId: String, enable: Boolean): Boolean =
        blocked("coding-$featureId-$enable", manufacturer = "UNVERIFIED")

    private fun blocked(operation: String, manufacturer: String): Boolean {
        // Keep an explicit audit breadcrumb without transmitting anything.
        android.util.Log.w(
            "AdvancedDiagnosticManager",
            "Blocked unsourced active operation=$operation manufacturer=$manufacturer",
        )
        return false
    }
}

data class CodingFeature(
    val id: String,
    val name: String,
    val description: String,
    val manufacturer: String,
    val isEnabled: Boolean,
)
