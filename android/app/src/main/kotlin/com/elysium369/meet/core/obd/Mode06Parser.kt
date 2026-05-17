package com.elysium369.meet.core.obd

import android.util.Log

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
            "\$01" to "Voltaje de umbral Rico a Pobre del sensor",
            "\$02" to "Voltaje de umbral Pobre a Rico del sensor",
            "\$03" to "Voltaje bajo del sensor para tiempo de conmutación",
            "\$04" to "Voltaje alto del sensor para tiempo de conmutación",
            "\$05" to "Tiempo de conmutación Rico a Pobre",
            "\$06" to "Tiempo de conmutación Pobre a Rico",
            "\$07" to "Voltaje mínimo del sensor en el ciclo de prueba",
            "\$08" to "Voltaje máximo del sensor en el ciclo de prueba",
            "\$09" to "Tiempo entre transiciones del sensor",
            "\$0A" to "Periodo del sensor",
            "\$0B" to "Conteo de fallas de encendido (EWMA)",
            "\$0C" to "Conteo de fallas de encendido (Ciclo actual)",
            "\$31" to "Prueba de fuga del sistema EVAP",
            "\$32" to "Prueba de flujo de purga EVAP",
            "\$41" to "Eficiencia del Catalizador",
            "\$51" to "Flujo del sistema EGR"
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
            "\$21" to "Catalizador Banco 1",
            "\$22" to "Catalizador Banco 2",
            "\$31" to "Monitor EGR Banco 1",
            "\$32" to "Monitor EGR Banco 2",
            "\$35" to "Monitor EVAP (0.040\")",
            "\$36" to "Monitor EVAP (0.020\")",
            "\$39" to "Monitor EVAP (Flujo Purga)",
            "\$3A" to "Monitor EVAP (Fuga Pequeña)",
            "\$41" to "Sistema Aire Secundario Banco 1",
            "\$42" to "Sistema Aire Secundario Banco 2",
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
        val clean = rawResponse.uppercase().replace(" ", "")
        
        if (!clean.startsWith("46")) return emptyList()

        try {
            var i = 0
            while (i + 18 <= clean.length) {
                val start = clean.indexOf("46", i)
                if (start < 0) break
                
                val end = (start + 20).coerceAtMost(clean.length)
                val record = clean.substring(start, end)
                
                if (record.length < 18) break
                
                val midHex = record.substring(2, 4)
                val tidHex = record.substring(4, 6)
                
                var offset = 6
                val hasUid = record.length >= 20
                val uidHex = if (hasUid) record.substring(6, 8) else "00"
                if (hasUid) offset = 8

                val valHex = record.substring(offset, offset + 4)
                val minHex = record.substring(offset + 4, offset + 8)
                val maxHex = record.substring(offset + 8, (offset + 12).coerceAtMost(record.length))

                val rawValue = valHex.toInt(16)
                val rawMin = if (minHex.length == 4 && minHex != "FFFF") minHex.toInt(16) else null
                val rawMax = if (maxHex.length == 4 && maxHex != "FFFF") maxHex.toInt(16) else null

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

                i = start + 20
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
