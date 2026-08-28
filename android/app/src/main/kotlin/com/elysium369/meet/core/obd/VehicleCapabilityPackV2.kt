package com.elysium369.meet.core.obd

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class CustomPidDefinition(
    val id: String,
    val mode: String,
    val pid: String,
    val formula: String,
    val minPhysical: Double? = null,
    val maxPhysical: Double? = null,
    val unit: String,
    val description: String,
    val ecuHeader: String? = null,
)

@Serializable
data class ActuatorTestDefinition(
    val testId: String,
    val name: String,
    val command: String,
    val safetyCondition: String,
    val requiresManualConfirmation: Boolean = true,
)

@Serializable
data class DtcSourceDefinition(
    val sourceId: String,
    val name: String,
    val queryCommands: List<String>,
    val clearCommand: String,
    val protocolFamily: String,
)

@Serializable
data class VehicleCapabilityPackV2(
    val packId: String,
    val oemBrand: String,
    val supportedModels: List<String>,
    val minYear: Int,
    val maxYear: Int,
    val signatureSha256: String,
    val pids: List<CustomPidDefinition>,
    val actuatorTests: List<ActuatorTestDefinition> = emptyList(),
    val dtcSources: List<DtcSourceDefinition> = emptyList(),
) {
    fun matchesVehicle(brand: String, model: String, year: Int): Boolean {
        if (!oemBrand.equals(brand, ignoreCase = true) && !oemBrand.equals("GENERIC", ignoreCase = true)) {
            return false
        }
        if (year < minYear || year > maxYear) return false
        if (supportedModels.isEmpty() || supportedModels.any { it.equals("ALL", ignoreCase = true) }) {
            return true
        }
        return supportedModels.any { it.equals(model, ignoreCase = true) }
    }

    fun verifyIntegrity(): Boolean {
        val payload = "$packId:$oemBrand:$minYear:$maxYear:${pids.size}:${actuatorTests.size}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        val computed = digest.take(8).joinToString("") { "%02x".format(it) }
        return signatureSha256.startsWith(computed)
    }
}

/**
 * Built-in registry of signed Vehicle Capability Packs for deep OEM coverage.
 */
object VehicleCapabilityPackRegistry {

    fun getPackForVehicle(brand: String, model: String, year: Int): List<VehicleCapabilityPackV2> {
        return ALL_PACKS.filter { it.matchesVehicle(brand, model, year) }
    }

    private fun generatePackSignature(packId: String, oem: String, min: Int, max: Int, pids: Int, actuators: Int): String {
        val payload = "$packId:$oem:$min:$max:$pids:$actuators"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) } + "-elysium-vanguard-v6"
    }

    val TOYOTA_HYBRID_PACK = VehicleCapabilityPackV2(
        packId = "TOYOTA_HYBRID_GEN3_GEN4",
        oemBrand = "Toyota",
        supportedModels = listOf("Prius", "Camry Hybrid", "RAV4 Hybrid", "Corolla Hybrid", "Highlander Hybrid"),
        minYear = 2009,
        maxYear = 2026,
        signatureSha256 = generatePackSignature("TOYOTA_HYBRID_GEN3_GEN4", "Toyota", 2009, 2026, 3, 1),
        pids = listOf(
            CustomPidDefinition("HV_SOC", "21", "C3", "(A*100)/255", 0.0, 100.0, "%", "Hybrid Battery State of Charge (SOC)", "7E2"),
            CustomPidDefinition("HV_BAT_TEMP", "21", "CF", "A-40", -40.0, 120.0, "°C", "Hybrid Battery Average Temperature", "7E2"),
            CustomPidDefinition("HV_BAT_VOLT", "21", "8B", "(A*256+B)*0.1", 150.0, 450.0, "V", "High Voltage Traction Battery Total Voltage", "7E2"),
        ),
        actuatorTests = listOf(
            ActuatorTestDefinition("HV_FAN_TEST", "HV Battery Cooling Fan Test", "30 01 02", "Vehicle Parked, Ready OFF", true),
        ),
        dtcSources = listOf(
            DtcSourceDefinition("TOYOTA_HV_ECU", "Hybrid Control ECU DTCs", listOf("18 02", "19 02 FF"), "14 FF 00", "UDS"),
        ),
    )

    val VAG_TSI_PACK = VehicleCapabilityPackV2(
        packId = "VAG_EA888_EA211_GEN3",
        oemBrand = "Volkswagen",
        supportedModels = listOf("Golf", "Jetta", "Tiguan", "Passat", "Polo", "Audi A3", "Audi A4"),
        minYear = 2012,
        maxYear = 2026,
        signatureSha256 = generatePackSignature("VAG_EA888_EA211_GEN3", "Volkswagen", 2012, 2026, 3, 0),
        pids = listOf(
            CustomPidDefinition("VAG_BOOST_ACTUAL", "22", "F40D", "((A*256+B)*0.039)-100", -100.0, 250.0, "kPa", "Turbocharger Boost Pressure (Actual)", "7E0"),
            CustomPidDefinition("VAG_OIL_TEMP", "22", "F45C", "A-40", -40.0, 160.0, "°C", "Engine Oil Temperature Sensor", "7E0"),
            CustomPidDefinition("VAG_CAM_PHASE", "22", "F422", "(A*0.5)-50", -50.0, 50.0, "°", "Camshaft Phase Angle Deviation", "7E0"),
        ),
    )

    val ALL_PACKS = listOf(
        TOYOTA_HYBRID_PACK,
        VAG_TSI_PACK,
    )
}
