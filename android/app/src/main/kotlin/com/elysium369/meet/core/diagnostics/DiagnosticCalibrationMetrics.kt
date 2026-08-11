package com.elysium369.meet.core.diagnostics

import kotlin.math.abs

data class VerifiedDiagnosticPrediction(
    val caseId: String,
    val predictedRootCauses: List<Pair<String, Double>>,
    val verifiedRootCauseId: String,
)

data class DiagnosticCalibrationReport(
    val sampleCount: Int,
    val brierScore: Double?,
    val expectedCalibrationError: Double?,
    val precisionAtOne: Double?,
    val recallAtOne: Double?,
    val topKRootCauseRecall: Map<Int, Double>,
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
                0, null, null, null, null, emptyMap(), false,
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
        val precision = correctAtOne.toDouble() / valid.size
        val recall = precision
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
        val topKRecall = topK.filter { it > 0 }.sorted().associateWith { k ->
            valid.count { case ->
                case.predictedRootCauses.sortedByDescending { it.second }.take(k)
                    .any { it.first == case.verifiedRootCauseId }
            }.toDouble() / valid.size
        }
        val publishable = valid.size >= MIN_PUBLISHABLE_CASES
        return DiagnosticCalibrationReport(
            sampleCount = valid.size,
            brierScore = brier,
            expectedCalibrationError = ece,
            precisionAtOne = precision,
            recallAtOne = recall,
            topKRootCauseRecall = topKRecall,
            statisticallyPublishable = publishable,
            reason = if (publishable) {
                "Dataset suficiente para revisión estadística; publicación aún requiere aprobación metodológica."
            } else {
                "Muestra insuficiente: usar prioridad cualitativa HIGH/MEDIUM/LOW."
            },
        )
    }
}
