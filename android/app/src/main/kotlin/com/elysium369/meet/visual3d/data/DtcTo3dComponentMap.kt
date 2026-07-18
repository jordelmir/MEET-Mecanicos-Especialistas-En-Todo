package com.elysium369.meet.visual3d.data

import com.elysium369.meet.visual3d.domain.DtcComponentMapping

/**
 * ═══════════════════════════════════════════════════════════════════
 * ELYSIUM VANGUARD — DTC → 3D Component Static Mapping
 * ═══════════════════════════════════════════════════════════════════
 *
 * Maps diagnostic trouble codes to their related 3D engine components.
 * Primary components are those directly referenced by the DTC circuit.
 * Secondary components share the system and may be root cause.
 *
 * HARD RULE: Initial probabilities are NEVER > 0.5 for primary
 *            and NEVER > 0.3 for secondary without physical evidence.
 * ═══════════════════════════════════════════════════════════════════
 */
object DtcTo3dComponentMap {

    private val mappings: Map<String, DtcComponentMapping> = buildMap {

        // ─── Fuel System ────────────────────────────────────────────
        put("P0230", DtcComponentMapping(
            dtcCode = "P0230",
            description = "Fuel Pump Primary Circuit Malfunction",
            primaryComponentIds = listOf(
                "fuel_pump_relay",
                "fuel_pump_fuse",
                "fuel_pump_power_feed",
                "fuel_pump_ground",
                "fuel_pump_connector"
            ),
            secondaryComponentIds = listOf(
                "fuel_pump",
                "pcm_fuel_pump_driver"
            ),
            systemAffected = "FUEL",
            initialProbabilities = mapOf(
                "fuel_pump_relay" to 0.35,
                "fuel_pump_fuse" to 0.30,
                "fuel_pump_power_feed" to 0.25,
                "fuel_pump_ground" to 0.20,
                "fuel_pump_connector" to 0.20,
                "fuel_pump" to 0.15,
                "pcm_fuel_pump_driver" to 0.05
            )
        ))

        // ─── Fuel Trim / Lean Condition ─────────────────────────────
        put("P0171", DtcComponentMapping(
            dtcCode = "P0171",
            description = "System Too Lean (Bank 1)",
            primaryComponentIds = listOf(
                "maf_sensor",
                "intake_manifold_gasket",
                "vacuum_hose",
                "fuel_injector_bank1",
                "fuel_pressure_regulator"
            ),
            secondaryComponentIds = listOf(
                "o2_sensor_b1s1",
                "pcv_valve",
                "fuel_pump",
                "fuel_filter",
                "exhaust_leak"
            ),
            systemAffected = "FUEL",
            initialProbabilities = mapOf(
                "maf_sensor" to 0.30,
                "intake_manifold_gasket" to 0.25,
                "vacuum_hose" to 0.25,
                "fuel_injector_bank1" to 0.20,
                "fuel_pressure_regulator" to 0.20,
                "o2_sensor_b1s1" to 0.15,
                "pcv_valve" to 0.15,
                "fuel_pump" to 0.10,
                "fuel_filter" to 0.10,
                "exhaust_leak" to 0.05
            )
        ))

        // ─── Misfire ────────────────────────────────────────────────
        put("P0301", DtcComponentMapping(
            dtcCode = "P0301",
            description = "Cylinder 1 Misfire Detected",
            primaryComponentIds = listOf(
                "spark_plug_cyl1",
                "ignition_coil_cyl1",
                "fuel_injector_cyl1"
            ),
            secondaryComponentIds = listOf(
                "spark_plug_wire_cyl1",
                "compression_cyl1",
                "intake_valve_cyl1",
                "exhaust_valve_cyl1",
                "head_gasket"
            ),
            systemAffected = "IGNITION",
            initialProbabilities = mapOf(
                "spark_plug_cyl1" to 0.40,
                "ignition_coil_cyl1" to 0.35,
                "fuel_injector_cyl1" to 0.25,
                "spark_plug_wire_cyl1" to 0.20,
                "compression_cyl1" to 0.10,
                "intake_valve_cyl1" to 0.05,
                "exhaust_valve_cyl1" to 0.05,
                "head_gasket" to 0.03
            )
        ))
        // P0302-P0308 follow same pattern for cylinders 2-8
        for (cyl in 2..8) {
            val code = "P030$cyl"
            put(code, DtcComponentMapping(
                dtcCode = code,
                description = "Cylinder $cyl Misfire Detected",
                primaryComponentIds = listOf(
                    "spark_plug_cyl$cyl",
                    "ignition_coil_cyl$cyl",
                    "fuel_injector_cyl$cyl"
                ),
                secondaryComponentIds = listOf(
                    "spark_plug_wire_cyl$cyl",
                    "compression_cyl$cyl"
                ),
                systemAffected = "IGNITION",
                initialProbabilities = mapOf(
                    "spark_plug_cyl$cyl" to 0.40,
                    "ignition_coil_cyl$cyl" to 0.35,
                    "fuel_injector_cyl$cyl" to 0.25,
                    "spark_plug_wire_cyl$cyl" to 0.20,
                    "compression_cyl$cyl" to 0.10
                )
            ))
        }

        // ─── Catalyst Efficiency ────────────────────────────────────
        put("P0420", DtcComponentMapping(
            dtcCode = "P0420",
            description = "Catalyst System Efficiency Below Threshold (Bank 1)",
            primaryComponentIds = listOf(
                "catalytic_converter_b1",
                "o2_sensor_b1s2"
            ),
            secondaryComponentIds = listOf(
                "o2_sensor_b1s1",
                "exhaust_manifold_gasket",
                "exhaust_pipe_leak",
                "fuel_injector_bank1"
            ),
            systemAffected = "EXHAUST_EMISSIONS",
            initialProbabilities = mapOf(
                "catalytic_converter_b1" to 0.40,
                "o2_sensor_b1s2" to 0.30,
                "o2_sensor_b1s1" to 0.15,
                "exhaust_manifold_gasket" to 0.10,
                "exhaust_pipe_leak" to 0.10,
                "fuel_injector_bank1" to 0.05
            )
        ))

        // ─── Coolant Temperature ────────────────────────────────────
        put("P0115", DtcComponentMapping(
            dtcCode = "P0115",
            description = "Engine Coolant Temperature Circuit Malfunction",
            primaryComponentIds = listOf(
                "ect_sensor",
                "ect_connector",
                "ect_wiring"
            ),
            secondaryComponentIds = listOf(
                "thermostat",
                "pcm_ect_input"
            ),
            systemAffected = "COOLING",
            initialProbabilities = mapOf(
                "ect_sensor" to 0.40,
                "ect_connector" to 0.30,
                "ect_wiring" to 0.20,
                "thermostat" to 0.10,
                "pcm_ect_input" to 0.05
            )
        ))

        // ─── O2 Sensor ─────────────────────────────────────────────
        put("P0130", DtcComponentMapping(
            dtcCode = "P0130",
            description = "O2 Sensor Circuit Malfunction (Bank 1, Sensor 1)",
            primaryComponentIds = listOf(
                "o2_sensor_b1s1",
                "o2_sensor_connector_b1s1",
                "o2_sensor_wiring_b1s1"
            ),
            secondaryComponentIds = listOf(
                "o2_sensor_heater_fuse",
                "exhaust_leak",
                "pcm_o2_input"
            ),
            systemAffected = "EXHAUST_EMISSIONS",
            initialProbabilities = mapOf(
                "o2_sensor_b1s1" to 0.45,
                "o2_sensor_connector_b1s1" to 0.25,
                "o2_sensor_wiring_b1s1" to 0.20,
                "o2_sensor_heater_fuse" to 0.10,
                "exhaust_leak" to 0.10,
                "pcm_o2_input" to 0.03
            )
        ))

        // ─── TPS ────────────────────────────────────────────────────
        put("P0120", DtcComponentMapping(
            dtcCode = "P0120",
            description = "Throttle Position Sensor/Switch A Circuit Malfunction",
            primaryComponentIds = listOf(
                "tps_sensor",
                "tps_connector",
                "tps_wiring"
            ),
            secondaryComponentIds = listOf(
                "throttle_body",
                "pcm_tps_input"
            ),
            systemAffected = "ENGINE_ELECTRICAL",
            initialProbabilities = mapOf(
                "tps_sensor" to 0.45,
                "tps_connector" to 0.25,
                "tps_wiring" to 0.20,
                "throttle_body" to 0.05,
                "pcm_tps_input" to 0.03
            )
        ))

        // ─── MAP Sensor ─────────────────────────────────────────────
        put("P0105", DtcComponentMapping(
            dtcCode = "P0105",
            description = "Manifold Absolute Pressure/Barometric Pressure Circuit Malfunction",
            primaryComponentIds = listOf(
                "map_sensor",
                "map_sensor_hose",
                "map_connector"
            ),
            secondaryComponentIds = listOf(
                "intake_manifold_gasket",
                "pcm_map_input"
            ),
            systemAffected = "SENSORS",
            initialProbabilities = mapOf(
                "map_sensor" to 0.40,
                "map_sensor_hose" to 0.30,
                "map_connector" to 0.20,
                "intake_manifold_gasket" to 0.10,
                "pcm_map_input" to 0.03
            )
        ))
    }

    /**
     * Get the component mapping for a given DTC code.
     * Returns null if the DTC is not mapped (generic/unknown).
     */
    fun getMapping(dtcCode: String): DtcComponentMapping? {
        return mappings[dtcCode.trim().uppercase()]
    }

    /**
     * Get all mappings for a list of DTC codes.
     * Useful when multiple DTCs are active simultaneously.
     */
    fun getMappings(dtcCodes: List<String>): List<DtcComponentMapping> {
        return dtcCodes.mapNotNull { getMapping(it) }
    }

    /**
     * Get all component IDs (primary + secondary) related to a DTC.
     */
    fun getAllComponentIds(dtcCode: String): List<String> {
        val mapping = getMapping(dtcCode) ?: return emptyList()
        return mapping.primaryComponentIds + mapping.secondaryComponentIds
    }

    /**
     * Check if a specific component is related to any active DTC.
     */
    fun isComponentRelatedToDtc(componentId: String, activeDtcs: List<String>): Boolean {
        return activeDtcs.any { dtc ->
            val mapping = getMapping(dtc) ?: return@any false
            componentId in mapping.primaryComponentIds || componentId in mapping.secondaryComponentIds
        }
    }
}
