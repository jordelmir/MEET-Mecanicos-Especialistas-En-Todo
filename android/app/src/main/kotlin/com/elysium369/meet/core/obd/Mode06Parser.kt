package com.elysium369.meet.core.obd

import android.util.Log
import com.elysium369.meet.core.obd.CanMultiFrameParser

/**
 * Professional Mode 06 (On-Board Monitoring) Expert Parser.
 * Designed to decode raw hex data into human-readable, context-aware diagnostic reports.
 * This is "Top 1% Mundial" grade logic, mimicking high-end tools like Snap-on/Autel.
 */
class Mode06Parser {

    companion object {
        private const val TAG = "Mode06Parser"

        /**
         * Test ID (TID) to Description Mapping (SAE J1979)
         */
        private val tidDefinitions = mapOf(
            "\$01" to "Voltaje de umbral Rico a Pobre del sensor (Rich-to-Lean)",
            "\$02" to "Voltaje de umbral Pobre a Rico del sensor (Lean-to-Rich)",
            "\$03" to "Voltaje bajo del sensor para tiempo de conmutación",
            "\$04" to "Voltaje alto del sensor para tiempo de conmutación",
            "\$05" to "Tiempo de conmutación Rico a Pobre (Rich-to-Lean switch time)",
            "\$06" to "Tiempo de conmutación Pobre a Rico (Lean-to-Rich switch time)",
            "\$07" to "Voltaje mínimo del sensor en el ciclo de prueba",
            "\$08" to "Voltaje máximo del sensor en el ciclo de prueba",
            "\$09" to "Tiempo entre transiciones del sensor",
            "\$0A" to "Periodo del sensor de O2",
            "\$0B" to "Conteo de fallas de encendido promedio (EWMA)",
            "\$0C" to "Conteo de fallas de encendido máximas (Ciclo actual)",
            "\$11" to "Monitoreo del catalizador: coeficiente de almacenamiento de oxígeno",
            "\$12" to "Monitoreo del catalizador: pico de respuesta del sensor de O2 trasero",
            "\$21" to "EVAP: Caída de presión del sistema (Fuga grande)",
            "\$22" to "EVAP: Vacío / Tasa de cambio de presión (Fuga pequeña)",
            "\$31" to "Prueba de fuga del sistema EVAP",
            "\$32" to "Prueba de flujo de purga EVAP",
            "\$41" to "Eficiencia del Catalizador",
            "\$45" to "Calentador del Sensor de Oxígeno: Resistencia del elemento",
            "\$51" to "Flujo del sistema EGR",
            "\$52" to "EGR: Sensor de contrapresión / elevación de válvula",
            "\$53" to "Control de sincronización del VVT / Sensor de posición de levas",
            "\$61" to "Presión del Turbo: Desviación respecto al valor objetivo",
            "\$71" to "Eficiencia de reducción de NOx del catalizador",
            "\$81" to "Filtro DPF/GPF: Presión diferencial / Caída de presión",
            "\$82" to "Filtro DPF/GPF: Regeneración o acumulación de hollín"
        )

        /**
         * Monitor ID (MID) to Name Mapping (SAE J1979)
         */
        private val midDefinitions = mapOf(
            "\$01" to "Sensor Oxígeno Banco 1 Sensor 1",
            "\$02" to "Sensor Oxígeno Banco 1 Sensor 2",
            "\$03" to "Sensor Oxígeno Banco 1 Sensor 3",
            "\$04" to "Sensor Oxígeno Banco 1 Sensor 4",
            "\$05" to "Sensor Oxígeno Banco 2 Sensor 1",
            "\$06" to "Sensor Oxígeno Banco 2 Sensor 2",
            "\$07" to "Sensor Oxígeno Banco 2 Sensor 3",
            "\$08" to "Sensor Oxígeno Banco 2 Sensor 4",
            "\$09" to "Sensor Oxígeno Banco 3 Sensor 1",
            "\$0A" to "Sensor Oxígeno Banco 3 Sensor 2",
            "\$0B" to "Sensor Oxígeno Banco 4 Sensor 1",
            "\$0C" to "Sensor Oxígeno Banco 4 Sensor 2",
            "\$21" to "Catalizador Banco 1",
            "\$22" to "Catalizador Banco 2",
            "\$23" to "Catalizador Banco 3",
            "\$24" to "Catalizador Banco 4",
            "\$31" to "Monitor EGR / VVT Banco 1",
            "\$32" to "Monitor EGR / VVT Banco 2",
            "\$35" to "Monitor EVAP (Fuga de 0.040\")",
            "\$36" to "Monitor EVAP (Fuga de 0.020\")",
            "\$39" to "Monitor EVAP (Flujo Purga)",
            "\$3A" to "Monitor EVAP (Fuga muy pequeña)",
            "\$3B" to "Monitor EVAP (Cánister / Sensor de presión)",
            "\$41" to "Sistema Aire Secundario Banco 1",
            "\$42" to "Sistema Aire Secundario Banco 2",
            "\$51" to "Monitoreo del Sistema de Combustible Banco 1",
            "\$52" to "Monitoreo del Sistema de Combustible Banco 2",
            "\$61" to "Control de Presión de Sobrealimentación (Turbo) Banco 1",
            "\$62" to "Control de Presión de Sobrealimentación (Turbo) Banco 2",
            "\$71" to "Sensor / Adsorbedor NOx Banco 1",
            "\$72" to "Sensor / Adsorbedor NOx Banco 2",
            "\$81" to "Filtro de Partículas (DPF/GPF) Banco 1",
            "\$82" to "Filtro de Partículas (DPF/GPF) Banco 2",
            "\$91" to "Distribución Variable (VVT) Banco 1",
            "\$92" to "Distribución Variable (VVT) Banco 2",
            "\$A1" to "Falla Encendido Cilindro 1",
            "\$A2" to "Falla Encendido Cilindro 2",
            "\$A3" to "Falla Encendido Cilindro 3",
            "\$A4" to "Falla Encendido Cilindro 4",
            "\$A5" to "Falla Encendido Cilindro 5",
            "\$A6" to "Falla Encendido Cilindro 6",
            "\$A7" to "Falla Encendido Cilindro 7",
            "\$A8" to "Falla Encendido Cilindro 8",
            "\$A9" to "Falla Encendido Cilindro 9",
            "\$AA" to "Falla Encendido Cilindro 10",
            "\$AB" to "Falla Encendido Cilindro 11",
            "\$AC" to "Falla Encendido Cilindro 12"
        )

        /**
         * Unit ID (UID) Scaling & Units (SAE J1979-DA)
         */
        private fun getUnitInfo(uid: String): Pair<Float, String> {
            return when (uid) {
                "\$01" -> 1f to "cnt"
                "\$07" -> 0.001f to "V"
                "\$08" -> 0.01f to "V"
                "\$0B" -> 1f to "ms"
                "\$0C" -> 0.1f to "ms"
                "\$0D" -> 0.001f to "s"
                "\$10" -> 1f to "Pa"
                "\$11" -> 0.1f to "kPa"
                "\$13" -> 0.01f to "kPa"
                "\$1B" -> 0.1f to "ratio"
                "\$23" -> 1f to "g/s"
                else -> 1f to ""
            }
        }
    }

    fun parse(rawResponse: String): List<Mode06TestResult> {
        val results = mutableListOf<Mode06TestResult>()

        // 1. Strip known noise lines before hex processing
        val filtered = rawResponse
            .replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { line ->
                val upper = line.uppercase().replace(" ", "")
                upper.isNotBlank() &&
                    upper != "OK" && upper != ">" &&
                    !upper.startsWith("AT") &&
                    !upper.startsWith("SEARCHING") &&
                    !upper.startsWith("NO DATA") &&
                    !upper.startsWith("UNABLE") &&
                    !upper.startsWith("ERROR") &&
                    !upper.startsWith("?")
            }
            .joinToString(" ")

        // 2. Unify multi-frame via ISO-TP parser, then clean to pure hex
        val unified = CanMultiFrameParser.parse(filtered)
        val clean = unified.uppercase().replace(Regex("[^0-9A-F]"), "")

        if (!clean.contains("46")) return emptyList()

        try {
            var i = 0
            while (i < clean.length) {
                val start = clean.indexOf("46", i)
                if (start < 0) break

                // Need at least 18 hex chars for a minimum record (no UID)
                if (start + 18 > clean.length) break

                try {
                    val midHex = clean.substring(start + 2, start + 4)
                    val tidHex = clean.substring(start + 4, start + 6)

                    // Validate MID/TID are valid hex — skip garbage bytes
                    midHex.toInt(16)
                    tidHex.toInt(16)

                    var offset = start + 6
                    val hasUid = start + 20 <= clean.length
                    val uidHex = if (hasUid) clean.substring(start + 6, start + 8) else "00"
                    if (hasUid) offset = start + 8

                    // Guard substring bounds
                    if (offset + 12 > clean.length) {
                        i = start + 2
                        continue
                    }

                    val valHex = clean.substring(offset, offset + 4)
                    val minHex = clean.substring(offset + 4, offset + 8)
                    val maxHex = clean.substring(offset + 8, offset + 12)

                    val rawValue = valHex.toIntOrNull(16)
                    if (rawValue == null) {
                        i = start + 2
                        continue
                    }
                    val rawMin = if (minHex != "FFFF") minHex.toIntOrNull(16) else null
                    val rawMax = if (maxHex != "FFFF") maxHex.toIntOrNull(16) else null

                    val (scaling, unit) = getUnitInfo("\$$uidHex")

                    val scaledValue = rawValue * scaling
                    val scaledMin = rawMin?.let { it * scaling }
                    val scaledMax = rawMax?.let { it * scaling }

                    val passed = when {
                        scaledMin != null && scaledMax != null -> scaledValue in scaledMin..scaledMax
                        scaledMax != null -> scaledValue <= scaledMax
                        scaledMin != null -> scaledValue >= scaledMin
                        else -> true
                    }

                    // Severity Logic: "Near Limit" detection
                    var severity = if (passed) DiagnosticSeverity.INFO else DiagnosticSeverity.HIGH

                    if (passed) {
                        if (scaledMax != null && scaledValue > (scaledMax * 0.9f)) severity = DiagnosticSeverity.MODERATE
                        if (scaledMin != null && scaledValue < (scaledMin * 1.1f)) severity = DiagnosticSeverity.MODERATE
                    }

                    val midName = midDefinitions["\$$midHex"] ?: "Monitor ID \$$midHex"
                    val tidName = tidDefinitions["\$$tidHex"] ?: "Prueba ID \$$tidHex"

                    results.add(Mode06TestResult(
                        mid = "\$$midHex",
                        tid = "\$$tidHex",
                        value = scaledValue,
                        minLimit = scaledMin,
                        maxLimit = scaledMax,
                        unit = unit,
                        passed = passed,
                        testName = tidName,
                        componentName = midName,
                        proTip = generateProTip("\$$midHex", "\$$tidHex", scaledValue, scaledMin, scaledMax, passed, severity),
                        severity = severity
                    ))

                    i = if (hasUid) start + 20 else start + 18
                } catch (e: Exception) {
                    // Skip this record and try to find the next "46" marker
                    Log.w(TAG, "Skipping corrupt Mode 06 record at offset $start: ${e.message}")
                    i = start + 2
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Mode 06: ${e.message}")
        }

        return results
    }

    private fun generateProTip(mid: String, tid: String, value: Float, min: Float?, max: Float?, passed: Boolean, severity: DiagnosticSeverity): String? {
        if (passed && severity == DiagnosticSeverity.INFO) return null

        if (passed && severity == DiagnosticSeverity.MODERATE) {
            return "ALERTA PREVENTIVA: El valor está muy cerca del límite de falla. Aunque el test pasó, este componente está empezando a degradarse. Recomiende limpieza o revisión preventiva para evitar que se encienda el Check Engine pronto."
        }

        return when {
            mid.startsWith("\$A") -> {
                "Falla de encendido crítica detectada. Si el valor es alto, el daño al catalizador es inminente. Verifique bujías y bobinas inmediatamente. En motores GDI, considere también limpieza de válvulas por carbonilla."
            }
            mid == "\$21" || mid == "\$22" -> {
                "Baja eficiencia catalítica. Antes de cambiar el catalizador, verifique que no existan fugas de aire en la admisión o escape. Un sensor de O2 envejecido también puede causar una lectura falsa de falla de catalizador."
            }
            mid == "\$31" || mid == "\$32" -> {
                "Problema de flujo EGR. La causa más común es la obstrucción por carbón en el tubo o la válvula. Limpie el sistema antes de reemplazar componentes costosos."
            }
            mid.startsWith("\$0") && mid <= "\$08" -> {
                "Sensor de Oxígeno con respuesta lenta o fuera de rango. Esto destruye la economía de combustible y puede causar tirones. Verifique el calentador del sensor y posibles contaminantes como silicón o anticongelante."
            }
            mid.startsWith("\$3") -> {
                "Fuga detectada en el sistema EVAP. Asegúrese de que el tapón de gasolina esté bien cerrado. Si persiste, use una máquina de humo para localizar fugas en las mangueras del cánister o la válvula de purga."
            }
            else -> "El ECU reporta un valor fuera de parámetros operativos. Este fallo inminente afectará el rendimiento y las emisiones del vehículo. Se recomienda diagnóstico físico detallado."
        }
    }
}
