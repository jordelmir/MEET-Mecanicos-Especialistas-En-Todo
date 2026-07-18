package com.elysium369.meet.visual3d.domain

import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode
import java.text.Normalizer

data class GenericSystemSourceAlias(
    val systemId: String,
    val literalName: String
)

data class GenericSystemAssetBinding(
    val meshKey: String,
    val sourceAliases: Set<GenericSystemSourceAlias>,
    val serviceStage: Int,
    val explodedOffset: CatalogServiceOffset
) {
    val isSelectable: Boolean
        get() = sourceAliases.isNotEmpty()
}

data class GenericSystemAssetDefinition(
    val id: String,
    val assetPath: String,
    val manifestPath: String,
    val supportedSystemIds: Set<String>,
    val bindings: List<GenericSystemAssetBinding>,
    val scaleToUnits: Float = 2.30f
) {
    val requiredMeshKeys: Set<String> = bindings.mapTo(linkedSetOf(), GenericSystemAssetBinding::meshKey)
}

/**
 * Stable source-to-mesh contract for the project-owned L2 vehicle-system atlas.
 *
 * The models are recognizable generic service assemblies. Their proportions and service offsets
 * are illustrative renderer units, never measurements or proof that a component is installed on
 * the active vehicle. Selection is only enabled when a literal proprietary source record exists.
 */
object GenericVehicleSystemsAssetContract {
    const val MESH_NODE_PREFIX = "system_mesh__"
    const val AUTHORITY = "L2_GENERIC_ASSEMBLY"
    private const val SERVICE_OFFSET_HORIZONTAL_SCALE = 0.20f
    private const val SERVICE_OFFSET_VERTICAL_SCALE = 0.17f
    private const val SERVICE_OFFSET_DEPTH_SCALE = 0.08f

    val intakeBoost = asset(
        id = "intake_boost",
        assetFile = "intake_boost/generic_intake_boost.glb",
        supportedSystemIds = setOf("intake", "forced_induction"),
        bindings = listOf(
            binding("air_filter_box", 1, -2.8f, 0.8f, 0f, alias("intake", "1. Caja del filtro de aire")),
            binding("air_filter", 2, -2.8f, 1.8f, 0f, alias("intake", "2. Filtro de aire")),
            binding("air_box_lid", 1, -2.8f, 2.6f, 0f, alias("intake", "3. Tapa de caja de filtro")),
            binding("intake_duct", 1, -1.8f, 1.3f, -1.1f, alias("intake", "4. Ducto de admisión", "6. Manguera de admisión")),
            binding("maf_sensor", 2, -1.0f, 2.2f, -1.2f, alias("intake", "12. Sensor MAF"), alias("sensors", "Sensor MAF")),
            binding("throttle_body", 3, 1.5f, 1.4f, -1.2f, alias("intake", "8. Mariposa de aceleración")),
            binding("tps_sensor", 3, 2.3f, 2.0f, -1.1f, alias("intake", "9. Sensor TPS"), alias("sensors", "Sensor TPS")),
            binding("throttle_actuator", 3, 2.6f, 1.1f, -1.2f, alias("intake", "10. Motor actuador de aceleración electrónica")),
            binding("iac_valve", 3, 2.3f, 0.4f, -1.1f, alias("intake", "11. Válvula IAC")),
            binding("map_sensor", 4, 0.4f, 2.8f, -1.0f, alias("intake", "13. Sensor MAP"), alias("sensors", "Sensor MAP")),
            binding("iat_sensor", 2, -0.4f, 2.8f, -1.0f, alias("intake", "14. Sensor IAT"), alias("sensors", "Sensor IAT")),
            binding("intake_manifold", 4, 0f, 2.2f, 0f, alias("intake", "15. Múltiple de admisión")),
            binding("intake_manifold_gasket", 5, 0f, 3.0f, 0f, alias("intake", "16. Junta de múltiple de admisión")),
            binding("variable_intake_actuator", 4, 1.4f, 2.8f, 0.8f, alias("intake", "18. Actuador de múltiple variable")),
            binding("variable_intake_solenoid", 4, 2.0f, 2.4f, 1.1f, alias("intake", "19. Solenoide de admisión variable")),
            binding("vacuum_hoses", 2, -0.8f, 2.7f, 1.5f, alias("intake", "22. Mangueras de vacío")),
            binding("turbocharger", 5, 2.2f, -1.4f, 0f, alias("forced_induction", "1. Turbocharger")),
            binding("turbo_cold_housing", 5, 3.0f, -1.0f, -1.0f, alias("forced_induction", "3. Carcasa fría")),
            binding("turbo_hot_housing", 5, 3.0f, -1.0f, 1.0f, alias("forced_induction", "4. Carcasa caliente")),
            binding("wastegate_actuator", 6, 3.5f, -0.2f, 0.8f, alias("forced_induction", "7. Actuador wastegate"), alias("actuators", "Actuador wastegate")),
            binding("boost_control_solenoid", 6, 3.5f, 0.7f, 0.2f, alias("forced_induction", "8. Solenoide boost control")),
            binding("turbo_oil_lines", 5, 2.3f, -2.4f, 1.4f, alias("forced_induction", "10. Línea de aceite turbo", "11. Retorno de aceite turbo")),
            binding("turbo_coolant_lines", 5, 2.8f, -2.1f, -1.4f, alias("forced_induction", "12. Línea de refrigerante turbo")),
            binding("charge_hoses", 4, 1.3f, -2.5f, -1.5f, alias("forced_induction", "14. Mangueras de presión"), alias("intake", "30. Mangueras de carga")),
            binding("boost_sensor", 5, 1.0f, -2.8f, 1.2f, alias("forced_induction", "16. Sensor boost"), alias("sensors", "Sensor boost")),
            binding("blow_off_valve", 6, 1.8f, -2.7f, 1.5f, alias("forced_induction", "18. Válvula blow-off / BOV"), alias("intake", "28. BOV / válvula de descarga")),
            binding("bypass_valve", 6, 2.6f, -2.4f, 1.5f, alias("forced_induction", "19. Válvula bypass"), alias("intake", "29. Válvula bypass turbo")),
            binding("supercharger", 5, -2.0f, -1.8f, 0f, alias("forced_induction", "2. Compresor supercharger")),
            binding("supercharger_pulley", 6, -3.0f, -1.2f, 0f, alias("forced_induction", "20. Polea de supercharger")),
            binding("supercharger_belt", 6, -3.4f, -0.5f, 0f, alias("forced_induction", "21. Correa de supercharger"))
        )
    )

    val transmissionDrivetrain = asset(
        id = "transmission_drivetrain",
        assetFile = "transmission_drivetrain/generic_transmission_drivetrain.glb",
        supportedSystemIds = setOf("transmission"),
        bindings = listOf(
            binding("torque_converter", 1, -3.2f, 0f, 0f, alias("transmission", "Convertidor de par")),
            binding("transmission_oil_pump", 2, -2.5f, 1.4f, 0f, alias("transmission", "Bomba de aceite de transmisión")),
            binding("valve_body", 2, 0f, -2.5f, 0f, alias("transmission", "Cuerpo de válvulas")),
            binding("shift_solenoids", 3, 0f, -3.2f, -1.2f, alias("transmission", "Solenoides de cambio"), alias("actuators", "Solenoides transmisión")),
            binding("tcc_solenoid", 3, 1.0f, -3.2f, -1.2f, alias("transmission", "Solenoide TCC"), alias("actuators", "Solenoide TCC")),
            binding("pressure_solenoid", 3, -1.0f, -3.2f, -1.2f, alias("transmission", "Solenoide de presión lineal")),
            binding("input_shaft", 3, -1.7f, 1.8f, 0f, alias("transmission", "Eje de entrada")),
            binding("output_shaft", 4, 1.7f, 1.8f, 0f, alias("transmission", "Eje de salida")),
            binding("planetary_gearset", 4, 0f, 2.7f, 0f),
            binding("clutch_packs", 4, 0f, 3.3f, 0f),
            binding("transmission_filter", 2, 0f, -3.4f, 0f, alias("transmission", "Filtro ATF")),
            binding("transmission_pan", 1, 0f, -4.0f, 0f, alias("transmission", "Carter de transmisión", "Cárter de transmisión")),
            binding("atf_cooler_lines", 2, 2.0f, -2.7f, 1.2f, alias("transmission", "Líneas de enfriador ATF")),
            binding("atf_temperature_sensor", 3, -1.8f, -2.8f, 1.0f, alias("transmission", "Sensor de temperatura ATF"), alias("sensors", "Sensor temperatura ATF")),
            binding("input_speed_sensor", 4, -1.2f, 2.8f, -1.2f, alias("transmission", "Sensor de velocidad de entrada"), alias("sensors", "Sensor velocidad entrada")),
            binding("output_speed_sensor", 4, 1.2f, 2.8f, -1.2f, alias("transmission", "Sensor de velocidad de salida"), alias("sensors", "Sensor velocidad salida")),
            binding("range_sensor", 3, -2.1f, 2.2f, 1.1f, alias("transmission", "Sensor de rango PRNDL"), alias("sensors", "Sensor rango PRNDL")),
            binding("tcm", 1, -3.0f, 2.5f, 1.2f, alias("transmission", "Módulo TCM"), alias("control_modules", "TCM transmisión")),
            binding("internal_harness", 2, 0f, -2.8f, 1.8f, alias("transmission", "Arnés interno de transmisión")),
            binding("bulkhead_connector", 2, -2.4f, -1.8f, 1.4f, alias("transmission", "Conector pasamuros")),
            binding("differential", 5, 2.0f, 0f, 0f),
            binding("left_axle", 5, -3.0f, 0.2f, 0f, alias("transmission", "Semieje izquierdo")),
            binding("right_axle", 5, 3.0f, 0.2f, 0f, alias("transmission", "Semieje derecho")),
            binding("outer_cv_joints", 6, 4.1f, 0.8f, 0f, alias("transmission", "Junta homocinética externa")),
            binding("inner_cv_joints", 6, 2.8f, 1.2f, 0f, alias("transmission", "Junta homocinética interna")),
            binding("cv_boots", 6, 3.5f, 1.6f, 0f, alias("transmission", "Guardapolvo CV")),
            binding("axle_seals", 6, 2.3f, 2.0f, 0f, alias("transmission", "Retén de semieje"))
        )
    )

    val suspension = asset(
        id = "suspension",
        assetFile = "suspension/generic_suspension.glb",
        supportedSystemIds = setOf("suspension"),
        bindings = listOf(
            binding("front_struts", 1, 0f, 2.8f, 0f, alias("suspension", "Amortiguador delantero")),
            binding("front_coil_springs", 2, 0f, 3.5f, 0f, alias("suspension", "Resorte helicoidal")),
            binding("upper_strut_mounts", 3, 0f, 4.0f, 0f, alias("suspension", "Base superior de amortiguador")),
            binding("strut_bearings", 3, 0f, 4.5f, 0f, alias("suspension", "Rodamiento de copela")),
            binding("bump_stops", 2, 0f, 2.2f, 0f, alias("suspension", "Tope de amortiguador")),
            binding("strut_dust_boots", 2, 0f, 1.6f, 0f, alias("suspension", "Guardapolvo de amortiguador")),
            binding("lower_control_arms", 4, 0f, -2.5f, -1.2f, alias("suspension", "Brazo inferior de suspensión")),
            binding("control_arm_bushings", 5, 0f, -3.2f, -1.2f, alias("suspension", "Bujes de brazo")),
            binding("lower_ball_joints", 5, 0f, -2.8f, 1.1f, alias("suspension", "Rótula inferior")),
            binding("front_stabilizer_bar", 4, 0f, -3.2f, 0f),
            binding("stabilizer_bushings", 5, 0f, -3.8f, 0f, alias("suspension", "Bujes de barra estabilizadora")),
            binding("stabilizer_links", 5, 0f, -3.0f, 1.5f, alias("suspension", "Terminales de barra estabilizadora", "Link kit / bieleta estabilizadora")),
            binding("rear_shocks", 1, 0f, 2.8f, 1.4f, alias("suspension", "Amortiguador trasero")),
            binding("rear_springs", 2, 0f, 3.5f, 1.4f, alias("suspension", "Resorte trasero")),
            binding("torsion_beam", 4, 0f, -2.7f, 1.5f, alias("suspension", "Eje torsional")),
            binding("trailing_arms", 4, 0f, -3.4f, 1.8f, alias("suspension", "Brazos trailing arm")),
            binding("lateral_arms", 4, 0f, -3.9f, 2.0f, alias("suspension", "Brazos laterales")),
            binding("longitudinal_arms", 4, 0f, -4.4f, 2.2f, alias("suspension", "Brazos longitudinales")),
            binding("rear_stabilizer_links", 5, 0f, -3.3f, 2.8f, alias("suspension", "Bieletas traseras")),
            binding("rear_hubs", 6, 0f, -2.2f, 3.0f, alias("suspension", "Cubo trasero")),
            binding("rear_bearings", 6, 0f, -1.5f, 3.0f, alias("suspension", "Rodamiento trasero"))
        )
    )

    val steeringBrakesWheels = asset(
        id = "steering_brakes_wheels",
        assetFile = "steering_brakes_wheels/generic_steering_brakes_wheels.glb",
        supportedSystemIds = setOf("steering", "brakes", "wheels"),
        bindings = listOf(
            binding("steering_wheel", 1, 0f, 3.0f, -1.5f, alias("steering", "Volante")),
            binding("steering_column", 2, 0f, 2.2f, -1.2f),
            binding("steering_u_joint", 2, 0f, 1.5f, -1.0f, alias("steering", "Junta universal de dirección")),
            binding("steering_rack", 3, 0f, -2.2f, 0f, alias("steering", "Cremallera de dirección")),
            binding("rack_pinion", 4, 0f, -2.8f, 0f, alias("steering", "Piñón de cremallera")),
            binding("inner_tie_rods", 4, 0f, -3.1f, -1.0f, alias("steering", "Terminal interno / inner tie rod")),
            binding("outer_tie_rods", 5, 0f, -3.5f, -1.4f, alias("steering", "Terminal externo / outer tie rod")),
            binding("rack_boots", 4, 0f, -2.7f, 1.0f, alias("steering", "Guardapolvos de cremallera")),
            binding("power_steering_pump", 2, -2.7f, 1.6f, 1.2f, alias("steering", "Bomba hidráulica de dirección")),
            binding("power_steering_reservoir", 1, -3.2f, 2.4f, 1.4f, alias("steering", "Depósito de fluido")),
            binding("steering_hoses", 3, -2.7f, 0.6f, 1.8f, alias("steering", "Manguera de alta presión", "Manguera de retorno")),
            binding("brake_pedal", 1, 0f, 3.2f, 1.3f, alias("brakes", "Pedal de freno")),
            binding("brake_booster", 2, 0f, 2.5f, 1.6f, alias("brakes", "Booster de freno")),
            binding("master_cylinder", 2, 0f, 1.8f, 1.8f, alias("brakes", "Bomba maestra")),
            binding("brake_fluid_reservoir", 2, 0f, 2.5f, 2.4f, alias("brakes", "Depósito de líquido de frenos")),
            binding("brake_lines", 3, 0f, 1.0f, 2.5f, alias("brakes", "Líneas rígidas de freno", "Mangueras flexibles")),
            binding("front_discs", 4, 0f, -2.0f, -1.7f, alias("brakes", "Discos delanteros")),
            binding("front_calipers", 5, 0f, -2.8f, -2.0f, alias("brakes", "Calipers delanteros")),
            binding("brake_pads", 6, 0f, -3.5f, -2.2f, alias("brakes", "Pastillas de freno")),
            binding("rear_drum_context", 4, 0f, -2.0f, 1.7f),
            binding("abs_module", 3, 2.6f, 1.6f, 1.5f, alias("brakes", "Módulo ABS"), alias("control_modules", "Módulo ABS")),
            binding("abs_pump", 4, 3.2f, 0.9f, 1.6f, alias("brakes", "Bomba hidráulica ABS"), alias("actuators", "Bomba ABS")),
            binding("abs_solenoids", 5, 3.6f, 0.1f, 1.6f, alias("brakes", "Solenoides ABS"), alias("actuators", "Solenoides ABS")),
            binding("wheels_tires", 1, 0f, 0f, 3.0f),
            binding("wheel_air_valves", 5, 0f, -1.0f, 3.5f, alias("wheels", "Válvula de aire")),
            binding("tpms_sensors", 6, 0f, -1.8f, 3.5f, alias("wheels", "Sensor TPMS"), alias("sensors", "Sensor TPMS")),
            binding("wheel_center_caps", 6, 0f, -2.5f, 3.5f, alias("wheels", "Tapa de centro"))
        )
    )

    val electricalControl = asset(
        id = "electrical_control",
        assetFile = "electrical_control/generic_electrical_control.glb",
        supportedSystemIds = setOf("electrical", "control_modules", "sensors", "actuators"),
        bindings = listOf(
            binding("battery", 1, -3.2f, 2.2f, 0f, alias("electrical", "Batería 12V")),
            binding("positive_terminal", 2, -3.7f, 3.0f, -0.8f, alias("electrical", "Terminal positivo")),
            binding("negative_terminal", 2, -3.7f, 3.0f, 0.8f, alias("electrical", "Terminal negativo")),
            binding("main_positive_cable", 2, -2.8f, 3.4f, -1.2f, alias("electrical", "Cable positivo principal")),
            binding("main_negative_cable", 2, -2.8f, 3.4f, 1.2f, alias("electrical", "Cable negativo principal")),
            binding("engine_ground", 2, -2.2f, 3.8f, 1.6f, alias("electrical", "Cable de tierra motor")),
            binding("chassis_ground", 2, -1.6f, 4.0f, 1.8f, alias("electrical", "Cable de tierra chasis")),
            binding("main_fuse", 3, -1.7f, 3.5f, -1.2f, alias("electrical", "Fusible principal")),
            binding("fusible_link", 3, -1.0f, 3.8f, -1.4f, alias("electrical", "Fusible link")),
            binding("engine_fuse_box", 3, -0.4f, 3.2f, -1.2f, alias("electrical", "Caja de fusibles del motor")),
            binding("interior_fuse_box", 3, 0.5f, 3.2f, -1.2f, alias("electrical", "Caja de fusibles interior")),
            binding("blade_fuses", 4, 0f, 4.0f, -1.8f, alias("electrical", "Fusibles blade")),
            binding("iso_relays", 4, 1.0f, 4.0f, -1.8f, alias("electrical", "Relés ISO")),
            binding("main_relay", 4, 1.8f, 3.5f, -1.2f, alias("electrical", "Relé principal")),
            binding("ignition_relay", 4, 2.5f, 3.2f, -1.0f, alias("electrical", "Relé de ignición")),
            binding("starter_relay", 4, 3.1f, 2.8f, -0.8f, alias("electrical", "Relé de arranque")),
            binding("alternator", 2, -2.8f, -2.1f, 0f, alias("electrical", "Alternador")),
            binding("starter_motor", 2, -1.8f, -2.6f, 0f, alias("electrical", "Motor de arranque"), alias("actuators", "Motor starter")),
            binding("engine_harness", 3, -0.8f, -2.8f, 1.5f, alias("electrical", "Arnés principal de motor")),
            binding("injector_harness", 4, 0f, -3.4f, 1.7f, alias("electrical", "Arnés de inyectores")),
            binding("coil_harness", 4, 0.8f, -3.4f, 1.7f, alias("electrical", "Arnés de bobinas")),
            binding("transmission_harness", 4, 1.6f, -3.1f, 1.6f, alias("electrical", "Arnés de transmisión")),
            binding("abs_harness", 4, 2.4f, -2.8f, 1.5f, alias("electrical", "Arnés de ABS")),
            binding("sensor_harness", 4, 3.0f, -2.2f, 1.5f, alias("electrical", "Arnés de sensores")),
            binding("multipin_connectors", 5, 3.5f, -1.4f, 1.5f, alias("electrical", "Conectores multipin")),
            binding("ecm", 1, -2.4f, 0f, -1.5f, alias("control_modules", "ECU / ECM motor")),
            binding("tcm", 1, -1.3f, 0f, -1.5f, alias("control_modules", "TCM transmisión")),
            binding("abs_controller", 1, -0.2f, 0f, -1.5f, alias("control_modules", "Módulo ABS")),
            binding("ckp_sensor", 5, 0.6f, 1.7f, 1.6f, alias("sensors", "Sensor CKP cigüeñal")),
            binding("cmp_sensor", 5, 1.2f, 1.7f, 1.6f, alias("sensors", "Sensor CMP árbol de levas")),
            binding("maf_sensor", 5, 1.8f, 1.7f, 1.6f, alias("sensors", "Sensor MAF")),
            binding("map_sensor", 5, 2.4f, 1.7f, 1.6f, alias("sensors", "Sensor MAP")),
            binding("ect_sensor", 5, 3.0f, 1.7f, 1.6f, alias("sensors", "Sensor ECT")),
            binding("oxygen_sensors", 5, 3.5f, 1.0f, 1.6f, alias("sensors", "Sensor O2 upstream", "Sensor O2 downstream")),
            binding("knock_sensor", 5, 3.6f, 0.2f, 1.6f, alias("sensors", "Sensor knock")),
            binding("injectors", 6, 0.5f, -1.3f, -1.7f, alias("actuators", "Inyectores")),
            binding("vvt_solenoid", 6, 1.2f, -1.3f, -1.7f, alias("actuators", "Solenoide VVT")),
            binding("evap_purge_solenoid", 6, 1.9f, -1.3f, -1.7f, alias("actuators", "Solenoide EVAP purge")),
            binding("radiator_fan", 6, 2.6f, -1.3f, -1.7f, alias("actuators", "Ventilador radiador")),
            binding("fuel_pump", 6, 3.3f, -1.3f, -1.7f, alias("actuators", "Bomba combustible")),
            binding("transmission_solenoids", 6, 3.7f, -2.0f, -1.7f, alias("actuators", "Solenoides transmisión")),
            binding("abs_pump", 6, 3.7f, -2.8f, -1.7f, alias("actuators", "Bomba ABS"))
        )
    )

    val assets: List<GenericSystemAssetDefinition> = listOf(
        intakeBoost,
        transmissionDrivetrain,
        suspension,
        steeringBrakesWheels,
        electricalControl
    )

    private val assetBySystem = buildMap {
        assets.forEach { definition ->
            definition.supportedSystemIds.forEach { systemId -> put(systemId, definition) }
        }
    }

    fun assetForSystem(systemId: String): GenericSystemAssetDefinition? = assetBySystem[systemId]

    fun meshKeyForNodeName(nodeName: String?): String? {
        if (nodeName == null || !nodeName.startsWith(MESH_NODE_PREFIX)) return null
        return nodeName.removePrefix(MESH_NODE_PREFIX).substringBefore("__").takeIf(String::isNotBlank)
    }

    fun bindingForNodeName(
        asset: GenericSystemAssetDefinition,
        nodeName: String?
    ): GenericSystemAssetBinding? = meshKeyForNodeName(nodeName)?.let { key ->
        asset.bindings.firstOrNull { it.meshKey == key }
    }

    fun bindingForSourceNode(
        asset: GenericSystemAssetDefinition,
        node: UniversalCatalogSceneNode
    ): GenericSystemAssetBinding? = asset.bindings.firstOrNull { binding ->
        binding.sourceAliases.any { alias ->
            alias.systemId == node.systemId && canonical(alias.literalName) == canonical(node.name)
        }
    }

    fun sourceBackedNodes(
        asset: GenericSystemAssetDefinition,
        nodes: List<UniversalCatalogSceneNode>
    ): List<UniversalCatalogSceneNode> {
        val claimedMeshKeys = mutableSetOf<String>()
        return nodes.filter { node ->
            val binding = bindingForSourceNode(asset, node) ?: return@filter false
            binding.isSelectable && claimedMeshKeys.add(binding.meshKey)
        }
    }

    fun placementForNodeName(
        asset: GenericSystemAssetDefinition,
        nodeName: String?,
        placements: List<CatalogSemanticPlacement>
    ): CatalogSemanticPlacement? {
        val binding = bindingForNodeName(asset, nodeName) ?: return null
        if (!binding.isSelectable) return null
        return placements.firstOrNull { placement ->
            placement.occurrence == 0 && bindingForSourceNode(asset, placement.node)?.meshKey == binding.meshKey
        }
    }

    fun isNodeSelected(
        asset: GenericSystemAssetDefinition,
        nodeName: String?,
        placements: List<CatalogSemanticPlacement>,
        selectedEntityId: String?
    ): Boolean {
        if (selectedEntityId == null) return false
        return placementForNodeName(asset, nodeName, placements)?.node?.id == selectedEntityId
    }

    fun serviceOffset(
        asset: GenericSystemAssetDefinition,
        nodeName: String?,
        progress: Float
    ): CatalogServiceOffset {
        val binding = bindingForNodeName(asset, nodeName) ?: return CatalogServiceOffset.ZERO
        val stageStart = (binding.serviceStage - 1f) / 6f
        val localProgress = ((progress.coerceIn(0f, 1f) - stageStart) * 6f).coerceIn(0f, 1f)
        val eased = localProgress * localProgress * (3f - 2f * localProgress)
        return CatalogServiceOffset(
            x = binding.explodedOffset.x * eased * SERVICE_OFFSET_HORIZONTAL_SCALE,
            y = binding.explodedOffset.y * eased * SERVICE_OFFSET_VERTICAL_SCALE,
            z = binding.explodedOffset.z * eased * SERVICE_OFFSET_DEPTH_SCALE
        )
    }

    private fun asset(
        id: String,
        assetFile: String,
        supportedSystemIds: Set<String>,
        bindings: List<GenericSystemAssetBinding>
    ) = GenericSystemAssetDefinition(
        id = id,
        assetPath = "models/vehicle_systems/$assetFile",
        manifestPath = "models/vehicle_systems/${assetFile.substringBeforeLast('/')}/manifest.json",
        supportedSystemIds = supportedSystemIds,
        bindings = bindings
    )

    private fun binding(
        meshKey: String,
        serviceStage: Int,
        offsetX: Float,
        offsetY: Float,
        offsetZ: Float,
        vararg aliases: Set<GenericSystemSourceAlias>
    ) = GenericSystemAssetBinding(
        meshKey = meshKey,
        sourceAliases = aliases.flatMapTo(linkedSetOf()) { it },
        serviceStage = serviceStage,
        explodedOffset = CatalogServiceOffset(offsetX, offsetY, offsetZ)
    )

    private fun alias(systemId: String, vararg literalNames: String): Set<GenericSystemSourceAlias> =
        literalNames.mapTo(linkedSetOf()) { literalName -> GenericSystemSourceAlias(systemId, literalName) }

    private fun canonical(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase()
        .trim()
        .replace("\\s+".toRegex(), " ")
}
