package com.elysium369.meet.core.services

import java.util.Locale

enum class EvidenceType {
    BEFORE_PHOTO,
    AFTER_PHOTO,
    OBD_SNAPSHOT,
    FREEZE_FRAME,
    SENSOR_GRAPH,
    MULTIMETER_READING,
    PART_REPLACED,
    RECEIPT,
    CUSTOMER_SIGNATURE,
    PROVIDER_NOTE,
    TEST_DRIVE_RESULT,
    PDF_REPORT,
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class ServiceCategory(
    val displayName: String,
    val defaultTools: List<String>,
    val defaultEvidence: List<EvidenceType>,
    val defaultDurationMin: Int,
    val basePriceMinCrc: Int,
    val basePriceMaxCrc: Int,
    val defaultRisk: RiskLevel,
    val requiresObdByDefault: Boolean,
    val supportsRemoteByDefault: Boolean,
    val supportsMobileByDefault: Boolean,
) {
    DIAGNOSTIC(
        displayName = "Diagnostico",
        defaultTools = listOf("OBD-II scanner", "multimetro", "linterna tecnica"),
        defaultEvidence = listOf(EvidenceType.OBD_SNAPSHOT, EvidenceType.FREEZE_FRAME, EvidenceType.PROVIDER_NOTE),
        defaultDurationMin = 45,
        basePriceMinCrc = 15000,
        basePriceMaxCrc = 45000,
        defaultRisk = RiskLevel.MEDIUM,
        requiresObdByDefault = true,
        supportsRemoteByDefault = true,
        supportsMobileByDefault = true,
    ),
    PREVENTIVE_MAINTENANCE(
        displayName = "Mantenimiento preventivo",
        defaultTools = listOf("herramienta manual", "torquimetro", "scanner basico"),
        defaultEvidence = listOf(EvidenceType.BEFORE_PHOTO, EvidenceType.RECEIPT, EvidenceType.PROVIDER_NOTE),
        defaultDurationMin = 75,
        basePriceMinCrc = 18000,
        basePriceMaxCrc = 65000,
        defaultRisk = RiskLevel.LOW,
        requiresObdByDefault = false,
        supportsRemoteByDefault = false,
        supportsMobileByDefault = true,
    ),
    ENGINE(
        displayName = "Motor",
        defaultTools = listOf("OBD-II scanner", "manometro", "multimetro", "herramienta mecanica"),
        defaultEvidence = listOf(EvidenceType.OBD_SNAPSHOT, EvidenceType.BEFORE_PHOTO, EvidenceType.SENSOR_GRAPH, EvidenceType.PROVIDER_NOTE),
        defaultDurationMin = 90,
        basePriceMinCrc = 25000,
        basePriceMaxCrc = 95000,
        defaultRisk = RiskLevel.HIGH,
        requiresObdByDefault = true,
        supportsRemoteByDefault = false,
        supportsMobileByDefault = true,
    ),
    ELECTRICAL_ELECTRONIC(
        displayName = "Electrico/electronico",
        defaultTools = listOf("multimetro", "pinza amperimetrica", "OBD-II scanner", "probador de fusibles"),
        defaultEvidence = listOf(EvidenceType.MULTIMETER_READING, EvidenceType.OBD_SNAPSHOT, EvidenceType.BEFORE_PHOTO, EvidenceType.PROVIDER_NOTE),
        defaultDurationMin = 90,
        basePriceMinCrc = 22000,
        basePriceMaxCrc = 90000,
        defaultRisk = RiskLevel.HIGH,
        requiresObdByDefault = true,
        supportsRemoteByDefault = true,
        supportsMobileByDefault = true,
    ),
    BRAKES_SUSPENSION_STEERING(
        displayName = "Frenos/suspension/direccion",
        defaultTools = listOf("elevador o gata segura", "herramienta mecanica", "scanner ABS si aplica"),
        defaultEvidence = listOf(EvidenceType.BEFORE_PHOTO, EvidenceType.AFTER_PHOTO, EvidenceType.TEST_DRIVE_RESULT, EvidenceType.PROVIDER_NOTE),
        defaultDurationMin = 90,
        basePriceMinCrc = 25000,
        basePriceMaxCrc = 110000,
        defaultRisk = RiskLevel.HIGH,
        requiresObdByDefault = false,
        supportsRemoteByDefault = false,
        supportsMobileByDefault = true,
    ),
    TRANSMISSION(
        displayName = "Transmision",
        defaultTools = listOf("OBD-II scanner con TCM", "medidor ATF", "herramienta mecanica"),
        defaultEvidence = listOf(EvidenceType.OBD_SNAPSHOT, EvidenceType.SENSOR_GRAPH, EvidenceType.TEST_DRIVE_RESULT, EvidenceType.PROVIDER_NOTE),
        defaultDurationMin = 100,
        basePriceMinCrc = 30000,
        basePriceMaxCrc = 130000,
        defaultRisk = RiskLevel.HIGH,
        requiresObdByDefault = true,
        supportsRemoteByDefault = false,
        supportsMobileByDefault = false,
    ),
    AIR_CONDITIONING(
        displayName = "Aire acondicionado",
        defaultTools = listOf("manometros A/C", "detector de fugas", "termometro", "herramienta A/C"),
        defaultEvidence = listOf(EvidenceType.BEFORE_PHOTO, EvidenceType.MULTIMETER_READING, EvidenceType.PROVIDER_NOTE),
        defaultDurationMin = 80,
        basePriceMinCrc = 25000,
        basePriceMaxCrc = 100000,
        defaultRisk = RiskLevel.MEDIUM,
        requiresObdByDefault = false,
        supportsRemoteByDefault = false,
        supportsMobileByDefault = true,
    ),
    PREPURCHASE_INSPECTION(
        displayName = "Precompra/peritaje",
        defaultTools = listOf("OBD-II scanner", "medidor pintura si disponible", "linterna tecnica", "checklist peritaje"),
        defaultEvidence = listOf(EvidenceType.OBD_SNAPSHOT, EvidenceType.BEFORE_PHOTO, EvidenceType.TEST_DRIVE_RESULT, EvidenceType.PDF_REPORT),
        defaultDurationMin = 120,
        basePriceMinCrc = 45000,
        basePriceMaxCrc = 180000,
        defaultRisk = RiskLevel.MEDIUM,
        requiresObdByDefault = true,
        supportsRemoteByDefault = false,
        supportsMobileByDefault = true,
    ),
    EMERGENCY_ASSISTANCE(
        displayName = "Emergencia/asistencia",
        defaultTools = listOf("booster bateria", "herramienta movil", "OBD-II scanner", "equipo seguridad vial"),
        defaultEvidence = listOf(EvidenceType.BEFORE_PHOTO, EvidenceType.PROVIDER_NOTE, EvidenceType.TEST_DRIVE_RESULT),
        defaultDurationMin = 45,
        basePriceMinCrc = 25000,
        basePriceMaxCrc = 120000,
        defaultRisk = RiskLevel.CRITICAL,
        requiresObdByDefault = false,
        supportsRemoteByDefault = false,
        supportsMobileByDefault = true,
    ),
    PARTS(
        displayName = "Repuestos",
        defaultTools = listOf("catalogo VIN", "verificador compatibilidad", "canal entrega"),
        defaultEvidence = listOf(EvidenceType.RECEIPT, EvidenceType.PROVIDER_NOTE),
        defaultDurationMin = 30,
        basePriceMinCrc = 0,
        basePriceMaxCrc = 0,
        defaultRisk = RiskLevel.MEDIUM,
        requiresObdByDefault = false,
        supportsRemoteByDefault = true,
        supportsMobileByDefault = true,
    ),
}

data class ServiceDefinition(
    val id: String,
    val category: ServiceCategory,
    val name: String,
    val description: String,
    val requiredTools: List<String>,
    val requiredEvidence: List<EvidenceType>,
    val estimatedDurationMin: Int,
    val basePriceMinCrc: Int,
    val basePriceMaxCrc: Int,
    val riskLevel: RiskLevel,
    val requiresVehicleOn: Boolean,
    val requiresObd: Boolean,
    val requiresPhysicalPresence: Boolean,
    val supportsRemote: Boolean,
    val supportsMobileService: Boolean,
    val supportedVehicleTypes: List<String>,
    val supportedFuelTypes: List<String>,
    val relatedDtcs: List<String>,
    val relatedPids: List<String>,
    val enabled: Boolean = true,
)

data class ProviderRoleDefinition(
    val id: String,
    val displayName: String,
    val requiredFields: List<String>,
    val requiredCapabilities: List<String>,
    val compatibleCategories: List<ServiceCategory>,
    val requiresCertification: Boolean = false,
)

data class ServicePackageDefinition(
    val id: String,
    val name: String,
    val includedTasks: List<String>,
    val recommendedForDtcs: List<String> = emptyList(),
)

object WorkshopServiceCatalog {
    private val commonVehicleTypes = listOf("sedan", "suv", "pickup", "van")
    private val commonFuelTypes = listOf("gasolina", "diesel", "hibrido", "electrico")

    val providerRoles: List<ProviderRoleDefinition> = listOf(
        role("MECHANIC_GENERAL", "Mecanico general", "anios_experiencia", "herramientas_basicas", categories = allRepairCategories()),
        role("ELECTRICAL_SPECIALIST", "Mecanico electrico/electronico", "multimetro", "scanner", "diagramas", categories = listOf(ServiceCategory.ELECTRICAL_ELECTRONIC, ServiceCategory.DIAGNOSTIC)),
        role("OBD_DIAGNOSTIC_SPECIALIST", "Especialista OBD-II / diagnostico", "scanner_obd", "protocolos_soportados", categories = listOf(ServiceCategory.DIAGNOSTIC, ServiceCategory.ENGINE, ServiceCategory.TRANSMISSION)),
        role("AC_TECH", "Tecnico aire acondicionado automotriz", "equipo_ac", "certificacion_refrigerante", categories = listOf(ServiceCategory.AIR_CONDITIONING), requiresCertification = true),
        role("TRANSMISSION_TECH", "Tecnico transmision automatica/manual", "herramientas_transmision", "experiencia_cajas", categories = listOf(ServiceCategory.TRANSMISSION)),
        role("FUEL_INJECTION_TECH", "Tecnico inyeccion/combustible", "manometro_combustible", "scanner", categories = listOf(ServiceCategory.ENGINE, ServiceCategory.DIAGNOSTIC)),
        role("BRAKE_SUSPENSION_STEERING_TECH", "Tecnico frenos/suspension/direccion", "elevador_o_gata", "herramienta_frenos", categories = listOf(ServiceCategory.BRAKES_SUSPENSION_STEERING)),
        role("HYBRID_EV_TECH", "Tecnico hibridos/EV", "certificacion_alto_voltaje", "EPP_alto_voltaje", categories = listOf(ServiceCategory.ELECTRICAL_ELECTRONIC, ServiceCategory.DIAGNOSTIC), requiresCertification = true),
        role("DIESEL_TECH", "Tecnico diesel", "herramienta_diesel", "scanner_diesel", categories = listOf(ServiceCategory.ENGINE, ServiceCategory.DIAGNOSTIC)),
        role("MOBILE_MECHANIC", "Mecanico movil a domicilio", "radio_cobertura", "vehiculo_servicio", categories = allRepairCategories()),
        role("PHYSICAL_WORKSHOP", "Taller fisico", "direccion_taller", "horario", "capacidad_bahias", categories = allRepairCategories()),
        role("TOW_TRUCK", "Gruista/asistencia vial", "radio_cobertura", "tipo_grua", "capacidad", "disponibilidad_24_7", categories = listOf(ServiceCategory.EMERGENCY_ASSISTANCE)),
        role("PARTS_STORE", "Repuestera/autopartes", "catalogo", "horarios", "zonas_entrega", categories = listOf(ServiceCategory.PARTS)),
        role("PREPURCHASE_INSPECTOR", "Perito/inspector precompra", "plantilla_inspeccion", "firma_digital", categories = listOf(ServiceCategory.PREPURCHASE_INSPECTION), requiresCertification = true),
        role("ACCESSORY_INSTALLER", "Instalador accesorios/alarma/audio/GPS", "portafolio_instalaciones", "herramienta_electrica", categories = listOf(ServiceCategory.ELECTRICAL_ELECTRONIC)),
        role("MOTORCYCLE_TECH", "Tecnico motos", "herramienta_motos", "marcas_soportadas", categories = allRepairCategories()),
        role("MACHINERY_GENERATOR_TECH", "Tecnico maquinaria/generadores", "equipo_diagnostico_maquinaria", "transporte_equipo", categories = listOf(ServiceCategory.ENGINE, ServiceCategory.ELECTRICAL_ELECTRONIC, ServiceCategory.DIAGNOSTIC)),
    )

    val servicePackages: List<ServicePackageDefinition> = listOf(
        servicePackage("diagnostic_express", "Diagnostico Express", "lectura DTC", "freeze frame", "revision visual basica", "recomendacion inicial"),
        servicePackage("diagnostic_pro", "Diagnostico Pro", "DTC", "freeze frame", "sensores live", "prueba dirigida", "reporte PDF", "estimacion reparacion"),
        servicePackage("prepurchase_basic", "Precompra Basica", "revision visual", "DTCs", "prueba carretera", "reporte corto"),
        servicePackage("prepurchase_elite", "Precompra Elite", "DTCs", "live data", "revision motor/caja/suspension", "fotos", "firma", "score final", "PDF certificado"),
        servicePackage("emergency_no_start", "Emergencia No Arranca", "bateria", "arranque", "fusibles", "combustible", "inmovilizador basico"),
        servicePackage("remote_check_engine", "Check Engine Remoto", "Live Link", "lectura DTC", "telemetria", "videollamada opcional", "conclusion remota"),
        servicePackage("maintenance_100k", "Mantenimiento 100K", "aceite", "filtros", "bujias", "refrigerante", "frenos", "scanner"),
    )

    val services: List<ServiceDefinition> = p0230Services() + listOf(
        ServiceCategory.DIAGNOSTIC to listOf(
            "Diagnostico OBD-II basico",
            "Diagnostico avanzado con freeze frame",
            "Diagnostico electrico",
            "Diagnostico de sensores",
            "Diagnostico de fallas intermitentes",
            "Diagnostico de no arranque",
            "Diagnostico de check engine",
            "Diagnostico remoto Live Link",
            "Segunda opinion tecnica",
            "Revision de codigos DTC antes de compra",
        ),
        ServiceCategory.PREVENTIVE_MAINTENANCE to listOf(
            "Cambio de aceite",
            "Cambio de filtros",
            "Cambio de bujias",
            "Limpieza cuerpo de aceleracion",
            "Limpieza MAF/MAP",
            "Limpieza inyectores",
            "Cambio refrigerante",
            "Cambio liquido de frenos",
            "Cambio ATF",
            "Servicio de faja/correa",
            "Revision pre-viaje",
        ),
        ServiceCategory.ENGINE to listOf(
            "Revision compresion",
            "Revision fugas",
            "Revision calentamiento",
            "Revision consumo aceite",
            "Revision perdida potencia",
            "Revision mezcla rica/pobre",
            "Revision sensores motor",
            "Revision bomba combustible",
            "Revision bobinas/bujias",
            "Revision sistema admision/escape",
        ),
        ServiceCategory.ELECTRICAL_ELECTRONIC to listOf(
            "Revision alternador",
            "Revision bateria",
            "Revision arranque",
            "Revision tierras/masas",
            "Revision fusibles/reles",
            "Revision arnes",
            "Revision modulos ECU/TCM/ABS",
            "Revision consumo parasitario",
            "Reparacion conectores",
            "Revision CAN bus",
            "Programacion/adaptacion basica, solo si el vehiculo lo soporta",
        ),
        ServiceCategory.BRAKES_SUSPENSION_STEERING to listOf(
            "Cambio pastillas",
            "Cambio discos",
            "Purga frenos",
            "Revision ABS",
            "Revision amortiguadores",
            "Revision rotulas",
            "Revision terminales",
            "Revision cremallera",
            "Alineacion",
            "Balanceo",
            "Revision vibraciones",
        ),
        ServiceCategory.TRANSMISSION to listOf(
            "Diagnostico caja automatica",
            "Revision nivel ATF",
            "Cambio ATF",
            "Revision solenoides",
            "Revision patinamiento",
            "Revision golpes de cambio",
            "Revision sensores velocidad",
            "Adaptacion TCM, si aplica",
        ),
        ServiceCategory.AIR_CONDITIONING to listOf(
            "Diagnostico A/C",
            "Carga refrigerante",
            "Prueba fugas",
            "Revision compresor",
            "Revision abanicos",
            "Revision evaporador/condensador",
            "Revision presostato",
        ),
        ServiceCategory.PREPURCHASE_INSPECTION to listOf(
            "Inspeccion visual",
            "Escaneo OBD",
            "Revision chasis",
            "Revision motor",
            "Revision caja",
            "Revision suspension",
            "Revision fugas",
            "Revision historial DTC",
            "Reporte PDF certificado",
            "Firma del inspector",
        ),
        ServiceCategory.EMERGENCY_ASSISTANCE to listOf(
            "No arranca",
            "Bateria descargada",
            "Llanta pinchada",
            "Sobrecalentamiento",
            "Fuga visible",
            "Check engine critico",
            "Grua",
            "Asistencia en carretera",
            "Mecanico movil express",
        ),
        ServiceCategory.PARTS to listOf(
            "Cotizacion de pieza",
            "Busqueda por VIN",
            "Original/OEM/aftermarket",
            "Entrega local",
            "Confirmacion compatibilidad",
            "Garantia",
        ),
    ).flatMap { (category, names) ->
        names.map { service(category, it) }
    }

    fun categories(): List<ServiceCategory> = ServiceCategory.values().toList()

    fun enabledServicesForCategory(category: ServiceCategory): List<ServiceDefinition> =
        services.filter { it.enabled && it.category == category }

    fun serviceById(id: String?): ServiceDefinition? =
        id?.let { candidate -> services.firstOrNull { it.id == candidate } }

    fun servicesForDtc(dtcCode: String?): List<ServiceDefinition> {
        val normalized = dtcCode?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (normalized.isBlank()) return emptyList()
        return services.filter { service ->
            service.relatedDtcs.any { it.uppercase(Locale.ROOT) == normalized }
        }
    }

    fun bestServicesForDtcs(dtcCodes: List<String>): List<ServiceDefinition> {
        val directMatches = dtcCodes.flatMap(::servicesForDtc).distinctBy { it.id }
        return directMatches.ifEmpty {
            enabledServicesForCategory(ServiceCategory.DIAGNOSTIC).take(4)
        }
    }

    fun providerRoleRequirements(roleId: String): ProviderRoleDefinition? =
        providerRoles.firstOrNull { it.id == roleId }

    fun p0230SafetyNote(): String =
        "P0230 requiere medicion electrica y presion de combustible; no recomendar cambio directo de bomba sin evidencia."

    fun requestSummary(service: ServiceDefinition, dtcCodes: List<String>): String {
        val dtcs = dtcCodes.filter { it.isNotBlank() }.distinct()
        return buildString {
            appendLine("[MEET_SERVICE_CATALOG]")
            appendLine("service_id=${service.id}")
            appendLine("service_category=${service.category.name}")
            appendLine("risk=${service.riskLevel.name}")
            appendLine("duration_min=${service.estimatedDurationMin}")
            appendLine("requires_obd=${service.requiresObd}")
            appendLine("supports_remote=${service.supportsRemote}")
            appendLine("supports_mobile=${service.supportsMobileService}")
            appendLine("tools=${service.requiredTools.joinToString()}")
            appendLine("evidence=${service.requiredEvidence.joinToString { it.name }}")
            if (dtcs.isNotEmpty()) appendLine("dtc_codes=${dtcs.joinToString()}")
            if (service.relatedDtcs.any { it == "P0230" }) appendLine("safety_note=${p0230SafetyNote()}")
            appendLine("[/MEET_SERVICE_CATALOG]")
        }.trim()
    }

    private fun service(
        category: ServiceCategory,
        name: String,
        description: String = "${category.displayName}: $name con evidencia tecnica y trazabilidad MEET.",
        requiredTools: List<String> = category.defaultTools,
        requiredEvidence: List<EvidenceType> = category.defaultEvidence,
        estimatedDurationMin: Int = category.defaultDurationMin,
        basePriceMinCrc: Int = category.basePriceMinCrc,
        basePriceMaxCrc: Int = category.basePriceMaxCrc,
        riskLevel: RiskLevel = category.defaultRisk,
        requiresVehicleOn: Boolean = category in setOf(ServiceCategory.DIAGNOSTIC, ServiceCategory.ENGINE, ServiceCategory.TRANSMISSION, ServiceCategory.AIR_CONDITIONING),
        requiresObd: Boolean = category.requiresObdByDefault,
        requiresPhysicalPresence: Boolean = !category.supportsRemoteByDefault,
        supportsRemote: Boolean = category.supportsRemoteByDefault,
        supportsMobileService: Boolean = category.supportsMobileByDefault,
        supportedVehicleTypes: List<String> = commonVehicleTypes,
        supportedFuelTypes: List<String> = commonFuelTypes,
        relatedDtcs: List<String> = emptyList(),
        relatedPids: List<String> = emptyList(),
    ) = ServiceDefinition(
        id = "${category.name.lowercase(Locale.ROOT)}_${slug(name)}",
        category = category,
        name = name,
        description = description,
        requiredTools = requiredTools,
        requiredEvidence = requiredEvidence,
        estimatedDurationMin = estimatedDurationMin,
        basePriceMinCrc = basePriceMinCrc,
        basePriceMaxCrc = basePriceMaxCrc,
        riskLevel = riskLevel,
        requiresVehicleOn = requiresVehicleOn,
        requiresObd = requiresObd,
        requiresPhysicalPresence = requiresPhysicalPresence,
        supportsRemote = supportsRemote,
        supportsMobileService = supportsMobileService,
        supportedVehicleTypes = supportedVehicleTypes,
        supportedFuelTypes = supportedFuelTypes,
        relatedDtcs = relatedDtcs,
        relatedPids = relatedPids,
    )

    private fun p0230Services(): List<ServiceDefinition> {
        val dtcs = listOf("P0230", "P0231", "P0232", "P0233")
        val pids = listOf("BATTERY_VOLTAGE", "FUEL_PRESSURE", "FUEL_RAIL_PRESSURE", "ENGINE_RPM")
        val evidence = listOf(
            EvidenceType.FREEZE_FRAME,
            EvidenceType.OBD_SNAPSHOT,
            EvidenceType.MULTIMETER_READING,
            EvidenceType.BEFORE_PHOTO,
            EvidenceType.PROVIDER_NOTE,
        )
        return listOf(
            service(
                category = ServiceCategory.DIAGNOSTIC,
                name = "Diagnostico circuito primario bomba combustible",
                description = "Prueba guiada del circuito primario de bomba: DTC, freeze frame, voltaje bateria, rele/fusible, alimentacion de bomba y masa.",
                requiredTools = listOf("OBD-II scanner", "multimetro", "manometro combustible", "probador rele/fusible"),
                requiredEvidence = evidence,
                estimatedDurationMin = 75,
                basePriceMinCrc = 25000,
                basePriceMaxCrc = 65000,
                riskLevel = RiskLevel.HIGH,
                relatedDtcs = dtcs,
                relatedPids = pids,
            ),
            service(
                category = ServiceCategory.ELECTRICAL_ELECTRONIC,
                name = "Revision rele/fusible bomba combustible",
                description = "Verificacion de fusiblera, rele, alimentacion y caida de tension antes de condenar piezas.",
                requiredTools = listOf("multimetro", "probador rele/fusible", "diagrama electrico"),
                requiredEvidence = evidence,
                estimatedDurationMin = 60,
                basePriceMinCrc = 22000,
                basePriceMaxCrc = 55000,
                riskLevel = RiskLevel.HIGH,
                relatedDtcs = dtcs,
                relatedPids = pids,
            ),
            service(
                category = ServiceCategory.ELECTRICAL_ELECTRONIC,
                name = "Medicion voltaje bomba combustible",
                description = "Medicion documentada de voltaje, tierra y continuidad en el circuito de bomba.",
                requiredTools = listOf("multimetro", "back probes", "diagrama electrico"),
                requiredEvidence = evidence,
                estimatedDurationMin = 70,
                basePriceMinCrc = 24000,
                basePriceMaxCrc = 60000,
                riskLevel = RiskLevel.HIGH,
                relatedDtcs = dtcs,
                relatedPids = pids,
            ),
            service(
                category = ServiceCategory.ENGINE,
                name = "Prueba presion combustible",
                description = "Medicion de presion y comportamiento de combustible como prueba complementaria al circuito electrico.",
                requiredTools = listOf("manometro combustible", "OBD-II scanner", "equipo seguridad combustible"),
                requiredEvidence = listOf(EvidenceType.OBD_SNAPSHOT, EvidenceType.MULTIMETER_READING, EvidenceType.PROVIDER_NOTE),
                estimatedDurationMin = 70,
                basePriceMinCrc = 25000,
                basePriceMaxCrc = 65000,
                riskLevel = RiskLevel.HIGH,
                relatedDtcs = dtcs,
                relatedPids = pids,
            ),
            service(
                category = ServiceCategory.ELECTRICAL_ELECTRONIC,
                name = "Revision arnes/masa bomba combustible",
                description = "Inspeccion de arnes, tierras, conectores y caidas de tension del circuito de bomba.",
                requiredTools = listOf("multimetro", "linterna tecnica", "limpiador contactos", "diagrama electrico"),
                requiredEvidence = evidence,
                estimatedDurationMin = 80,
                basePriceMinCrc = 25000,
                basePriceMaxCrc = 70000,
                riskLevel = RiskLevel.HIGH,
                relatedDtcs = dtcs,
                relatedPids = pids,
            ),
        )
    }

    private fun role(
        id: String,
        displayName: String,
        vararg fields: String,
        categories: List<ServiceCategory>,
        requiresCertification: Boolean = false,
    ) = ProviderRoleDefinition(
        id = id,
        displayName = displayName,
        requiredFields = fields.toList(),
        requiredCapabilities = categories.flatMap { it.defaultTools }.distinct(),
        compatibleCategories = categories,
        requiresCertification = requiresCertification,
    )

    private fun allRepairCategories(): List<ServiceCategory> = listOf(
        ServiceCategory.DIAGNOSTIC,
        ServiceCategory.PREVENTIVE_MAINTENANCE,
        ServiceCategory.ENGINE,
        ServiceCategory.ELECTRICAL_ELECTRONIC,
        ServiceCategory.BRAKES_SUSPENSION_STEERING,
        ServiceCategory.TRANSMISSION,
        ServiceCategory.AIR_CONDITIONING,
        ServiceCategory.EMERGENCY_ASSISTANCE,
    )

    private fun servicePackage(id: String, name: String, vararg tasks: String) =
        ServicePackageDefinition(id = id, name = name, includedTasks = tasks.toList())

    private fun slug(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
}
