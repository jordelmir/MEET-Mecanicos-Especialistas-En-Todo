package com.elysium369.meet.core.obd

/**
 * Unique identifier for Mode $06 monitor and test definitions,
 * parameterized by protocol family and optional UASID.
 */
data class Mode06SemanticKey(
    val protocolFamily: ProtocolFamily,
    val mid: Int,
    val tid: Int,
    val uasid: Int? = null
)

data class Mode06Definition(
    val testName: String,
    val componentName: String,
    val isStandardized: Boolean = true,
    val proTip: String? = null
)

interface Mode06DefinitionRegistry {
    fun resolve(key: Mode06SemanticKey, vehicleContext: String? = null): Mode06Definition?
}

/**
 * Standard SAE J1979 / SAE J1979-DA / ISO 15765-4 Mode $06 definitions.
 */
class DefaultMode06DefinitionRegistry : Mode06DefinitionRegistry {

    private val midNames = mapOf(
        0x01 to "Sensor Oxígeno Banco 1 Sensor 1",
        0x02 to "Sensor Oxígeno Banco 1 Sensor 2",
        0x03 to "Sensor Oxígeno Banco 1 Sensor 3",
        0x04 to "Sensor Oxígeno Banco 1 Sensor 4",
        0x05 to "Sensor Oxígeno Banco 2 Sensor 1",
        0x06 to "Sensor Oxígeno Banco 2 Sensor 2",
        0x07 to "Sensor Oxígeno Banco 2 Sensor 3",
        0x08 to "Sensor Oxígeno Banco 2 Sensor 4",
        0x09 to "Sensor Oxígeno Banco 3 Sensor 1",
        0x0A to "Sensor Oxígeno Banco 3 Sensor 2",
        0x0B to "Sensor Oxígeno Banco 4 Sensor 1",
        0x0C to "Sensor Oxígeno Banco 4 Sensor 2",
        0x21 to "Catalizador Banco 1",
        0x22 to "Catalizador Banco 2",
        0x23 to "Catalizador Banco 3",
        0x24 to "Catalizador Banco 4",
        0x31 to "Monitor EGR / VVT Banco 1",
        0x32 to "Monitor EGR / VVT Banco 2",
        0x35 to "Monitor EVAP (Fuga de 0.040\")",
        0x36 to "Monitor EVAP (Fuga de 0.020\")",
        0x39 to "Monitor EVAP (Flujo Purga)",
        0x3A to "Monitor EVAP (Fuga muy pequeña)",
        0x3B to "Monitor EVAP (Cánister / Presión)",
        0x41 to "Sistema Aire Secundario Banco 1",
        0x42 to "Sistema Aire Secundario Banco 2",
        0x51 to "Monitoreo del Sistema de Combustible Banco 1",
        0x52 to "Monitoreo del Sistema de Combustible Banco 2",
        0x61 to "Control de Presión Turbo Banco 1",
        0x62 to "Control de Presión Turbo Banco 2",
        0x71 to "Sensor / Adsorbedor NOx Banco 1",
        0x72 to "Sensor / Adsorbedor NOx Banco 2",
        0x81 to "Filtro Partículas (DPF/GPF) Banco 1",
        0x82 to "Filtro Partículas (DPF/GPF) Banco 2",
        0x91 to "Distribución Variable (VVT) Banco 1",
        0x92 to "Distribución Variable (VVT) Banco 2",
        0xA1 to "Falla Encendido Cilindro 1",
        0xA2 to "Falla Encendido Cilindro 2",
        0xA3 to "Falla Encendido Cilindro 3",
        0xA4 to "Falla Encendido Cilindro 4",
        0xA5 to "Falla Encendido Cilindro 5",
        0xA6 to "Falla Encendido Cilindro 6",
        0xA7 to "Falla Encendido Cilindro 7",
        0xA8 to "Falla Encendido Cilindro 8",
        0xA9 to "Falla Encendido Cilindro 9",
        0xAA to "Falla Encendido Cilindro 10",
        0xAB to "Falla Encendido Cilindro 11",
        0xAC to "Falla Encendido Cilindro 12"
    )

    private val tidNames = mapOf(
        0x01 to "Voltaje de umbral Rico a Pobre del sensor (Rich-to-Lean)",
        0x02 to "Voltaje de umbral Pobre a Rico del sensor (Lean-to-Rich)",
        0x03 to "Voltaje bajo del sensor para tiempo de conmutación",
        0x04 to "Voltaje alto del sensor para tiempo de conmutación",
        0x05 to "Tiempo de conmutación Rico a Pobre (Rich-to-Lean switch time)",
        0x06 to "Tiempo de conmutación Pobre a Rico (Lean-to-Rich switch time)",
        0x07 to "Voltaje mínimo del sensor en el ciclo de prueba",
        0x08 to "Voltaje máximo del sensor en el ciclo de prueba",
        0x09 to "Tiempo entre transiciones del sensor",
        0x0A to "Periodo del sensor de O2",
        0x0B to "Conteo de fallas de encendido promedio (EWMA)",
        0x0C to "Conteo de fallas de encendido máximas (Ciclo actual)",
        0x11 to "Monitoreo del catalizador: coeficiente de almacenamiento de oxígeno",
        0x12 to "Monitoreo del catalizador: pico de respuesta del sensor de O2 trasero",
        0x21 to "EVAP: Caída de presión del sistema (Fuga grande)",
        0x22 to "EVAP: Vacío / Tasa de cambio de presión (Fuga pequeña)",
        0x31 to "Prueba de fuga del sistema EVAP",
        0x32 to "Prueba de flujo de purga EVAP",
        0x41 to "Eficiencia del Catalizador",
        0x45 to "Calentador del Sensor de Oxígeno: Resistencia del elemento",
        0x51 to "Flujo del sistema EGR",
        0x52 to "EGR: Sensor de contrapresión / elevación de válvula",
        0x53 to "Control de sincronización del VVT / Sensor de posición de levas",
        0x61 to "Presión del Turbo: Desviación respecto al valor objetivo",
        0x71 to "Eficiencia de reducción de NOx del catalizador",
        0x81 to "Filtro DPF/GPF: Presión diferencial / Caída de presión",
        0x82 to "Filtro DPF/GPF: Regeneración o acumulación de hollín"
    )

    override fun resolve(key: Mode06SemanticKey, vehicleContext: String?): Mode06Definition? {
        if (key.protocolFamily in listOf(ProtocolFamily.ISO_K_LINE, ProtocolFamily.J1850_PWM, ProtocolFamily.J1850_VPW)) {
            // In SAE J1979 Legacy (pre-CAN), byte 1 is TID and byte 2 is CID.
            // Do NOT use CAN SAE J1979-DA MID mappings which cause bogus Bank 3/4 labels on 4-cylinder engines.
            val legacyTid = key.mid
            val legacyCid = key.tid
            val tidDesc = tidNames[legacyTid] ?: String.format("Prueba Legacy TID \$%02X", legacyTid)
            val compName = String.format("Monitor Legacy \$%02X (CID \$%02X)", legacyTid, legacyCid)
            return Mode06Definition(
                testName = tidDesc,
                componentName = compName,
                isStandardized = false,
                proTip = "En protocolos heredados (ISO 9141-2 / J1850), los identificadores corresponden a la tabla OEM del fabricante. Semántica no confirmada."
            )
        }

        // Sanity guard: Bank 3 and Bank 4 do not exist on inline-4 or single-bank engines
        val isMultibankAnomalous = key.mid in listOf(0x09, 0x0A, 0x0B, 0x0C, 0x23, 0x24)
        val isConfirmedSmallEngine = vehicleContext != null && (
            vehicleContext.contains("4-cyl", ignoreCase = true) ||
            vehicleContext.contains("1.6", ignoreCase = true) ||
            vehicleContext.contains("Accent", ignoreCase = true) ||
            vehicleContext.contains("I4", ignoreCase = true)
        )

        val rawComp = midNames[key.mid]
        val comp = if (isMultibankAnomalous && isConfirmedSmallEngine) {
            String.format("Monitor \$%02X (Definición OEM no confirmada)", key.mid)
        } else {
            rawComp ?: String.format("Monitor ID \$%02X", key.mid)
        }

        val test = tidNames[key.tid] ?: String.format("Prueba ID \$%02X", key.tid)
        val isStd = midNames.containsKey(key.mid) || tidNames.containsKey(key.tid)
        return Mode06Definition(
            testName = test,
            componentName = comp,
            isStandardized = isStd && !(isMultibankAnomalous && isConfirmedSmallEngine),
            proTip = if (isMultibankAnomalous && isConfirmedSmallEngine) "Banco 3/4 no existe en motores de 4 cilindros. Requiere definición OEM." else null
        )
    }
}
