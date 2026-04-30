package com.elysium369.meet.core.obd

/**
 * DiagnosticCommandManager — Managed repository of OEM-specific and standard UDS commands.
 * Used for bi-directional controls (Mode 2F) and Routine Control (Mode 31).
 */
object DiagnosticCommandManager {

    data class ObdCommandDef(
        val name: String,
        val description: String,
        val command: String, // Full HEX command
        val expectedResponse: String, // Regex or substring
        val category: String,
        val manufacturer: String = "GENERIC"
    )

    private val COMMAND_DATABASE = listOf(
        // --- Active Tests (Mode 2F: InputOutputControlByIdentifier) ---
        ObdCommandDef(
            "Apagar Inyector Cilindro 1",
            "Corta el pulso de inyección del cilindro 1 para prueba de balance.",
            "2F 01 01 03", // Short-term adjustment (03) of ID 0101
            "6F 01 01",
            "ENGINE"
        ),
        ObdCommandDef(
            "Test Electroventilador (Baja)",
            "Enciende el ventilador del radiador en velocidad baja.",
            "2F 11 02 03 01",
            "6F 11 02",
            "COOLING"
        ),
        ObdCommandDef(
            "Test Electroventilador (Alta)",
            "Enciende el ventilador del radiador en velocidad alta.",
            "2F 11 02 03 02",
            "6F 11 02",
            "COOLING"
        ),
        ObdCommandDef(
            "Compresor A/C Clutch Relay",
            "Activa manualmente el embrague del compresor de aire acondicionado.",
            "2F 01 10 03 01",
            "6F 01 10",
            "HVAC"
        ),
        ObdCommandDef(
            "Prueba de Válvula EVAP Purge",
            "Abre la válvula de purga del cánister.",
            "2F 01 22 03 64", // 100% duty cycle
            "6F 01 22",
            "EMISSIONS"
        ),

        // --- Service Resets (Mode 31: RoutineControl) ---
        ObdCommandDef(
            "Oil Maintenance Reset",
            "Resetea el intervalo de servicio de aceite.",
            "31 01 FF 00", // Standard UDS routine for reset
            "71 01 FF",
            "MAINTENANCE"
        ),
        ObdCommandDef(
            "EPB Retract (Service Mode)",
            "Retrae los motores del freno de mano para mantenimiento.",
            "31 01 00 01",
            "71 01 00",
            "BRAKES"
        ),
        ObdCommandDef(
            "Throttle Adaptation",
            "Inicia el proceso de aprendizaje del cuerpo de mariposa.",
            "31 01 00 05",
            "71 01 00",
            "ENGINE"
        ),
        ObdCommandDef(
            "DPF Regeneration",
            "Inicia la regeneración estática del filtro de partículas.",
            "31 01 00 07",
            "71 01 00",
            "EMISSIONS"
        ),
        ObdCommandDef(
            "SAS Calibration",
            "Calibración del sensor del ángulo de giro del volante.",
            "31 01 00 09",
            "71 01 00",
            "STEERING"
        ),
        ObdCommandDef(
            "BMS Reset",
            "Reseteo de parámetros de batería tras reemplazo.",
            "31 01 00 0B",
            "71 01 00",
            "MAINTENANCE"
        ),
        ObdCommandDef(
            "TPMS Reset",
            "Reaprendizaje de sensores de presión de neumáticos.",
            "31 01 00 0D",
            "71 01 00",
            "TIRES"
        ),

        // --- VAG Specific (Volkswagen/Audi/Seat/Skoda) ---
        ObdCommandDef(
            "VAG: Retraer Frenos EPB",
            "Abre los calipers traseros para cambio de pastillas (VAG).",
            "04 2F 5F 02 03 01", // Example VAG UDS command
            "6F 5F 02",
            "BRAKES",
            "VAG"
        ),
        ObdCommandDef(
            "VAG: Calibración G85",
            "Calibración básica del sensor de ángulo de dirección (VAG).",
            "04 31 01 00 12",
            "71 01 00",
            "STEERING",
            "VAG"
        ),
        ObdCommandDef(
            "VAG: Purga Bomba Combustible",
            "Activa la bomba de combustible para purgar el sistema.",
            "04 2F 01 25 03 01",
            "6F 01 25",
            "ENGINE",
            "VAG"
        ),

        // --- Ford Specific ---
        ObdCommandDef(
            "Ford: Reset BMS",
            "Resetea el sensor de monitoreo de batería (Ford).",
            "22 40 4B", // Mode 22 to check, but routine is often Mode 31
            "62 40 4B",
            "MAINTENANCE",
            "FORD"
        ),
        ObdCommandDef(
            "Ford: Calibración ABS",
            "Inicia la purga de servicio del módulo ABS (Ford).",
            "31 01 00 01", 
            "71 01 00",
            "BRAKES",
            "FORD"
        ),

        // --- GM Specific ---
        ObdCommandDef(
            "GM: Reset Vida Aceite",
            "Resetea el porcentaje de vida del aceite en vehículos GM.",
            "31 01 00 0F",
            "71 01 00",
            "MAINTENANCE",
            "GM"
        ),
        ObdCommandDef(
            "GM: Aprendizaje Variación Cigüeñal",
            "Inicia el aprendizaje de variación de posición del cigüeñal (CASE).",
            "31 01 00 1A",
            "71 01 00",
            "ENGINE",
            "GM"
        ),

        // --- Toyota Specific ---
        ObdCommandDef(
            "Toyota: Purga de Frenos",
            "Activa los actuadores de ABS para purgado de aire (Toyota).",
            "31 01 00 01",
            "71 01 00",
            "BRAKES",
            "TOYOTA"
        ),
        ObdCommandDef(
            "Toyota: Registro Batería",
            "Registra el cambio de batería en el sistema de gestión.",
            "31 01 00 0B",
            "71 01 00",
            "MAINTENANCE",
            "TOYOTA"
        ),

        // --- BMW Specific ---
        ObdCommandDef(
            "BMW: Registro Batería",
            "Registra una nueva batería en el sistema DME/DDE (BMW).",
            "31 01 00 01", 
            "71 01 00",
            "MAINTENANCE",
            "BMW"
        ),
        ObdCommandDef(
            "BMW: Reset CBS",
            "Resetea los servicios basados en la condición (CBS).",
            "31 01 00 05",
            "71 01 00",
            "MAINTENANCE",
            "BMW"
        )
    )

    fun getCommandsByCategory(category: String, manufacturer: String = "GENERIC"): List<ObdCommandDef> {
        return COMMAND_DATABASE.filter { 
            (it.category == category || category == "ALL") && 
            (it.manufacturer == manufacturer || it.manufacturer == "GENERIC")
        }
    }

    fun getCommandByName(name: String): ObdCommandDef? {
        return COMMAND_DATABASE.find { it.name == name }
    }
}
