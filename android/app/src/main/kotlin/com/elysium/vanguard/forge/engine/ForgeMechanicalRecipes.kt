package com.elysium.vanguard.forge.engine

import com.elysium.vanguard.forge.domain.BoltPattern
import com.elysium.vanguard.forge.domain.ConnectionPort
import com.elysium.vanguard.forge.domain.ConnectionPortType
import com.elysium.vanguard.forge.domain.DimensionSet
import com.elysium.vanguard.forge.domain.FeatureOperation
import com.elysium.vanguard.forge.domain.FeatureType
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.JointType
import com.elysium.vanguard.forge.domain.ManufacturingProcess
import com.elysium.vanguard.forge.domain.MaterialSpec
import com.elysium.vanguard.forge.domain.ParametricFeature
import com.elysium.vanguard.forge.domain.ProcedureDifficulty
import com.elysium.vanguard.forge.domain.ProcedureStep
import com.elysium.vanguard.forge.domain.ReplacementProcedure
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.domain.ToolRequirement
import com.elysium.vanguard.forge.domain.TorqueSpec
import com.elysium.vanguard.forge.domain.Vector3Data
import kotlin.math.atan2
import kotlin.math.hypot

object ForgeMechanicalRecipes {
    const val EDUCATIONAL_SAFETY_WARNING =
        "Modelo educativo paramétrico. No es dato OEM, no es pieza certificada y no debe usarse en carretera sin ingeniería, material certificado, pruebas físicas y homologación."

    val seedMaterials: List<MaterialSpec> = listOf(
        MaterialSpec(
            id = "cast_iron_gray",
            displayName = "Gray Cast Iron",
            category = "ferrous",
            densityKgM3 = 7200.0,
            youngModulusGPa = 110.0,
            yieldStrengthMPa = 160.0,
            tensileStrengthMPa = 240.0,
            thermalExpansion = 10.8,
            thermalConductivity = 52.0,
            maxOperatingTempC = 500.0,
            corrosionResistance = "media",
            fatigueResistance = "media",
            manufacturability = "excelente para fundición y mecanizado",
            costLevel = 2,
            compatibleProcesses = listOf("sand_casting", "cnc_machining", "balancing")
        ),
        MaterialSpec(
            id = "high_strength_steel",
            displayName = "High Strength Steel",
            category = "ferrous",
            densityKgM3 = 7850.0,
            youngModulusGPa = 200.0,
            yieldStrengthMPa = 420.0,
            tensileStrengthMPa = 620.0,
            thermalExpansion = 12.0,
            thermalConductivity = 45.0,
            maxOperatingTempC = 420.0,
            corrosionResistance = "requiere recubrimiento",
            fatigueResistance = "alta si se fabrica e inspecciona correctamente",
            manufacturability = "estampado, soldadura, mecanizado local",
            costLevel = 3,
            compatibleProcesses = listOf("stamping", "spot_welding", "cnc_machining", "powder_coating")
        )
    )

    val seedProcesses: List<ManufacturingProcess> = listOf(
        ManufacturingProcess(
            id = "sand_casting",
            displayName = "Sand Casting",
            category = "casting",
            description = "Fundición educativa para geometrías robustas como discos de freno.",
            compatibleMaterials = listOf("cast_iron_gray"),
            machines = listOf("molde de arena", "horno", "equipo de desmoldeo"),
            steps = listOf("preparar molde", "colar metal", "enfriar", "desbarbar"),
            commonDefects = listOf("porosidad", "inclusiones", "deformación"),
            qualityControls = listOf("inspección visual", "runout", "balanceo"),
            risks = listOf("alta temperatura", "humos", "material fundido"),
            costLevel = 2,
            typicalPrecisionMm = 1.5
        ),
        ManufacturingProcess(
            id = "cnc_machining",
            displayName = "CNC Machining",
            category = "subtractive",
            description = "Mecanizado de caras funcionales, agujeros, bore y tolerancias finales.",
            compatibleMaterials = listOf("cast_iron_gray", "high_strength_steel"),
            machines = listOf("torno CNC", "centro CNC", "taladro"),
            steps = listOf("referenciar pieza", "desbaste", "acabado", "medición"),
            commonDefects = listOf("vibración", "runout", "rebaba"),
            qualityControls = listOf("CMM", "micrómetro", "rugosidad"),
            risks = listOf("viruta", "sujeción incorrecta"),
            costLevel = 3,
            typicalPrecisionMm = 0.05
        ),
        ManufacturingProcess(
            id = "stamping",
            displayName = "Stamping",
            category = "forming",
            description = "Estampado de chapa para brazos y soportes estructurales.",
            compatibleMaterials = listOf("high_strength_steel"),
            machines = listOf("prensa", "troquel", "alimentador"),
            steps = listOf("cortar blank", "formar", "recortar", "inspeccionar"),
            commonDefects = listOf("grietas", "springback", "arrugas"),
            qualityControls = listOf("plantilla", "CMM", "inspección de fisuras"),
            risks = listOf("atrapamiento", "rebabas", "energía de prensa"),
            costLevel = 4,
            typicalPrecisionMm = 0.3
        ),
        ManufacturingProcess(
            id = "spot_welding",
            displayName = "Spot Welding",
            category = "joining",
            description = "Soldadura por puntos para refuerzos y alojamientos.",
            compatibleMaterials = listOf("high_strength_steel"),
            machines = listOf("soldadora por puntos", "fixture"),
            steps = listOf("alinear", "soldar", "enfriar", "prueba de punto"),
            commonDefects = listOf("punto frío", "expulsión", "desalineación"),
            qualityControls = listOf("prueba destructiva", "inspección visual"),
            risks = listOf("quemaduras", "humos", "corriente alta"),
            costLevel = 3,
            typicalPrecisionMm = 0.5
        ),
        ManufacturingProcess(
            id = "balancing",
            displayName = "Dynamic Balancing",
            category = "quality",
            description = "Balanceo dinámico educativo para piezas rotativas.",
            compatibleMaterials = listOf("cast_iron_gray"),
            machines = listOf("balanceadora"),
            steps = listOf("montar", "medir desbalance", "corregir", "validar"),
            commonDefects = listOf("vibración", "masa mal distribuida"),
            qualityControls = listOf("registro de desbalance residual"),
            risks = listOf("rotación", "sujeción deficiente"),
            costLevel = 2,
            typicalPrecisionMm = 0.1
        )
    )

    fun brakeDisc(
        now: Long = System.currentTimeMillis(),
        outerDiameterMm: Double = 280.0,
        thicknessMm: Double = 24.0,
        centerBoreMm: Double = 64.1,
        boltCount: Int = 5,
        boltCircleDiameterMm: Double = 114.3,
        boltHoleDiameterMm: Double = 12.5
    ): ForgePart {
        val id = "forge_brake_disc_${outerDiameterMm.toInt()}_${thicknessMm.toInt()}"
        return ForgePart(
            artifact = ForgeArtifact(
                id = id,
                name = "Disco de freno paramétrico ${outerDiameterMm.toInt()}x${thicknessMm.toInt()}",
                description = "Disco de freno educativo desde documento paramétrico editable: diámetro, espesor, centro y patrón de pernos.",
                artifactType = ForgeArtifactType.PART,
                createdAt = now,
                updatedAt = now,
                safetyClassification = SafetyClassification.SAFETY_CRITICAL_UNCERTIFIED,
                tags = listOf("forge", "brake_disc", "parametric", "safety_critical")
            ),
            dimensions = DimensionSet(
                diameterMm = outerDiameterMm,
                outerDiameterMm = outerDiameterMm,
                innerDiameterMm = centerBoreMm,
                thicknessMm = thicknessMm,
                customDimensions = mapOf(
                    "boltCircleDiameterMm" to boltCircleDiameterMm,
                    "boltHoleDiameterMm" to boltHoleDiameterMm,
                    "boltCount" to boltCount.toDouble()
                )
            ),
            materialId = "cast_iron_gray",
            manufacturingProcessIds = listOf("sand_casting", "cnc_machining", "balancing"),
            featureTree = listOf(
                ParametricFeature(
                    id = "disc_annular_body",
                    type = FeatureType.TUBE,
                    name = "Cuerpo anular con bore central",
                    parameters = mapOf(
                        "outerDiameterMm" to outerDiameterMm,
                        "innerDiameterMm" to centerBoreMm,
                        "heightMm" to thicknessMm
                    )
                ),
                ParametricFeature(
                    id = "bolt_pattern",
                    type = FeatureType.CIRCULAR_PATTERN,
                    name = "Patrón de pernos $boltCount x $boltCircleDiameterMm",
                    parameters = mapOf(
                        "count" to boltCount.toDouble(),
                        "boltCircleDiameterMm" to boltCircleDiameterMm,
                        "holeDiameterMm" to boltHoleDiameterMm,
                        "depthMm" to thicknessMm
                    ),
                    operation = FeatureOperation.PATTERN
                )
            ),
            connectionPorts = listOf(
                ConnectionPort(
                    id = "center_bore",
                    name = "Center bore",
                    portType = ConnectionPortType.BEARING_SEAT,
                    diameterMm = centerBoreMm,
                    compatibleJointTypes = listOf(JointType.CONCENTRIC, JointType.BEARING)
                ),
                ConnectionPort(
                    id = "wheel_bolt_pattern",
                    name = "Wheel bolt pattern",
                    portType = ConnectionPortType.BOLT_HOLE,
                    boltPattern = BoltPattern(boltCount, boltCircleDiameterMm, boltHoleDiameterMm),
                    compatibleJointTypes = listOf(JointType.BOLTED),
                    torqueLimitNm = 110.0
                )
            ),
            replacementProcedures = listOf(brakeDiscProcedure(id))
        )
    }

    fun lowerControlArm(
        now: Long = System.currentTimeMillis(),
        vehicleContext: String = "Hyundai Accent/Verna 2005"
    ): ForgePart {
        val id = "forge_lower_control_arm_${now}"
        val frontBushing = Vector3Data(-150.0, -58.0, 0.0)
        val rearBushing = Vector3Data(-126.0, 68.0, 0.0)
        val ballJoint = Vector3Data(178.0, 0.0, 0.0)
        val features = listOf(
            beam("front_leg_u_profile", "Brazo U delantero", frontBushing, ballJoint, 48.0, 38.0, 3.2),
            beam("rear_leg_u_profile", "Brazo U trasero", rearBushing, ballJoint, 54.0, 40.0, 3.2),
            beam("bushing_cross_member", "Puente de bujes", frontBushing, rearBushing, 42.0, 34.0, 3.2),
            tube("front_bushing_tube", "Alojamiento buje delantero", frontBushing, 48.0, 22.0, 54.0),
            tube("rear_bushing_tube", "Alojamiento buje trasero", rearBushing, 52.0, 24.0, 58.0),
            cylinder("ball_joint_cup", "Copa de rótula inferior", ballJoint, 58.0, 32.0)
        )
        return ForgePart(
            artifact = ForgeArtifact(
                id = id,
                name = "Tijereta paramétrica $vehicleContext",
                description = "Control arm educativo generado como artefacto paramétrico: brazos U, bujes, copa de rótula, puertos y manual base.",
                artifactType = ForgeArtifactType.PART,
                createdAt = now,
                updatedAt = now,
                safetyClassification = SafetyClassification.SAFETY_CRITICAL_UNCERTIFIED,
                tags = listOf("forge", "ai_generated", "suspension", "control_arm", "tijereta", "lower_control_arm")
            ),
            dimensions = DimensionSet(
                lengthMm = 390.0,
                widthMm = 175.0,
                heightMm = 58.0,
                thicknessMm = 3.2,
                toleranceMm = 1.0,
                customDimensions = mapOf("bushingSpacingMm" to 128.3, "ballJointOffsetMm" to 178.0)
            ),
            materialId = "high_strength_steel",
            manufacturingProcessIds = listOf("stamping", "spot_welding", "cnc_machining"),
            featureTree = features,
            connectionPorts = listOf(
                ConnectionPort("front_bushing_port", "Buje delantero", ConnectionPortType.BUSHING, frontBushing, Vector3Data.UNIT_Y, diameterMm = 22.0, compatibleJointTypes = listOf(JointType.BUSHING, JointType.BOLTED, JointType.PIN), loadRatingN = 4500.0, torqueLimitNm = 95.0),
                ConnectionPort("rear_bushing_port", "Buje trasero", ConnectionPortType.BUSHING, rearBushing, Vector3Data.UNIT_Y, diameterMm = 24.0, compatibleJointTypes = listOf(JointType.BUSHING, JointType.BOLTED, JointType.PIN), loadRatingN = 4500.0, torqueLimitNm = 95.0),
                ConnectionPort("ball_joint_port", "Rótula inferior", ConnectionPortType.HINGE_AXIS, ballJoint, Vector3Data.UNIT_Z, diameterMm = 18.0, compatibleJointTypes = listOf(JointType.HINGE, JointType.PIN, JointType.BOLTED), loadRatingN = 5200.0, torqueLimitNm = 70.0)
            ),
            replacementProcedures = listOf(controlArmProcedure(id, vehicleContext))
        )
    }

    fun editBrakeDisc(part: ForgePart, outerDiameterMm: Double, thicknessMm: Double, now: Long = System.currentTimeMillis()): ForgePart {
        val boltCount = part.dimensions.customDimensions["boltCount"]?.toInt() ?: 5
        val bcd = part.dimensions.customDimensions["boltCircleDiameterMm"] ?: 114.3
        val boltHole = part.dimensions.customDimensions["boltHoleDiameterMm"] ?: 12.5
        val center = part.dimensions.innerDiameterMm ?: 64.1
        val edited = brakeDisc(now, outerDiameterMm, thicknessMm, center, boltCount, bcd, boltHole)
        return edited.copy(
            artifact = edited.artifact.copy(
                id = part.artifact.id,
                name = part.artifact.name,
                createdAt = part.artifact.createdAt,
                updatedAt = now,
                version = part.artifact.version + 1
            ),
            materialId = part.materialId,
            manufacturingProcessIds = part.manufacturingProcessIds
        )
    }

    private fun beam(id: String, name: String, start: Vector3Data, end: Vector3Data, width: Double, height: Double, thickness: Double): ParametricFeature {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = hypot(dx, dy)
        val angle = Math.toDegrees(atan2(dy, dx))
        return ParametricFeature(
            id = id,
            type = FeatureType.PROFILE_U,
            name = name,
            parameters = mapOf("lengthMm" to length, "widthMm" to width, "heightMm" to height, "thicknessMm" to thickness),
            position = Vector3Data((start.x + end.x) / 2.0, (start.y + end.y) / 2.0, 0.0),
            rotation = Vector3Data(0.0, 0.0, angle)
        )
    }

    private fun tube(id: String, name: String, position: Vector3Data, outer: Double, inner: Double, height: Double): ParametricFeature =
        ParametricFeature(
            id = id,
            type = FeatureType.TUBE,
            name = name,
            parameters = mapOf("outerDiameterMm" to outer, "innerDiameterMm" to inner, "heightMm" to height),
            position = position
        )

    private fun cylinder(id: String, name: String, position: Vector3Data, diameter: Double, height: Double): ParametricFeature =
        ParametricFeature(
            id = id,
            type = FeatureType.CYLINDER,
            name = name,
            parameters = mapOf("diameterMm" to diameter, "heightMm" to height),
            position = position
        )

    private fun brakeDiscProcedure(partId: String): ReplacementProcedure =
        ReplacementProcedure(
            id = "replace_$partId",
            title = "Reemplazo educativo de disco de freno",
            partId = partId,
            difficulty = ProcedureDifficulty.HARD,
            estimatedTimeMinutes = 70,
            requiredTools = listOf(
                ToolRequirement("jack_stands", "Torres de seguridad", purpose = "Soportar el vehículo", safetyNote = "Nunca trabajar solo con gato hidráulico."),
                ToolRequirement("torque_wrench", "Torquímetro", "Nm", "Aplicar torque real de manual OEM")
            ),
            safetyWarnings = listOf(EDUCATIONAL_SAFETY_WARNING),
            steps = listOf(
                ProcedureStep(1, "Asegurar vehículo", "Elevar, apoyar en torres y retirar rueda.", warnings = listOf(EDUCATIONAL_SAFETY_WARNING)),
                ProcedureStep(2, "Retirar caliper", "Retirar caliper y soporte sin tensionar manguera."),
                ProcedureStep(3, "Retirar disco", "Retirar disco, limpiar maza y verificar runout."),
                ProcedureStep(4, "Instalar disco", "Instalar disco y torquear según manual OEM."),
                ProcedureStep(5, "Validar", "Bombear pedal, probar a baja velocidad y verificar vibración.")
            ),
            torqueSpecs = listOf(TorqueSpec("Tuercas de rueda", 110.0, note = "Referencia educativa; usar manual real."))
        )

    private fun controlArmProcedure(partId: String, vehicle: String): ReplacementProcedure =
        ReplacementProcedure(
            id = "replace_$partId",
            title = "Reemplazo educativo de tijereta $vehicle",
            partId = partId,
            difficulty = ProcedureDifficulty.EXPERT_ONLY,
            estimatedTimeMinutes = 100,
            requiredTools = listOf(
                ToolRequirement("jack_stands", "Torres de seguridad", purpose = "Soportar el vehículo", safetyNote = "Nunca trabajar solo con gato hidráulico."),
                ToolRequirement("ball_joint_separator", "Extractor de rótula", purpose = "Separar rótula sin destruir componentes"),
                ToolRequirement("torque_wrench", "Torquímetro", "Nm", "Aplicar torque real")
            ),
            safetyWarnings = listOf(EDUCATIONAL_SAFETY_WARNING),
            steps = listOf(
                ProcedureStep(1, "Asegurar vehículo", "Elevar y apoyar correctamente.", warnings = listOf(EDUCATIONAL_SAFETY_WARNING)),
                ProcedureStep(2, "Separar rótula", "Usar extractor; no golpear mangueras ni sensor ABS."),
                ProcedureStep(3, "Retirar pernos de buje", "Marcar orientación y retirar soportes."),
                ProcedureStep(4, "Instalar tijereta", "Instalar y preapretar sin cargar bujes."),
                ProcedureStep(5, "Torque bajo carga", "Aplicar torque con suspensión en altura de trabajo según manual OEM."),
                ProcedureStep(6, "Alinear", "Alineación profesional obligatoria.")
            ),
            torqueSpecs = listOf(
                TorqueSpec("Pernos de buje", 95.0, 1, "Referencia educativa; usar manual real."),
                TorqueSpec("Tuerca de rótula", 70.0, 2, "Referencia educativa; usar manual real.")
            )
        )
}
