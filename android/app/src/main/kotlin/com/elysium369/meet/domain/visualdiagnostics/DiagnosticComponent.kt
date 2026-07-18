package com.elysium369.meet.domain.visualdiagnostics

data class DiagnosticComponent(
    val id: String,
    val engineType: EngineType,
    val name: String,
    val category: ComponentCategory,
    val description: String,
    val location: String,
    val commonFailures: List<String>,
    val workshopTests: List<ComponentTest>,
    val repairFlow: List<RepairStep>,
    val specs: List<ComponentSpec>,
    val requiredTools: List<String>,
    val safetyWarnings: List<SafetyWarning>,
    val relatedPids: List<RelatedPid>,
    val relatedDtcs: List<RelatedDtc>,
    val position: ComponentPosition,
    val meshKey: String,
    val verificationLevel: ComponentVerificationLevel = ComponentVerificationLevel.GENERIC_REPRESENTATION,
    val evidenceRequirements: List<String> = emptyList(),
    val marketplaceTags: List<String> = emptyList(),
    val exactnessDisclaimer: String = GENERIC_COMPONENT_DISCLAIMER
)

enum class ComponentHealthStatus {
    OK,
    WARNING,
    CRITICAL
}

enum class ComponentVerificationLevel {
    GENERIC_REPRESENTATION,
    PROBABLE_LOCATION,
    VEHICLE_VALIDATED,
    VIN_OEM_VALIDATED,
    VISUAL_CONFIRMED
}

const val GENERIC_COMPONENT_DISCLAIMER: String =
    "Representacion tecnica generica. Confirmar ubicacion, forma y compatibilidad por VIN/OEM/foto/manual antes de cotizar o reemplazar."

data class VisualBomNode(
    val id: String,
    val displayName: String,
    val system: BomSystem,
    val subsystem: String,
    val category: ComponentCategory,
    val meshKey: String,
    val componentIds: List<String>,
    val relatedDtcs: List<String>,
    val relatedPids: List<String>,
    val verificationLevel: ComponentVerificationLevel,
    val safetyCritical: Boolean,
    val evidenceRequirements: List<String>,
    val marketplaceTags: List<String>,
    val exactnessDisclaimer: String = GENERIC_COMPONENT_DISCLAIMER
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return false
        return id == normalized ||
            meshKey.lowercase() == normalized ||
            displayName.lowercase().contains(normalized) ||
            componentIds.any { it.lowercase() == normalized } ||
            relatedDtcs.any { it.lowercase() == normalized } ||
            marketplaceTags.any { it.lowercase().contains(normalized) }
    }
}

enum class BomSystem {
    VEHICLE_CORE,
    ENGINE,
    TRANSMISSION_DRIVELINE,
    SUSPENSION,
    STEERING,
    BRAKES,
    WHEELS_TIRES,
    ELECTRICAL,
    MODULES_CONTROLLERS,
    SENSORS,
    ACTUATORS,
    LIGHTING,
    HVAC,
    PASSIVE_SAFETY,
    ADAS,
    BODY,
    INTERIOR,
    ACCESS_IMMOBILIZER,
    HYBRID_EV,
    FLUIDS_CONSUMABLES,
    FASTENERS_HARDWARE
}

object VisualBomAtlas {
    fun nodes(): List<VisualBomNode> = masterNodes

    fun find(query: String): VisualBomNode? = masterNodes.firstOrNull { it.matches(query) }

    fun byDtc(code: String): List<VisualBomNode> {
        val normalized = code.trim().uppercase()
        return masterNodes.filter { node ->
            node.relatedDtcs.any { it.equals(normalized, ignoreCase = true) }
        }
    }

    fun bySystem(system: BomSystem): List<VisualBomNode> = masterNodes.filter { it.system == system }

    private val proofForExactCompatibility = listOf(
        "VIN o identificacion OEM verificable",
        "Numero de parte/OEM o tuple cerrado marca-modelo-ano-motor-OEM",
        "Foto/conector/medidas cuando no hay evidencia OEM"
    )

    private val electricalProof = listOf(
        "Alimentacion medida bajo carga",
        "Masa con caida de voltaje aceptable",
        "Continuidad/senal confirmada con diagrama OEM"
    )

    private fun node(
        id: String,
        displayName: String,
        system: BomSystem,
        subsystem: String,
        category: ComponentCategory,
        meshKey: String,
        componentIds: List<String>,
        relatedDtcs: List<String> = emptyList(),
        relatedPids: List<String> = emptyList(),
        verificationLevel: ComponentVerificationLevel = ComponentVerificationLevel.GENERIC_REPRESENTATION,
        safetyCritical: Boolean = false,
        evidenceRequirements: List<String> = proofForExactCompatibility,
        marketplaceTags: List<String> = emptyList()
    ) = VisualBomNode(
        id = id,
        displayName = displayName,
        system = system,
        subsystem = subsystem,
        category = category,
        meshKey = meshKey,
        componentIds = componentIds,
        relatedDtcs = relatedDtcs,
        relatedPids = relatedPids,
        verificationLevel = verificationLevel,
        safetyCritical = safetyCritical,
        evidenceRequirements = evidenceRequirements,
        marketplaceTags = marketplaceTags
    )

    private val masterNodes = listOf(
        node(
            id = "fuel_pump_circuit",
            displayName = "Circuito bomba de combustible",
            system = BomSystem.ENGINE,
            subsystem = "Combustible gasolina",
            category = ComponentCategory.FUEL,
            meshKey = "fuel_pump",
            componentIds = listOf("fuel_pump", "relay_fuel_pump", "fuse_fuel_pump", "ground_straps"),
            relatedDtcs = listOf("P0230", "P0231", "P0232", "P0087", "P0088"),
            relatedPids = listOf("012F", "0142"),
            safetyCritical = true,
            evidenceRequirements = electricalProof + listOf("Presion de combustible medida con manometro"),
            marketplaceTags = listOf("bomba combustible", "rele bomba", "fusible bomba", "arnes bomba")
        ),
        node(
            id = "fuel_pump_relay",
            displayName = "Rele de bomba de combustible",
            system = BomSystem.ELECTRICAL,
            subsystem = "Relay/fuse center",
            category = ComponentCategory.RELAY_FUSE,
            meshKey = "relay_fuel_pump",
            componentIds = listOf("relay_fuel_pump"),
            relatedDtcs = listOf("P0230", "P0231", "P0232"),
            relatedPids = listOf("0142"),
            safetyCritical = true,
            evidenceRequirements = electricalProof + listOf("Prueba de comando y carga del rele"),
            marketplaceTags = listOf("rele bomba combustible", "fuel pump relay")
        ),
        node(
            id = "charging_system",
            displayName = "Sistema de carga 12V",
            system = BomSystem.ELECTRICAL,
            subsystem = "Bateria y carga",
            category = ComponentCategory.ELECTRICAL,
            meshKey = "alternator",
            componentIds = listOf("alternator", "battery_12v", "serpentine_belt", "ground_straps", "fuse_battery_main"),
            relatedDtcs = listOf("P0562", "P0563", "P2503"),
            relatedPids = listOf("0142"),
            evidenceRequirements = electricalProof + listOf("Prueba de rizado AC y estado de banda"),
            marketplaceTags = listOf("alternador", "bateria", "banda serpentina", "fusible principal")
        ),
        node(
            id = "cooling_thermal_loop",
            displayName = "Circuito de enfriamiento motor",
            system = BomSystem.ENGINE,
            subsystem = "Enfriamiento",
            category = ComponentCategory.COOLING,
            meshKey = "radiator",
            componentIds = listOf("radiator", "cooling_fan", "water_pump", "thermostat_housing", "heater_core"),
            relatedDtcs = listOf("P0115", "P0116", "P0117", "P0118", "P0128", "P0217", "P0480", "P0481"),
            relatedPids = listOf("0105"),
            safetyCritical = true,
            evidenceRequirements = listOf("Temperatura real validada", "Prueba de presion/fugas", "Activacion de ventilador confirmada"),
            marketplaceTags = listOf("radiador", "termostato", "bomba agua", "ventilador enfriamiento")
        ),
        node(
            id = "air_metering_intake",
            displayName = "Medicion de aire y admision",
            system = BomSystem.ENGINE,
            subsystem = "Admision de aire",
            category = ComponentCategory.AIR_INTAKE,
            meshKey = "maf_sensor",
            componentIds = listOf("maf_sensor", "map_sensor", "iat_sensor", "throttle_body", "maf_harness"),
            relatedDtcs = listOf("P0100", "P0101", "P0102", "P0103", "P0105", "P0110", "P0171", "P0172", "P2135"),
            relatedPids = listOf("010B", "010F", "0110", "0111"),
            evidenceRequirements = electricalProof + listOf("Inspeccion de fugas de aire no medido"),
            marketplaceTags = listOf("maf", "map", "iat", "cuerpo aceleracion", "manguera admision")
        ),
        node(
            id = "abs_brake_control",
            displayName = "ABS/ESC y freno hidraulico",
            system = BomSystem.BRAKES,
            subsystem = "ABS / ESC / TCS",
            category = ComponentCategory.BRAKES,
            meshKey = "abs_module",
            componentIds = listOf("abs_module", "abs_harness"),
            relatedDtcs = listOf("C0035", "C0040", "C0045", "C0050", "U0121"),
            safetyCritical = true,
            evidenceRequirements = listOf("Lectura de sensores de rueda", "Inspeccion de arnes ABS", "Prueba hidraulica antes de liberar vehiculo"),
            marketplaceTags = listOf("modulo abs", "sensor velocidad rueda", "bomba abs")
        ),
        node(
            id = "ev_high_voltage_core",
            displayName = "Nucleo alto voltaje EV/Hibrido",
            system = BomSystem.HYBRID_EV,
            subsystem = "Alta tension",
            category = ComponentCategory.EV_HIGH_VOLTAGE,
            meshKey = "hv_battery_pack",
            componentIds = listOf("hv_battery", "hv_battery_ev", "inverter", "inverter_hybrid", "dc_dc_converter", "dc_dc_converter_ev", "bms_ev"),
            relatedDtcs = listOf("P0A80", "P0A7F", "P0AA6", "P0A78", "P0A09", "P0A0F"),
            relatedPids = listOf("0142"),
            verificationLevel = ComponentVerificationLevel.PROBABLE_LOCATION,
            safetyCritical = true,
            evidenceRequirements = listOf("Tecnico certificado HV", "Desenergizacion OEM", "Ausencia de tension confirmada", "EPP HV registrado"),
            marketplaceTags = listOf("bateria hv", "inversor", "dc-dc", "bms")
        )
    )
}
