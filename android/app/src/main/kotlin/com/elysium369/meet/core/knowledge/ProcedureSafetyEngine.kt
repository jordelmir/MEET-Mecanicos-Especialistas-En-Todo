package com.elysium369.meet.core.knowledge

enum class SafetyActionType {
    MEASURE_RESISTANCE,
    APPLY_EXTERNAL_VOLTAGE,
    INSTALL_BYPASS_RESISTOR,
    WELD,
    INSTALL_USED_COMPONENT,
    ROUTE_CABLE,
    JOIN_POWER_FEEDS,
    INSTALL_AMPLIFIER,
    KEEP_ACCESSORY_AWAKE,
    EXPOSE_DEBUG_INTERFACE,
    REMOTE_VEHICLE_WRITE,
    OPEN_PRESSURIZED_SYSTEM,
    CREATE_SPARK,
    HIGH_VOLTAGE_SERVICE,
    OTHER
}

enum class SafetyTarget {
    AIRBAG,
    PRETENSIONER,
    YELLOW_SRS_CONNECTOR,
    SEAT_RAIL,
    ISOFIX_ANCHOR,
    TOP_TETHER_ANCHOR,
    SEAT_BELT_ANCHOR,
    USED_SEAT,
    AIRBAG_DEPLOYMENT_PATH,
    RADIO_ACC_B_PLUS,
    AMPLIFIER_POWER,
    RADIO_ANDROID,
    ADB_WIFI_BLUETOOTH,
    CAN_UDS,
    HOT_COOLING_SYSTEM,
    FUEL_SYSTEM,
    HIGH_VOLTAGE_SYSTEM,
    GENERAL
}

enum class ActionOrigin {
    USER,
    TECHNICIAN,
    AI_GENERATED,
    SYSTEM_ALLOWLIST
}

enum class SafetyDecisionStatus {
    ALLOWED,
    REQUIRES_EVIDENCE,
    BLOCKED
}

data class ProcedureSafetyRequest(
    val action: SafetyActionType,
    val target: SafetyTarget,
    val origin: ActionOrigin,
    val evidenceIds: Set<String> = emptySet()
)

data class ProcedureSafetyDecision(
    val status: SafetyDecisionStatus,
    val ruleId: String,
    val message: String,
    val missingEvidence: List<String> = emptyList()
)

class ProcedureSafetyEngine {
    fun evaluate(request: ProcedureSafetyRequest): ProcedureSafetyDecision {
        if (request.origin == ActionOrigin.AI_GENERATED &&
            request.action == SafetyActionType.REMOTE_VEHICLE_WRITE
        ) {
            return blocked(
                "AI_REMOTE_WRITE_FORBIDDEN",
                "La IA no puede autorizar ni ejecutar escritura CAN/UDS remota."
            )
        }

        if (request.action == SafetyActionType.REMOTE_VEHICLE_WRITE &&
            request.target == SafetyTarget.CAN_UDS
        ) {
            return ProcedureSafetyDecision(
                status = SafetyDecisionStatus.REQUIRES_EVIDENCE,
                ruleId = "ACTIVE_TEST_AUTHORIZATION_REQUIRED",
                message = "Ruta directa bloqueada: autorizar la escritura exclusivamente mediante el motor de pruebas activas.",
                missingEvidence = listOf("active_test_authorization_engine_decision")
            )
        }

        if (request.target in SRS_TARGETS && request.action in SRS_FORBIDDEN_ACTIONS) {
            return blocked(
                "SRS_DIRECT_TEST_FORBIDDEN",
                "Bloqueado: no aplicar ohmimetro, voltaje externo ni resistencias de anulacion en SRS."
            )
        }

        if (request.action == SafetyActionType.WELD && request.target in RESTRAINT_ANCHORS) {
            return blocked(
                "RESTRAINT_STRUCTURE_WELD_FORBIDDEN",
                "Bloqueado: no soldar rieles, ISOFIX, top tether ni anclajes de cinturon como reparacion artesanal."
            )
        }

        if (request.action == SafetyActionType.INSTALL_USED_COMPONENT &&
            request.target == SafetyTarget.USED_SEAT
        ) {
            return requireEvidence(
                request,
                ruleId = "USED_SEAT_EVIDENCE_REQUIRED",
                required = USED_SEAT_EVIDENCE,
                message = "Un asiento usado requiere validar numero de parte, SRS, geometria e historial antes de instalar."
            )
        }

        if (request.action == SafetyActionType.ROUTE_CABLE &&
            request.target == SafetyTarget.AIRBAG_DEPLOYMENT_PATH
        ) {
            return blocked(
                "AIRBAG_PATH_MUST_REMAIN_CLEAR",
                "Bloqueado: no enrutar cables, antenas, camaras ni pantallas frente a airbags."
            )
        }

        if (request.action == SafetyActionType.JOIN_POWER_FEEDS &&
            request.target == SafetyTarget.RADIO_ACC_B_PLUS
        ) {
            return blocked(
                "ACC_B_PLUS_JOIN_FORBIDDEN",
                "Bloqueado: ACC y B+ deben conservar funciones electricas separadas."
            )
        }

        if (request.action == SafetyActionType.INSTALL_AMPLIFIER &&
            request.target == SafetyTarget.AMPLIFIER_POWER
        ) {
            return requireEvidence(
                request,
                ruleId = "AMPLIFIER_POWER_EVIDENCE_REQUIRED",
                required = setOf("battery_adjacent_fuse", "verified_ground_point"),
                message = "La alimentacion del amplificador requiere fusible junto a bateria y masa verificada."
            )
        }

        if (request.action == SafetyActionType.KEEP_ACCESSORY_AWAKE &&
            request.target == SafetyTarget.RADIO_ANDROID
        ) {
            return blocked(
                "RADIO_SLEEP_REQUIRED",
                "Bloqueado: la radio no puede quedar despierta con el vehiculo apagado."
            )
        }

        if (request.action == SafetyActionType.EXPOSE_DEBUG_INTERFACE &&
            request.target == SafetyTarget.ADB_WIFI_BLUETOOTH
        ) {
            return blocked(
                "UNPROTECTED_DEBUG_INTERFACE_FORBIDDEN",
                "Bloqueado: no exponer ADB, WiFi o Bluetooth sin proteccion y control de acceso."
            )
        }

        if (request.action == SafetyActionType.OPEN_PRESSURIZED_SYSTEM &&
            request.target == SafetyTarget.HOT_COOLING_SYSTEM
        ) {
            return blocked(
                "HOT_COOLING_SYSTEM_OPEN_FORBIDDEN",
                "Bloqueado: no abrir un sistema de refrigeracion caliente o presurizado."
            )
        }

        if (request.action == SafetyActionType.CREATE_SPARK &&
            request.target == SafetyTarget.FUEL_SYSTEM
        ) {
            return blocked(
                "FUEL_IGNITION_SOURCE_FORBIDDEN",
                "Bloqueado: no usar chispas, cables pelados ni fuentes de ignicion en el sistema de combustible."
            )
        }

        if (request.action == SafetyActionType.HIGH_VOLTAGE_SERVICE &&
            request.target == SafetyTarget.HIGH_VOLTAGE_SYSTEM
        ) {
            return requireEvidence(
                request,
                ruleId = "HV_DEENERGIZATION_EVIDENCE_REQUIRED",
                required = setOf(
                    "oem_deenergization_completed",
                    "absence_of_voltage_confirmed",
                    "ppe_confirmed"
                ),
                message = "El servicio HV requiere desenergizacion OEM, EPP y confirmacion de ausencia de tension."
            )
        }

        return ProcedureSafetyDecision(
            status = SafetyDecisionStatus.ALLOWED,
            ruleId = "NO_BLOCKING_RULE_MATCHED",
            message = "Sin bloqueo automatico. Mantener procedimiento y evidencia aplicables."
        )
    }

    private fun blocked(ruleId: String, message: String) = ProcedureSafetyDecision(
        status = SafetyDecisionStatus.BLOCKED,
        ruleId = ruleId,
        message = message
    )

    private fun requireEvidence(
        request: ProcedureSafetyRequest,
        ruleId: String,
        required: Set<String>,
        message: String
    ): ProcedureSafetyDecision {
        val missing = (required - request.evidenceIds).sorted()
        return if (missing.isEmpty()) {
            ProcedureSafetyDecision(
                status = SafetyDecisionStatus.ALLOWED,
                ruleId = ruleId,
                message = "Evidencia minima registrada. Continuar solo con el procedimiento aplicable."
            )
        } else {
            ProcedureSafetyDecision(
                status = SafetyDecisionStatus.REQUIRES_EVIDENCE,
                ruleId = ruleId,
                message = message,
                missingEvidence = missing
            )
        }
    }

    companion object {
        private val SRS_TARGETS = setOf(
            SafetyTarget.AIRBAG,
            SafetyTarget.PRETENSIONER,
            SafetyTarget.YELLOW_SRS_CONNECTOR
        )
        private val SRS_FORBIDDEN_ACTIONS = setOf(
            SafetyActionType.MEASURE_RESISTANCE,
            SafetyActionType.APPLY_EXTERNAL_VOLTAGE,
            SafetyActionType.INSTALL_BYPASS_RESISTOR
        )
        private val RESTRAINT_ANCHORS = setOf(
            SafetyTarget.SEAT_RAIL,
            SafetyTarget.ISOFIX_ANCHOR,
            SafetyTarget.TOP_TETHER_ANCHOR,
            SafetyTarget.SEAT_BELT_ANCHOR
        )
        private val USED_SEAT_EVIDENCE = setOf(
            "oem_part_number_match",
            "srs_connector_match",
            "geometry_match",
            "collision_history_checked",
            "flood_history_checked"
        )
    }
}

data class ActiveTestRequest(
    val commandId: String,
    val origin: ActionOrigin,
    val allowlistedCommandIds: Set<String>,
    val connectionStable: Boolean,
    val measuredSystemVoltage: Double?,
    val minimumValidatedVoltage: Double,
    val explicitUserConfirmation: Boolean
)

class ActiveTestAuthorizationEngine {
    fun authorize(request: ActiveTestRequest): ProcedureSafetyDecision = when {
        request.origin == ActionOrigin.AI_GENERATED -> ProcedureSafetyDecision(
            SafetyDecisionStatus.BLOCKED,
            "AI_ACTIVE_TEST_AUTHORIZATION_FORBIDDEN",
            "La IA puede explicar la prueba, pero no autorizar ni ejecutar el comando."
        )
        request.commandId.isBlank() -> ProcedureSafetyDecision(
            SafetyDecisionStatus.BLOCKED,
            "ACTIVE_TEST_COMMAND_ID_INVALID",
            "El identificador del comando no puede estar vacio."
        )
        request.commandId !in request.allowlistedCommandIds -> ProcedureSafetyDecision(
            SafetyDecisionStatus.BLOCKED,
            "ACTIVE_TEST_NOT_ALLOWLISTED",
            "Comando fuera de la allowlist validada."
        )
        !request.connectionStable -> ProcedureSafetyDecision(
            SafetyDecisionStatus.BLOCKED,
            "ACTIVE_TEST_LINK_UNSTABLE",
            "Conexion inestable; prueba activa bloqueada."
        )
        !request.minimumValidatedVoltage.isFinite() || request.minimumValidatedVoltage <= 0.0 ->
            ProcedureSafetyDecision(
                SafetyDecisionStatus.BLOCKED,
                "ACTIVE_TEST_VOLTAGE_POLICY_INVALID",
                "El umbral de voltaje no es una especificacion validada."
            )
        request.measuredSystemVoltage == null -> ProcedureSafetyDecision(
            SafetyDecisionStatus.REQUIRES_EVIDENCE,
            "ACTIVE_TEST_VOLTAGE_MISSING",
            "Falta medir el voltaje del sistema antes de la prueba.",
            listOf("system_voltage")
        )
        !request.measuredSystemVoltage.isFinite() || request.measuredSystemVoltage <= 0.0 ->
            ProcedureSafetyDecision(
                SafetyDecisionStatus.BLOCKED,
                "ACTIVE_TEST_VOLTAGE_INVALID",
                "La medicion de voltaje es invalida; prueba activa bloqueada."
            )
        request.measuredSystemVoltage < request.minimumValidatedVoltage -> ProcedureSafetyDecision(
            SafetyDecisionStatus.BLOCKED,
            "ACTIVE_TEST_VOLTAGE_LOW",
            "Voltaje por debajo del umbral validado; prueba activa bloqueada."
        )
        !request.explicitUserConfirmation -> ProcedureSafetyDecision(
            SafetyDecisionStatus.REQUIRES_EVIDENCE,
            "ACTIVE_TEST_CONFIRMATION_REQUIRED",
            "Se requiere confirmacion explicita antes de ejecutar.",
            listOf("explicit_user_confirmation")
        )
        else -> ProcedureSafetyDecision(
            SafetyDecisionStatus.ALLOWED,
            "ACTIVE_TEST_AUTHORIZED",
            "Prueba autorizada por politica determinista."
        )
    }
}
