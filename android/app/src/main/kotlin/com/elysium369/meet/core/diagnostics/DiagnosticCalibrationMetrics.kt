package com.elysium369.meet.core.diagnostics

import kotlin.math.abs
import kotlin.math.sqrt

enum class CalibrationDatasetPartition { TRAINING, CALIBRATION, HOLDOUT }

data class VerifiedDiagnosticPrediction(
    val caseId: String,
    val predictedRootCauses: List<Pair<String, Double>>,
    val verifiedRootCauseId: String,
    val partition: CalibrationDatasetPartition = CalibrationDatasetPartition.HOLDOUT,
    val manufacturer: String? = null,
    val vehicleFamily: String? = null,
    val powertrain: String? = null,
    val dtcFamily: String? = null,
    val vehicleAgeYears: Int? = null,
    val market: String? = null,
)

data class MetricConfidenceInterval(
    val lower95: Double,
    val upper95: Double,
)

data class DiagnosticCalibrationReport(
    val sampleCount: Int,
    val brierScore: Double?,
    val expectedCalibrationError: Double?,
    val top1Accuracy: Double?,
    val macroPrecision: Double?,
    val macroRecall: Double?,
    val macroF1: Double?,
    val topKRootCauseRecall: Map<Int, Double>,
    val confidenceIntervals: Map<String, MetricConfidenceInterval>,
    val stratificationCoverage: Map<String, Double>,
    val statisticallyPublishable: Boolean,
    val reason: String,
)

/** Metrics authority; percentages remain hidden until a reviewed dataset is sufficiently large. */
object DiagnosticCalibrationMetrics {
    const val MIN_PUBLISHABLE_CASES = 200

    fun evaluate(
        cases: List<VerifiedDiagnosticPrediction>,
        bins: Int = 10,
        topK: Set<Int> = setOf(1, 3, 5),
    ): DiagnosticCalibrationReport {
        val valid = cases.filter { case ->
            case.verifiedRootCauseId.isNotBlank() &&
                case.predictedRootCauses.isNotEmpty() &&
                case.predictedRootCauses.map { it.first }.distinct().size == case.predictedRootCauses.size &&
                case.predictedRootCauses.any { it.first == case.verifiedRootCauseId } &&
                case.predictedRootCauses.all { (id, probability) -> id.isNotBlank() && probability in 0.0..1.0 } &&
                case.predictedRootCauses.sumOf { it.second } in 0.999..1.001
        }
        if (valid.isEmpty()) {
            return DiagnosticCalibrationReport(
                0, null, null, null, null, null, null, emptyMap(), emptyMap(), emptyMap(), false,
                "Sin casos de reparación verificados y probabilidades válidas.",
            )
        }
        val brier = valid.map { case ->
            case.predictedRootCauses.sumOf { (id, probability) ->
                val actual = if (id == case.verifiedRootCauseId) 1.0 else 0.0
                (probability - actual) * (probability - actual)
            }
        }.average()
        val topPredictions = valid.map { it.predictedRootCauses.maxBy { prediction -> prediction.second } }
        val correctAtOne = valid.indices.count { index ->
            topPredictions[index].first == valid[index].verifiedRootCauseId
        }
        val accuracy = correctAtOne.toDouble() / valid.size
        val labels = (valid.map { it.verifiedRootCauseId } + topPredictions.map { it.first }).toSortedSet()
        val perClass = labels.map { label ->
            val truePositive = valid.indices.count { index ->
                topPredictions[index].first == label && valid[index].verifiedRootCauseId == label
            }
            val predictedPositive = topPredictions.count { it.first == label }
            val actualPositive = valid.count { it.verifiedRootCauseId == label }
            val precision = if (predictedPositive == 0) 0.0 else truePositive.toDouble() / predictedPositive
            val recall = if (actualPositive == 0) 0.0 else truePositive.toDouble() / actualPositive
            val f1 = if (precision + recall == 0.0) 0.0 else 2.0 * precision * recall / (precision + recall)
            Triple(precision, recall, f1)
        }
        val macroPrecision = perClass.map { it.first }.average()
        val macroRecall = perClass.map { it.second }.average()
        val macroF1 = perClass.map { it.third }.average()
        val binCount = bins.coerceAtLeast(2)
        val ece = (0 until binCount).sumOf { bin ->
            val lower = bin.toDouble() / binCount
            val upper = (bin + 1).toDouble() / binCount
            val members = valid.indices.filter { index ->
                val confidence = topPredictions[index].second
                confidence >= lower && (confidence < upper || bin == binCount - 1 && confidence <= upper)
            }
            if (members.isEmpty()) return@sumOf 0.0
            val averageConfidence = members.map { topPredictions[it].second }.average()
            val accuracy = members.count {
                topPredictions[it].first == valid[it].verifiedRootCauseId
            }.toDouble() / members.size
            members.size.toDouble() / valid.size * abs(accuracy - averageConfidence)
        }
        val topKSuccesses = topK.filter { it > 0 }.sorted().associateWith { k ->
            valid.count { case ->
                case.predictedRootCauses.sortedByDescending { it.second }.take(k)
                    .any { it.first == case.verifiedRootCauseId }
            }
        }
        val topKRecall = topKSuccesses.mapValues { (_, successes) -> successes.toDouble() / valid.size }
        val coverage = mapOf(
            "manufacturer" to valid.count { !it.manufacturer.isNullOrBlank() }.toDouble() / valid.size,
            "vehicleFamily" to valid.count { !it.vehicleFamily.isNullOrBlank() }.toDouble() / valid.size,
            "powertrain" to valid.count { !it.powertrain.isNullOrBlank() }.toDouble() / valid.size,
            "dtcFamily" to valid.count { !it.dtcFamily.isNullOrBlank() }.toDouble() / valid.size,
            "vehicleAge" to valid.count { it.vehicleAgeYears != null }.toDouble() / valid.size,
            "market" to valid.count { !it.market.isNullOrBlank() }.toDouble() / valid.size,
        )
        val holdoutOnly = valid.all { it.partition == CalibrationDatasetPartition.HOLDOUT }
        val stratifiedEnough = coverage.values.all { it >= 0.9 }
        val publishable = valid.size >= MIN_PUBLISHABLE_CASES && holdoutOnly && stratifiedEnough
        return DiagnosticCalibrationReport(
            sampleCount = valid.size,
            brierScore = brier,
            expectedCalibrationError = ece,
            top1Accuracy = accuracy,
            macroPrecision = macroPrecision,
            macroRecall = macroRecall,
            macroF1 = macroF1,
            topKRootCauseRecall = topKRecall,
            confidenceIntervals = buildMap {
                put("top1Accuracy", wilson(correctAtOne, valid.size))
                topKSuccesses.forEach { (k, successes) ->
                    put("top${k}Recall", wilson(successes, valid.size))
                }
            },
            stratificationCoverage = coverage,
            statisticallyPublishable = publishable,
            reason = if (publishable) {
                "Dataset suficiente para revisión estadística; publicación aún requiere aprobación metodológica."
            } else {
                "Gate metodológico incompleto: requiere holdout, muestra mínima y cobertura estratificada; usar prioridad cualitativa."
            },
        )
    }

    private fun wilson(successes: Int, total: Int): MetricConfidenceInterval {
        if (total <= 0) return MetricConfidenceInterval(0.0, 1.0)
        val z = 1.959963984540054
        val n = total.toDouble()
        val p = successes.toDouble() / n
        val denominator = 1.0 + z * z / n
        val center = (p + z * z / (2.0 * n)) / denominator
        val margin = z * sqrt((p * (1.0 - p) + z * z / (4.0 * n)) / n) / denominator
        return MetricConfidenceInterval((center - margin).coerceAtLeast(0.0), (center + margin).coerceAtMost(1.0))
    }
}
