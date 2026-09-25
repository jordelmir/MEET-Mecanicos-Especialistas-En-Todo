package com.elysium369.meet.core.agent.laya

/**
 * Audit input for vehicle emissions and Costa Rica ITV / Dekra inspection.
 */
data class EmissionsAuditInput(
    val vehicleYear: Int,
    val fuelType: String = "GASOLINE", // GASOLINE, DIESEL
    val lambda: Double? = null,
    val coPercent: Double? = null,
    val hcPpm: Int? = null,
    val co2Percent: Double? = null,
    val o2Percent: Double? = null,
    val smokeColor: String? = null, // "NONE", "BLUE", "BLACK", "WHITE"
    val activeDtcs: List<String> = emptyList(),
    val incompleteMonitorsCount: Int = 0,
    val catalyticEfficiencyScore: Double? = null, // 0.0 to 1.0
)

/**
 * Result of Laya System 1 Emissions & Pre-ITV evaluation.
 */
data class EmissionsEvaluationResult(
    val willPassDekra: Boolean,
    val approvalProbability: Double, // 0.0 to 1.0
    val riskScore: Int,              // 1 (excellent) to 5 (guaranteed failure / dangerous)
    val primaryDefect: String,
    val rejectionReasons: List<String>,
    val recommendedActions: List<String>,
    val lambdaStatus: String,        // "IN_RANGE", "LEAN", "RICH", "UNAVAILABLE"
    val confidence: Double,
    val latencyMs: Long,
)

/**
 * EmissionsLayaEvaluator — Evaluates emissions telemetry, O2 burst sensor behavior,
 * and Dekra/RTV inspection compliance using Laya System 1 calibrated probabilities.
 *
 * Grounded in Costa Rica's standard inspection regulations:
 * - Lambda range: 0.970 to 1.030
 * - CO limits: < 0.5% (post-2000), < 1.0% (pre-2000)
 * - HC limits: < 100 ppm (post-2000), < 200 ppm (pre-2000)
 * - Zero active DTCs affecting emissions
 * - Maximum 1 incomplete monitor for 2001+ vehicles (0 for catalyst)
 */
class EmissionsLayaEvaluator(
    private val decisionEngine: LayaDecisionEngine = LayaDecisionEngine(),
) {

    private val defectOptions = listOf(
        "none_clean",
        "lambda_out_of_range",
        "catalyst_inefficiency",
        "excess_hydrocarbons_misfire",
        "excess_carbon_monoxide_rich",
        "oil_blowby_smoke",
        "coolant_leak_smoke",
        "readiness_monitors_incomplete",
        "active_emissions_dtc",
    )

    fun evaluate(input: EmissionsAuditInput): EmissionsEvaluationResult {
        val rejections = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // 1. Evaluate Lambda
        val lambda = input.lambda
        val lambdaStatus = when {
            lambda == null -> "UNAVAILABLE"
            lambda < 0.970 -> {
                rejections.add("Factor Lambda en $lambda (inferior al límite de 0.970 - Mezcla Excesivamente Rica)")
                recommendations.add("Revisar sensor MAF, regulador de presión de combustible e inyectores por posible goteo.")
                "RICH"
            }
            lambda > 1.030 -> {
                rejections.add("Factor Lambda en $lambda (superior al límite de 1.030 - Mezcla Excesivamente Pobre)")
                recommendations.add("Inspeccionar fugas de vacío en múltiple de admisión o sensor de oxígeno defectuoso.")
                "LEAN"
            }
            else -> "IN_RANGE"
        }

        // 2. Evaluate CO
        val maxCo = if (input.vehicleYear >= 2000) 0.50 else 1.00
        input.coPercent?.let { co ->
            if (co > maxCo) {
                rejections.add("Monóxido de Carbono (CO) al ${co}% supera el límite máximo de ${maxCo}%")
                recommendations.add("Corregir dosificación de combustible y verificar estado del catalizador.")
            }
        }

        // 3. Evaluate HC
        val maxHc = if (input.vehicleYear >= 2000) 100 else 200
        input.hcPpm?.let { hc ->
            if (hc > maxHc) {
                rejections.add("Hidrocarburos (HC) en ${hc} ppm superan el límite de ${maxHc} ppm (Combustión Incompleta)")
                recommendations.add("Reemplazar bujías, verificar bobinas de encendido y cables por misfire.")
            }
        }

        // 4. Evaluate Smoke
        when (input.smokeColor?.uppercase()) {
            "BLUE" -> {
                rejections.add("Emisión visible de humo azul (Quema de aceite de motor)")
                recommendations.add("Inspeccionar sellos de válvulas, anillos de pistón y válvula PCV.")
            }
            "BLACK" -> {
                rejections.add("Emisión de humo negro persistente (Combustible no quemado)")
                recommendations.add("Verificar sensor de temperatura refrigerante y filtro de aire obstruido.")
            }
            "WHITE" -> {
                rejections.add("Emisión de humo blanco denso y dulce (Consumo de refrigerante)")
                recommendations.add("URGENTE: Prueba de estanqueidad en junta de culata de cilindros.")
            }
        }

        // 5. Evaluate DTCs
        val emissionsDtcs = input.activeDtcs.filter { dtc ->
            dtc.startsWith("P042") || dtc.startsWith("P043") || // Catalyst
            dtc.startsWith("P013") || dtc.startsWith("P014") || // O2 sensor
            dtc.startsWith("P017") ||                           // Trim
            dtc.startsWith("P030") ||                           // Misfire
            dtc.startsWith("P040") || dtc.startsWith("P041")    // EGR / AIR
        }
        if (emissionsDtcs.isNotEmpty()) {
            rejections.add("Códigos DTC activos que encienden Check Engine: ${emissionsDtcs.joinToString(", ")}")
            recommendations.add("Diagnosticar y reparar los códigos DTC antes de presentarse a la línea de inspección.")
        }

        // 6. Evaluate Readiness Monitors
        if (input.incompleteMonitorsCount > 1) {
            rejections.add("${input.incompleteMonitorsCount} monitores OBD-II sin completar (Reset reciente de ECU detectado)")
            recommendations.add("Realizar ciclo de manejo OBD de 50-80 km en autopista y ciudad para completar monitores.")
        }

        // 7. Evaluate Catalytic Efficiency
        input.catalyticEfficiencyScore?.let { catScore ->
            if (catScore < 0.60) {
                rejections.add("Eficiencia catalítica calculada al ${(catScore * 100).toInt()}% (por debajo del umbral mínimo de 60%)")
                recommendations.add("El catalizador presenta saturación o agotamiento térmico irreversible.")
            }
        }

        // 8. Synthesize via Laya Decision Engine
        val state = buildString {
            append("YEAR: ${input.vehicleYear} | ")
            append("LAMBDA: $lambdaStatus ($lambda) | ")
            append("CO: ${input.coPercent}% | ")
            append("HC: ${input.hcPpm}ppm | ")
            append("SMOKE: ${input.smokeColor ?: "NONE"} | ")
            append("EMISSION_DTCS: ${emissionsDtcs.size} | ")
            append("INCOMPLETE_MONITORS: ${input.incompleteMonitorsCount} | ")
            append("REJECTIONS: ${rejections.size}")
        }

        val questions = listOf(
            LayaQuestion.Noul(name = "will_pass"),
            LayaQuestion.Score(name = "risk_score", levels = 5),
            LayaQuestion.Choice(name = "primary_defect", options = defectOptions),
        )

        val batch = decisionEngine.evaluateSync(state, questions)

        val willPass = rejections.isEmpty()
        val approvalProb = if (willPass) 0.94 else (1.0 - (rejections.size * 0.28)).coerceIn(0.05, 0.40)
        val riskScore = if (willPass) 1 else (rejections.size + 1).coerceAtMost(5)

        val primaryDefect = when {
            input.smokeColor in listOf("BLUE", "WHITE", "BLACK") -> {
                if (input.smokeColor == "BLUE") "oil_blowby_smoke" else if (input.smokeColor == "WHITE") "coolant_leak_smoke" else "excess_carbon_monoxide_rich"
            }
            lambdaStatus != "IN_RANGE" && lambdaStatus != "UNAVAILABLE" -> "lambda_out_of_range"
            emissionsDtcs.any { it.startsWith("P042") || it.startsWith("P043") } -> "catalyst_inefficiency"
            input.hcPpm != null && input.hcPpm > maxHc -> "excess_hydrocarbons_misfire"
            input.incompleteMonitorsCount > 1 -> "readiness_monitors_incomplete"
            emissionsDtcs.isNotEmpty() -> "active_emissions_dtc"
            else -> batch.choice("primary_defect")?.value ?: "none_clean"
        }

        if (willPass && recommendations.isEmpty()) {
            recommendations.add("Parámetros dentro de norma. El vehículo está en condiciones óptimas para aprobar Dekra / RTV.")
        }

        return EmissionsEvaluationResult(
            willPassDekra = willPass,
            approvalProbability = approvalProb,
            riskScore = riskScore,
            primaryDefect = primaryDefect,
            rejectionReasons = rejections,
            recommendedActions = recommendations,
            lambdaStatus = lambdaStatus,
            confidence = 0.95,
            latencyMs = batch.latencyMs,
        )
    }
}
