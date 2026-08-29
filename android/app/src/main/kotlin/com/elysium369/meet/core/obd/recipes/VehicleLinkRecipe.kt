package com.elysium369.meet.core.obd.recipes

import com.elysium369.meet.core.obd.ObdProtocol

/**
 * VehicleLinkRecipe — Production-grade automotive link descriptor.
 * Decouples manufacturer-specific physical addressing, baud rates, and wake-up messages
 * from the universal ELM negotiation engine.
 */
data class VehicleLinkRecipe(
    val id: String,
    val displayName: String,
    val manufacturer: String,
    val protocol: ObdProtocol,
    val requestHeader: String? = null,
    val initCommands: List<String> = emptyList(),
    val wakeMessage: String? = null,
    val probeTimeoutMs: Long = 3500L,
    val confidence: Float = 1.0f,
    val provenance: String = "OEM_SPEC"
) {
    companion object {
        // Universal Standard CAN Recipes
        val GENERIC_CAN_11BIT_500K = VehicleLinkRecipe(
            id = "CAN_11BIT_500K_GENERIC",
            displayName = "Standard ISO 15765-4 (CAN 11-bit 500k)",
            manufacturer = "GENERIC",
            protocol = ObdProtocol.CAN_11BIT_500K,
            requestHeader = "7DF",
            initCommands = listOf("ATCAF1", "ATCRA"),
            probeTimeoutMs = 3000L
        )

        val GENERIC_CAN_29BIT_500K = VehicleLinkRecipe(
            id = "CAN_29BIT_500K_GENERIC",
            displayName = "Extended ISO 15765-4 (CAN 29-bit 500k)",
            manufacturer = "GENERIC",
            protocol = ObdProtocol.CAN_29BIT_500K,
            requestHeader = "18DB33F1",
            initCommands = listOf("ATCAF1", "ATCRA"),
            probeTimeoutMs = 3000L
        )

        val GENERIC_CAN_11BIT_250K = VehicleLinkRecipe(
            id = "CAN_11BIT_250K_GENERIC",
            displayName = "Standard ISO 15765-4 (CAN 11-bit 250k)",
            manufacturer = "GENERIC",
            protocol = ObdProtocol.CAN_11BIT_250K,
            requestHeader = "7DF",
            initCommands = listOf("ATCAF1", "ATCRA"),
            probeTimeoutMs = 3000L
        )

        val GENERIC_CAN_29BIT_250K = VehicleLinkRecipe(
            id = "CAN_29BIT_250K_GENERIC",
            displayName = "Extended ISO 15765-4 (CAN 29-bit 250k)",
            manufacturer = "GENERIC",
            protocol = ObdProtocol.CAN_29BIT_250K,
            requestHeader = "18DB33F1",
            initCommands = listOf("ATCAF1", "ATCRA"),
            probeTimeoutMs = 3000L
        )

        // Manufacturer Legacy K-Line Recipes
        val HYUNDAI_KLINE_FAST_KEFICO = VehicleLinkRecipe(
            id = "HYUNDAI_KLINE_FAST_KEFICO",
            displayName = "Hyundai/Kia KWP2000 Fast (Bosch/Kefico ECM 0x10)",
            manufacturer = "HYUNDAI",
            protocol = ObdProtocol.KWP2000_FAST,
            requestHeader = "8110F1",
            initCommands = listOf("ATIB10", "ATAL", "ATWM8110F13E"),
            probeTimeoutMs = 5000L,
            provenance = "HYUNDAI_ACCENT_VERNA_2005"
        )

        val HYUNDAI_KLINE_FAST_BROADCAST = VehicleLinkRecipe(
            id = "HYUNDAI_KLINE_FAST_BROADCAST",
            displayName = "Hyundai/Kia KWP2000 Fast (Broadcast 0x33)",
            manufacturer = "HYUNDAI",
            protocol = ObdProtocol.KWP2000_FAST,
            requestHeader = "C233F1",
            initCommands = listOf("ATIB10", "ATAL"),
            probeTimeoutMs = 5000L
        )

        val HYUNDAI_KLINE_ISO9141 = VehicleLinkRecipe(
            id = "HYUNDAI_KLINE_ISO9141",
            displayName = "Hyundai/Kia ISO 9141-2 Physical",
            manufacturer = "HYUNDAI",
            protocol = ObdProtocol.ISO9141,
            requestHeader = "686A10",
            initCommands = listOf("ATIB10", "ATAL"),
            probeTimeoutMs = 5000L
        )

        val TOYOTA_KLINE_ISO9141 = VehicleLinkRecipe(
            id = "TOYOTA_KLINE_ISO9141",
            displayName = "Toyota Pre-CAN ISO 9141-2",
            manufacturer = "TOYOTA",
            protocol = ObdProtocol.ISO9141,
            requestHeader = "686AF1",
            initCommands = listOf("ATIB10", "ATAL"),
            probeTimeoutMs = 5000L
        )

        val VAG_KLINE_KWP1281 = VehicleLinkRecipe(
            id = "VAG_KLINE_KWP1281",
            displayName = "VAG Pre-CAN KWP2000/K-Line",
            manufacturer = "VOLKSWAGEN",
            protocol = ObdProtocol.KWP2000,
            requestHeader = "8101F1",
            initCommands = listOf("ATIB10", "ATAL"),
            probeTimeoutMs = 5000L
        )

        val FORD_J1850_PWM = VehicleLinkRecipe(
            id = "FORD_J1850_PWM",
            displayName = "Ford Pre-CAN J1850 PWM",
            manufacturer = "FORD",
            protocol = ObdProtocol.J1850_PWM,
            requestHeader = "616AF1",
            probeTimeoutMs = 4000L
        )

        val GM_J1850_VPW = VehicleLinkRecipe(
            id = "GM_J1850_VPW",
            displayName = "GM Pre-CAN J1850 VPW",
            manufacturer = "GM",
            protocol = ObdProtocol.J1850_VPW,
            requestHeader = "686AF1",
            probeTimeoutMs = 4000L
        )

        val HYUNDAI_KLINE_G4ED_AUTO = VehicleLinkRecipe(
            id = "HYUNDAI_KLINE_G4ED_AUTO",
            displayName = "Hyundai G4ED 1.6L Auto (Kefico/Siemens AT)",
            manufacturer = "HYUNDAI",
            protocol = ObdProtocol.KWP2000_FAST,
            requestHeader = "8110F1",
            initCommands = listOf("ATIB10", "ATAL", "ATKW0", "ATWM8110F13E"),
            probeTimeoutMs = 4500L,
            provenance = "HYUNDAI_ACCENT_VERNA_G4ED_2005"
        )

        val NISSAN_KLINE_CONSULT2 = VehicleLinkRecipe(
            id = "NISSAN_KLINE_CONSULT2",
            displayName = "Nissan Consult-II K-Line (ECM 0x12)",
            manufacturer = "NISSAN",
            protocol = ObdProtocol.KWP2000_FAST,
            requestHeader = "8112F1",
            initCommands = listOf("ATIB10", "ATAL"),
            probeTimeoutMs = 4500L,
            provenance = "NISSAN_CONSULT_II_SPEC"
        )

        val RENAULT_KLINE_KWP = VehicleLinkRecipe(
            id = "RENAULT_KLINE_KWP",
            displayName = "Renault/Dacia KWP2000 Physical (0x7A)",
            manufacturer = "RENAULT",
            protocol = ObdProtocol.KWP2000,
            requestHeader = "817AF1",
            initCommands = listOf("ATIB10", "ATAL"),
            probeTimeoutMs = 4500L,
            provenance = "RENAULT_CLIP_KWP_SPEC"
        )

        val ALL_RECIPES = listOf(
            GENERIC_CAN_11BIT_500K,
            GENERIC_CAN_29BIT_500K,
            GENERIC_CAN_11BIT_250K,
            GENERIC_CAN_29BIT_250K,
            HYUNDAI_KLINE_G4ED_AUTO,
            HYUNDAI_KLINE_FAST_KEFICO,
            HYUNDAI_KLINE_FAST_BROADCAST,
            HYUNDAI_KLINE_ISO9141,
            TOYOTA_KLINE_ISO9141,
            NISSAN_KLINE_CONSULT2,
            RENAULT_KLINE_KWP,
            VAG_KLINE_KWP1281,
            FORD_J1850_PWM,
            GM_J1850_VPW
        )

        private val GENERIC_RECIPES = ALL_RECIPES.filter { it.manufacturer == "GENERIC" }

        /**
         * Returns only protocol probes justified by the currently selected vehicle.
         * Manufacturer-specific wakeups and headers must never be broadcast merely
         * because they exist in the catalogue.
         */
        fun negotiationCandidates(
            manufacturer: String?,
            vehicleYear: Int? = null,
        ): List<VehicleLinkRecipe> {
            val manufacturerRecipes = getRecipesForManufacturer(manufacturer)
            return if (manufacturerRecipes.isNotEmpty() && vehicleYear != null && vehicleYear < 2008) {
                manufacturerRecipes + GENERIC_RECIPES
            } else {
                GENERIC_RECIPES + manufacturerRecipes
            }.distinctBy(VehicleLinkRecipe::id)
        }

        fun getRecipesForManufacturer(mfr: String?): List<VehicleLinkRecipe> {
            if (mfr.isNullOrBlank()) return emptyList()
            val normalized = mfr.trim().uppercase()
            return ALL_RECIPES.filter { it.manufacturer.equals(normalized, ignoreCase = true) }
        }
    }
}
