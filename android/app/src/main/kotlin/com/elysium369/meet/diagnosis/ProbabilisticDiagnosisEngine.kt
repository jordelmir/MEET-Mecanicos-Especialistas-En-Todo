package com.elysium369.meet.diagnosis

import com.elysium369.meet.diagnostic.DiagnosticProvenance

/**
 * Motor de diagnóstico probabilístico V1.
 *
 * Reglas del producto:
 * 1. NUNCA afirma "pieza dañada". Siempre: "causa probable requiere test X antes de condenar pieza Y".
 * 2. Si provenance no es Real/Offline, multiplica probabilidades por <1 y agrega warning.
 * 3. Cada causa probable incluye mandatoryTests que el mecánico debe ejecutar primero.
 * 4. Cada causa incluye doNotReplaceYet para evitar reemplazos prematuros.
 * 5. Si el motor no conoce el DTC, devuelve INSUFFICIENT_DATA con recomendación de escanear más.
 *
 * Semilla: P0230, P0301, P0171, P0174, P0420, P0128 — todos con ≥3 causas probables.
 */
class ProbabilisticDiagnosisEngine {

    /**
     * Catálogo de causas probables por DTC. Las probabilidades son likelihoods
     * independientes (no suman 1.0 necesariamente); el caller debe interpretar
     * en conjunto con mandatoryTests.
     */
    private val catalog: Map<String, List<ProbableCause>> = mapOf(
        // P0230 — Fuel Pump Primary Circuit
        "P0230" to listOf(
            ProbableCause(
                cause = "Relé de bomba de combustible defectuoso o fusible quemado",
                probability = 0.42,
                severity = 3,
                estimatedCostUsd = 25.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Multímetro", "Pinza de prueba"),
                mandatoryTests = listOf(
                    "Medir voltaje en el relé con ignición ON (debe ser ~12V)",
                    "Verificar continuidad del fusible de la bomba"
                ),
                possibleFalsePositives = listOf(
                    "Cableado con falso contacto intermitente"
                ),
                doNotReplaceYet = listOf("Bomba de combustible", "Módulo ECM/PCM")
            ),
            ProbableCause(
                cause = "Conexión a tierra deficiente (GND)",
                probability = 0.26,
                severity = 2,
                estimatedCostUsd = 10.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Multímetro", "Cable de puente"),
                mandatoryTests = listOf("Medir resistencia entre GND del relé y chasis (<0.5Ω)"),
                doNotReplaceYet = listOf("Bomba de combustible", "ECM")
            ),
            ProbableCause(
                cause = "Conector sulfatado / corroído en el arnés de la bomba",
                probability = 0.15,
                severity = 2,
                estimatedCostUsd = 5.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Limpiador de contactos", "Pinza"),
                mandatoryTests = listOf("Inspección visual + prueba de continuidad"),
                doNotReplaceYet = listOf("Bomba de combustible")
            ),
            ProbableCause(
                cause = "Bomba de combustible defectuosa",
                probability = 0.10,
                severity = 3,
                estimatedCostUsd = 220.0,
                difficulty = ProcedureDifficulty.MEDIUM,
                requiredTools = listOf("Multímetro", "Manómetro de presión de combustible"),
                mandatoryTests = listOf(
                    "Verificar presión de combustible con motor en crank",
                    "Aislar relé y energizar bomba directamente con jumper"
                ),
                doNotReplaceYet = listOf("Tanque de combustible")
            ),
            ProbableCause(
                cause = "ECM/PCM no comandando el relé",
                probability = 0.05,
                severity = 4,
                estimatedCostUsd = 800.0,
                difficulty = ProcedureDifficulty.EXPERT_ONLY,
                requiredTools = listOf("Osciloscopio", "Scanner OEM"),
                mandatoryTests = listOf("Verificar señal de comando del ECM con osciloscopio"),
                doNotReplaceYet = listOf("Bomba, relé")
            ),
            ProbableCause(
                cause = "Software desactualizado / TSB",
                probability = 0.02,
                severity = 1,
                estimatedCostUsd = 0.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Scanner OEM"),
                mandatoryTests = listOf("Buscar TSB vigente del fabricante"),
                doNotReplaceYet = listOf("Cualquier pieza")
            )
        ),

        // P0301 — Cylinder 1 Misfire Detected
        "P0301" to listOf(
            ProbableCause(
                cause = "Bujía del cilindro 1 desgastada o con gap incorrecto",
                probability = 0.35,
                severity = 3,
                estimatedCostUsd = 15.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Llave de bujías", "Calibrador de gaps"),
                mandatoryTests = listOf(
                    "Inspeccionar electrodo de bujía (desgaste, depósito, gap)",
                    "Medir gap con calibrador (spec OEM)"
                ),
                doNotReplaceYet = listOf("Bobina de encendido", "Inyector", "Compresión del motor")
            ),
            ProbableCause(
                cause = "Bobina de encendido del cilindro 1 defectuosa",
                probability = 0.25,
                severity = 3,
                estimatedCostUsd = 60.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Multímetro", "Osciloscopio opcional"),
                mandatoryTests = listOf(
                    "Intercambiar bobina con otro cilindro y ver si el misfire sigue",
                    "Medir resistencia primaria y secundaria"
                ),
                doNotReplaceYet = listOf("Inyector", "Pistón")
            ),
            ProbableCause(
                cause = "Inyector del cilindro 1 obstruido o con goteo",
                probability = 0.15,
                severity = 3,
                estimatedCostUsd = 180.0,
                difficulty = ProcedureDifficulty.MEDIUM,
                requiredTools = listOf("Kit de prueba de inyectores", "Multímetro"),
                mandatoryTests = listOf(
                    "Medir resistencia del inyector (spec)",
                    "Test de goteo y patrón de aspersión"
                ),
                doNotReplaceYet = listOf("Pistón", "Anillos")
            ),
            ProbableCause(
                cause = "Pérdida de compresión en cilindro 1 (anillos, válvulas)",
                probability = 0.12,
                severity = 4,
                estimatedCostUsd = 1500.0,
                difficulty = ProcedureDifficulty.EXPERT_ONLY,
                requiredTools = listOf("Compresómetro"),
                mandatoryTests = listOf("Test de compresión en frío y caliente"),
                doNotReplaceYet = listOf("Pistón", "Culata")
            ),
            ProbableCause(
                cause = "Cable de bujía con resistencia alta o dañado",
                probability = 0.08,
                severity = 2,
                estimatedCostUsd = 30.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Multímetro"),
                mandatoryTests = listOf("Medir resistencia del cable (kΩ por metro)"),
                doNotReplaceYet = listOf("Cualquier componente interno")
            ),
            ProbableCause(
                cause = "Válvula EGR pegada o PCV defectuoso (causa menos común de P0301)",
                probability = 0.05,
                severity = 2,
                estimatedCostUsd = 50.0,
                difficulty = ProcedureDifficulty.MEDIUM,
                mandatoryTests = listOf("Inspeccionar vacío del motor con vacuómetro"),
                doNotReplaceYet = listOf("Inyector", "ECM")
            )
        ),

        // P0171 — System Too Lean (Bank 1)
        "P0171" to listOf(
            ProbableCause(
                cause = "Fuga de vacío (manguera, junta de admisión)",
                probability = 0.40,
                severity = 2,
                estimatedCostUsd = 30.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Humo generador o herramienta de vacío"),
                mandatoryTests = listOf(
                    "Búsqueda de fugas con humo en el sistema de admisión",
                    "Inspección visual de mangueras y juntas"
                ),
                doNotReplaceYet = listOf("Sensor MAF", "Inyectores", "Bomba de combustible")
            ),
            ProbableCause(
                cause = "Sensor MAF sucio o defectuoso",
                probability = 0.25,
                severity = 2,
                estimatedCostUsd = 80.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Limpiador MAF", "Multímetro"),
                mandatoryTests = listOf(
                    "Limpiar con spray MAF y re-escanear",
                    "Verificar lectura de gramos/segundo vs spec"
                ),
                doNotReplaceYet = listOf("Bomba de combustible", "Regulador de presión")
            ),
            ProbableCause(
                cause = "Bomba de combustible con presión baja",
                probability = 0.15,
                severity = 3,
                estimatedCostUsd = 250.0,
                difficulty = ProcedureDifficulty.MEDIUM,
                requiredTools = listOf("Manómetro de presión"),
                mandatoryTests = listOf("Medir presión con motor en idle y bajo carga"),
                doNotReplaceYet = listOf("Inyectores", "Filtro de combustible")
            ),
            ProbableCause(
                cause = "Válvula PCV pegada o rota",
                probability = 0.10,
                severity = 2,
                estimatedCostUsd = 25.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Inspección visual"),
                mandatoryTests = listOf("Verificar funcionamiento de la PCV"),
                doNotReplaceYet = listOf("Bomba", "MAF")
            ),
            ProbableCause(
                cause = "Inyectores con flujo desbalanceado",
                probability = 0.06,
                severity = 3,
                estimatedCostUsd = 400.0,
                difficulty = ProcedureDifficulty.HARD,
                requiredTools = listOf("Kit de prueba de inyectores"),
                mandatoryTests = listOf("Test de flujo de inyectores"),
                doNotReplaceYet = listOf("Cualquier pieza interna del motor")
            ),
            ProbableCause(
                cause = "Fuga en el sistema de escape pre-sonda (exhaust leak)",
                probability = 0.04,
                severity = 2,
                estimatedCostUsd = 50.0,
                difficulty = ProcedureDifficulty.MEDIUM,
                requiredTools = listOf("OBD live data, escucha"),
                mandatoryTests = listOf("Verificar comportamiento de sondas O2 Bank 1"),
                doNotReplaceYet = listOf("Sonda O2", "Catalizador")
            )
        ),

        // P0420 — Catalyst System Efficiency Below Threshold (Bank 1)
        "P0420" to listOf(
            ProbableCause(
                cause = "Catalizador degradado o contaminado",
                probability = 0.45,
                severity = 3,
                estimatedCostUsd = 800.0,
                difficulty = ProcedureDifficulty.HARD,
                requiredTools = listOf("Osciloscopio", "Scanner con live data"),
                mandatoryTests = listOf(
                    "Comparar voltaje de sonda upstream vs downstream (debe invertirse)",
                    "Inspección visual del catalizador"
                ),
                doNotReplaceYet = listOf("Sonda O2", "Escape")
            ),
            ProbableCause(
                cause = "Sonda de oxígeno downstream envejecida",
                probability = 0.25,
                severity = 2,
                estimatedCostUsd = 120.0,
                difficulty = ProcedureDifficulty.MEDIUM,
                requiredTools = listOf("Scanner OBD"),
                mandatoryTests = listOf("Verificar tiempo de respuesta de la sonda downstream"),
                doNotReplaceYet = listOf("Catalizador")
            ),
            ProbableCause(
                cause = "Fuga de escape pre-catalizador",
                probability = 0.20,
                severity = 2,
                estimatedCostUsd = 80.0,
                difficulty = ProcedureDifficulty.MEDIUM,
                requiredTools = listOf("Humo o escucha con estetoscopio"),
                mandatoryTests = listOf("Búsqueda de fugas en el escape pre-cat"),
                doNotReplaceYet = listOf("Catalizador")
            ),
            ProbableCause(
                cause = "Contaminación del catalizador por aceite o refrigerante",
                probability = 0.06,
                severity = 4,
                estimatedCostUsd = 1200.0,
                difficulty = ProcedureDifficulty.EXPERT_ONLY,
                requiredTools = listOf("Inspección física + endoscopio"),
                mandatoryTests = listOf("Verificar consumo de aceite y coolant"),
                doNotReplaceYet = listOf("Motor — primero diagnosticar causa raíz")
            ),
            ProbableCause(
                cause = "Sonda de oxígeno upstream envejecida",
                probability = 0.04,
                severity = 2,
                estimatedCostUsd = 80.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Scanner"),
                mandatoryTests = listOf("Verificar voltaje upstream (debe oscilar 0.1-0.9V)"),
                doNotReplaceYet = listOf("Catalizador")
            )
        ),

        // P0128 — Coolant Thermostat (below regulating temperature)
        "P0128" to listOf(
            ProbableCause(
                cause = "Termostato pegado en posición abierta",
                probability = 0.55,
                severity = 2,
                estimatedCostUsd = 50.0,
                difficulty = ProcedureDifficulty.MEDIUM,
                requiredTools = listOf("Termómetro infrarrojo o scanner live"),
                mandatoryTests = listOf(
                    "Confirmar que el motor no alcanza temperatura operativa",
                    "Verificar apertura del termostato retirándolo"
                ),
                doNotReplaceYet = listOf("Bomba de agua", "Sensor de temperatura")
            ),
            ProbableCause(
                cause = "Sensor de temperatura de refrigerante (CTS) defectuoso",
                probability = 0.20,
                severity = 2,
                estimatedCostUsd = 40.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Multímetro", "Scanner"),
                mandatoryTests = listOf("Comparar lectura del CTS vs termómetro real"),
                doNotReplaceYet = listOf("Termostato")
            ),
            ProbableCause(
                cause = "Bomba de agua con flujo reducido (no sólo termostato)",
                probability = 0.10,
                severity = 3,
                estimatedCostUsd = 200.0,
                difficulty = ProcedureDifficulty.HARD,
                requiredTools = listOf("Manómetro de presión de cooling system"),
                mandatoryTests = listOf("Test de flujo del sistema de enfriamiento"),
                doNotReplaceYet = listOf("Termostato", "Radiador")
            ),
            ProbableCause(
                cause = "Bajo nivel de refrigerante por fuga lenta",
                probability = 0.10,
                severity = 2,
                estimatedCostUsd = 30.0,
                difficulty = ProcedureDifficulty.EASY,
                requiredTools = listOf("Pressure tester"),
                mandatoryTests = listOf("Test de presión del sistema de cooling"),
                doNotReplaceYet = listOf("Cualquier pieza interna")
            ),
            ProbableCause(
                cause = "Falla del módulo ECM/PCM en lectura de temperatura",
                probability = 0.05,
                severity = 3,
                estimatedCostUsd = 800.0,
                difficulty = ProcedureDifficulty.EXPERT_ONLY,
                requiredTools = listOf("Scanner OEM"),
                mandatoryTests = listOf("Verificar señal cruda del sensor en scanner"),
                doNotReplaceYet = listOf("Termostato", "CTS")
            )
        )
    )

    /**
     * Diagnostica usando reglas heurísticas + catálogo.
     */
    fun diagnose(context: DiagnosisContext): ProbabilisticDiagnosisReport {
        val rawCauses = catalog[context.dtcCode.uppercase()]
        if (rawCauses == null || rawCauses.isEmpty()) {
            return insufficientDataReport(context)
        }

        // Ajustar probabilidades según provenance.
        val multiplier = context.confidenceMultiplier
        val adjustedCauses = rawCauses.map { cause ->
            cause.copy(probability = (cause.probability * multiplier).coerceIn(0.0, 1.0))
        }.sortedByDescending { it.probability }

        // Confidence global = suma ponderada (top 3) × 0.6 + provenance multiplier × 0.4.
        val top3 = adjustedCauses.take(3)
        val topProbabilitySum = top3.sumOf { it.probability }
        val confidenceOverall = ((topProbabilitySum / 3.0) * 0.6 + multiplier * 0.4).coerceIn(0.0, 1.0)

        // Recommended next test = primer mandatory test de la causa más probable.
        val recommendedNextTest = adjustedCauses.firstOrNull()?.mandatoryTests?.firstOrNull()
            ?: "Realizar escaneo completo del sistema"

        // Safety warnings por provenance.
        val safetyWarnings = mutableListOf<String>()
        when {
            context.provenance is DiagnosticProvenance.SinEnlace ->
                safetyWarnings += "Sin enlace: datos no verificados por hardware. Multiplique pruebas físicas."
            context.provenance is DiagnosticProvenance.Simulated ->
                safetyWarnings += "Datos simulados. NO usar para reparación real."
            context.provenance is DiagnosticProvenance.ManualEntry ->
                safetyWarnings += "DTC introducido manualmente. Confirmar con escaneo real antes de reparar."
            context.provenance is DiagnosticProvenance.NoSoportado ->
                safetyWarnings += "Adaptador/vehículo no soporta este DTC. Verificar manualmente."
            context.provenance is DiagnosticProvenance.RequiereHardware ->
                safetyWarnings += "Requiere hardware adicional: ${(context.provenance as DiagnosticProvenance.RequiereHardware).toolName}"
            context.provenance is DiagnosticProvenance.Offline ->
                safetyWarnings += "Datos offline/genéricos. Confirmar con datos reales del vehículo."
        }
        safetyWarnings += "DTC indica circuito o sistema afectado. NO confirma pieza dañada sin medición física."

        val educationalExplanation = buildExplanation(context, adjustedCauses)

        return ProbabilisticDiagnosisReport(
            dtcCode = context.dtcCode.uppercase(),
            probableCauses = adjustedCauses,
            confidenceOverall = confidenceOverall,
            recommendedNextTest = recommendedNextTest,
            safetyWarnings = safetyWarnings,
            provenance = context.provenance,
            educationalExplanation = educationalExplanation
        )
    }

    /**
     * Map rápido síntoma → posibles DTCs. Útil para triage cuando aún no hay DTC.
     */
    fun suggestDtcFromSymptoms(symptoms: List<String>): List<String> {
        val s = symptoms.joinToString(" ").lowercase()
        return when {
            "misfire" in s || "tropiezo" in s -> listOf("P0301", "P0300", "P0302", "P0303", "P0304")
            "no enciende" in s || "no arranca" in s -> listOf("P0230", "P0335", "P0380")
            "humo" in s && "negro" in s -> listOf("P0171", "P0174")
            "calentamiento" in s || "temperatura" in s -> listOf("P0128", "P0217")
            "catalizador" in s || "eficiencia" in s -> listOf("P0420", "P0430")
            "ralentí" in s || "idle" in s -> listOf("P0171", "P0301", "P0505")
            else -> emptyList()
        }
    }

    private fun insufficientDataReport(context: DiagnosisContext): ProbabilisticDiagnosisReport {
        return ProbabilisticDiagnosisReport(
            dtcCode = context.dtcCode.uppercase(),
            probableCauses = emptyList(),
            confidenceOverall = 0.0,
            recommendedNextTest = "Escanear DTCs con adaptador compatible y revisar freeze frame + live PIDs.",
            safetyWarnings = listOf(
                "DTC ${context.dtcCode.uppercase()} no está en el catálogo actual.",
                "Recomendar escaneo completo antes de proceder.",
                "Sin datos suficientes, no emitir conclusiones."
            ),
            provenance = context.provenance,
            educationalExplanation = "Sin datos suficientes para diagnosticar ${context.dtcCode}."
        )
    }

    private fun buildExplanation(
        context: DiagnosisContext,
        causes: List<ProbableCause>
    ): String {
        val top = causes.firstOrNull()
        return buildString {
            append("DTC: ${context.dtcCode.uppercase()}\n")
            if (context.dtcStatus != DtcStatus.ACTIVE) {
                append("Estado: ${context.dtcStatus}\n")
            }
            append("Provenance: ${context.provenance.displayLabel}\n\n")
            if (top != null) {
                append("Causa más probable: ${top.cause} (${(top.probability * 100).toInt()}%).\n")
                append("Severidad: ${top.severity}/4. ")
                if (top.estimatedCostUsd != null) {
                    append("Costo aprox: $${top.estimatedCostUsd}.")
                }
                append("\n")
                append("Próximo test: ${top.mandatoryTests.firstOrNull() ?: "—"}")
            } else {
                append("Sin causas catalogadas. Escanear más señales.")
            }
        }
    }
}