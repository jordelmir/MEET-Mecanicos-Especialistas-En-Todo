package com.elysium369.meet.core.obd

enum class DiagnosticTruthStatus {
    UNVERIFIED_PRELIMINARY_HYPOTHESIS,
    OBSERVED_ANOMALY,
    REQUIRES_PHYSICAL_BENCH_TEST,
    PHYSICALLY_CONFIRMED_FAULT,
    VERIFIED_POST_REPAIR_HEALTHY,
}

data class DiagnosticStep(
    val stepIndex: Int,
    val title: String,
    val instructions: String,
    val requiredPhysicalTool: String?, // e.g. "Multímetro / Osciloscopio", "Manómetro de Combustible"
    val isSafetyCritical: Boolean,
)

data class GuidedDiagnosticPlan(
    val dtcCode: String,
    val primaryHypothesis: String,
    val truthStatus: DiagnosticTruthStatus,
    val confidence: Double,
    val steps: List<DiagnosticStep>,
    val requiresPhysicalVerification: Boolean = true,
)

/**
 * GuidedDiagnosticPlanCompiler — Compiles step-by-step diagnostic plans grounded in physical proof.
 * Never elevates a hypothesis to a confirmed repair without physical test evidence.
 */
object GuidedDiagnosticPlanCompiler {

    fun compilePlan(
        dtcCode: String,
        freezeFrameAvailable: Boolean,
        physicalTestResultPassed: Boolean? = null,
    ): GuidedDiagnosticPlan {
        val cleanCode = dtcCode.uppercase().trim()

        val (hypothesis, steps) = when (cleanCode) {
            "P0300" -> Pair(
                "Fallo de combustión aleatorio en múltiples cilindros (Random/Multiple Cylinder Misfire)",
                listOf(
                    DiagnosticStep(1, "Inspección de Bujías y Bobinas", "Extraer y medir resistencia de bobinas; verificar desgaste en electrodos.", "Multímetro", false),
                    DiagnosticStep(2, "Prueba de Presión de Combustible", "Conectar manómetro en riel y medir presión en ralentí y bajo carga.", "Manómetro de Combustible", false),
                    DiagnosticStep(3, "Prueba de Humo por Fugas de Vacío", "Inyectar humo en admisión para descartar tomas de aire parásitas.", "Máquina de Humo", false),
                )
            )
            "P0171" -> Pair(
                "Sistema demasiado pobre en Banco 1 (System Too Lean Bank 1)",
                listOf(
                    DiagnosticStep(1, "Comprobación de Fugas de Vacío", "Revisar mangueras de PCV y juntas de colector.", "Máquina de Humo", false),
                    DiagnosticStep(2, "Verificación del Sensor MAF", "Monitorear flujo en g/s con escáner vs especificación de fábrica.", "Escáner OBD", false),
                    DiagnosticStep(3, "Presión y Caudal de Bomba", "Medir caída de presión bajo aceleración.", "Manómetro", false),
                )
            )
            "P0420" -> Pair(
                "Eficiencia del Catalizador por debajo del umbral Banco 1",
                listOf(
                    DiagnosticStep(1, "Osciloscopía de Sensores de O2", "Comparar conmutación de Sensor 1 vs Sensor 2 con motor caliente.", "Osciloscopio", false),
                    DiagnosticStep(2, "Inspección Térmica de Catalizador", "Medir delta de temperatura entre entrada y salida del catalizador.", "Termómetro Infrarrojo", false),
                )
            )
            else -> Pair(
                "Anomalía registrada en código $cleanCode",
                listOf(
                    DiagnosticStep(1, "Inspección Visual del Circuito", "Verificar arnés y conector del componente relacionado.", null, false),
                    DiagnosticStep(2, "Comprobación de Alimentación y Masa", "Medir voltajes de referencia y continuidad con el chasis.", "Multímetro", false),
                )
            )
        }

        val truthStatus = when (physicalTestResultPassed) {
            true -> DiagnosticTruthStatus.PHYSICALLY_CONFIRMED_FAULT
            false -> DiagnosticTruthStatus.VERIFIED_POST_REPAIR_HEALTHY
            null -> if (freezeFrameAvailable) DiagnosticTruthStatus.REQUIRES_PHYSICAL_BENCH_TEST else DiagnosticTruthStatus.UNVERIFIED_PRELIMINARY_HYPOTHESIS
        }

        val confidence = when (truthStatus) {
            DiagnosticTruthStatus.PHYSICALLY_CONFIRMED_FAULT -> 99.0
            DiagnosticTruthStatus.VERIFIED_POST_REPAIR_HEALTHY -> 99.0
            DiagnosticTruthStatus.REQUIRES_PHYSICAL_BENCH_TEST -> 75.0
            DiagnosticTruthStatus.OBSERVED_ANOMALY -> 60.0
            DiagnosticTruthStatus.UNVERIFIED_PRELIMINARY_HYPOTHESIS -> 40.0
        }

        return GuidedDiagnosticPlan(
            dtcCode = cleanCode,
            primaryHypothesis = hypothesis,
            truthStatus = truthStatus,
            confidence = confidence,
            steps = steps,
            requiresPhysicalVerification = (physicalTestResultPassed == null),
        )
    }
}
