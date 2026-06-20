package com.elysium369.meet.core.obd

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
data class VehicleInspectionReport(
    val inspectionId: String,
    val vehicleId: String,
    val vin: String,
    val score0to100: Int,
    val criticalIssues: List<String>,
    val warnings: List<String>,
    val estimatedRepairCost: Double,
    val recommendation: String,
    val createdAt: Long,
    val category: String, // Excelente, Bueno, Requiere atención, Alto riesgo
    val dimensionsDetails: Map<String, String> // 10 dimensions detailed text
)

@Singleton
class MeetPerito @Inject constructor() {
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Realiza la evaluación clínica completa del vehículo en base a las 10 dimensiones OBD y genera el reporte.
     */
    fun performInspection(
        context: Context,
        vehicleId: String,
        vin: String?,
        activeDtcs: List<String>,
        pendingDtcs: List<String>,
        freezeFrame: Map<String, String>?,
        liveData: Map<String, Float>,
        odometerKmCluster: Long,
        readinessMonitors: Map<String, Boolean>
    ): VehicleInspectionReport {
        val criticalIssues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val details = mutableMapOf<String, String>()
        var score = 100
        var repairCost = 0.0

        val resolvedVin = vin ?: "DESCONOCIDO"

        // 1. Dimension VIN
        if (resolvedVin.length != 17 || resolvedVin.contains("?") || resolvedVin == "DESCONOCIDO") {
            score -= 5
            warnings.add("VIN no detectado o inválido: '$resolvedVin'")
            details["VIN"] = "❌ No detectado o formato inválido"
        } else {
            details["VIN"] = "✅ Detectado: $resolvedVin"
        }

        // 2. Dimension DTC Activos
        if (activeDtcs.isNotEmpty()) {
            val count = activeDtcs.size
            val penalty = (count * 15).coerceAtMost(40)
            score -= penalty
            criticalIssues.add("Códigos DTC activos: $count detectados (${activeDtcs.joinToString(", ")})")
            
            // Analyze specific high severity DTCs
            for (code in activeDtcs) {
                when {
                    code.startsWith("P03") -> {
                        repairCost += 150.0
                        criticalIssues.add("Falla de encendido (Misfire) detectada en cilindro ($code)")
                    }
                    code.startsWith("P0420") || code.startsWith("P0430") -> {
                        repairCost += 800.0
                        criticalIssues.add("Baja eficiencia del Catalizador ($code) - Reparación Costosa")
                    }
                    code.startsWith("P07") -> {
                        repairCost += 500.0
                        criticalIssues.add("Código en transmisión automática ($code)")
                    }
                    else -> {
                        repairCost += 120.0
                    }
                }
            }
            details["DTC_ACTIVOS"] = "❌ $count códigos activos detectados. Penalización -$penalty pts."
        } else {
            details["DTC_ACTIVOS"] = "✅ Sin códigos de falla activos."
        }

        // 3. Dimension DTC Pendientes
        if (pendingDtcs.isNotEmpty()) {
            val count = pendingDtcs.size
            val penalty = (count * 5).coerceAtMost(15)
            score -= penalty
            warnings.add("Códigos DTC pendientes: $count detectados (${pendingDtcs.joinToString(", ")})")
            repairCost += (count * 60.0)
            details["DTC_PENDIENTES"] = "⚠️ $count códigos pendientes. Penalización -$penalty pts."
        } else {
            details["DTC_PENDIENTES"] = "✅ Sin códigos pendientes."
        }

        // 4. Dimension Freeze Frame
        if (!freezeFrame.isNullOrEmpty()) {
            score -= 10
            warnings.add("Datos de Freeze Frame capturados en ECU")
            details["FREEZE_FRAME"] = "❌ Registrado en ECU. Indica falla recurrente. Penalización -10 pts."
        } else {
            details["FREEZE_FRAME"] = "✅ Sin capturas de Freeze Frame guardadas."
        }

        // 5. Fuel Trims (LTFT)
        val ltft = liveData["LTFT_B1"] ?: liveData["ltft_b1"] ?: liveData["0107"] ?: 0f
        val absLtft = abs(ltft)
        if (absLtft > 15f) {
            score -= 15
            criticalIssues.add("Fuel Trim fuera de rango (${String.format("%.1f", ltft)}%) - Mezcla muy pobre/rica")
            repairCost += 120.0
            details["FUEL_TRIMS"] = "❌ LTFT: ${String.format("%.1f", ltft)}% (Anómalo, normal ±10%). Penalización -15 pts."
        } else if (absLtft > 10f) {
            score -= 5
            warnings.add("Fuel Trim al límite (${String.format("%.1f", ltft)}%)")
            details["FUEL_TRIMS"] = "⚠️ LTFT: ${String.format("%.1f", ltft)}% (Ligeramente desviado). Penalización -5 pts."
        } else {
            details["FUEL_TRIMS"] = "✅ LTFT: ${String.format("%.1f", ltft)}% (Normal)."
        }

        // 6. Temperatura (Coolant Temp)
        val ect = liveData["COOLANT"] ?: liveData["coolant"] ?: liveData["0105"] ?: 90f
        if (ect > 115f) {
            score -= 30
            criticalIssues.add("Sobrecalentamiento severo del motor (${ect.roundToInt()}°C)")
            repairCost += 400.0
            details["TEMPERATURA"] = "❌ ECT: ${ect.roundToInt()}°C (Severo). Penalización -30 pts."
        } else if (ect > 105f) {
            score -= 15
            criticalIssues.add("Temperatura elevada del motor (${ect.roundToInt()}°C)")
            repairCost += 150.0
            details["TEMPERATURA"] = "❌ ECT: ${ect.roundToInt()}°C (Elevada). Penalización -15 pts."
        } else if (ect < 70f) {
            score -= 5
            warnings.add("Motor trabaja frío (${ect.roundToInt()}°C) - Termostato defectuoso")
            repairCost += 80.0
            details["TEMPERATURA"] = "⚠️ ECT: ${ect.roundToInt()}°C (Baja). Penalización -5 pts."
        } else {
            details["TEMPERATURA"] = "✅ ECT: ${ect.roundToInt()}°C (Normal, 75°C - 100°C)."
        }

        // 7. Voltaje
        val voltage = liveData["VOLTAGE"] ?: liveData["voltage"] ?: liveData["CTRL_VOLTAGE"] ?: liveData["ELM_VOLTAGE"] ?: liveData["0142"] ?: 13.8f
        if (voltage < 12.0f || voltage > 15.5f) {
            score -= 15
            criticalIssues.add("Voltaje de alternador/batería fuera de rango (${String.format("%.1f", voltage)}V)")
            repairCost += 200.0
            details["VOLTAJE"] = "❌ Voltaje: ${String.format("%.1f", voltage)}V (Muy bajo/alto). Penalización -15 pts."
        } else if (voltage < 13.2f || voltage > 14.9f) {
            score -= 5
            warnings.add("Voltaje subóptimo de carga (${String.format("%.1f", voltage)}V)")
            details["VOLTAJE"] = "⚠️ Voltaje: ${String.format("%.1f", voltage)}V (Carga débil/inestable). Penalización -5 pts."
        } else {
            details["VOLTAJE"] = "✅ Voltaje: ${String.format("%.1f", voltage)}V (Estable)."
        }

        // 8. Sensores Críticos (MAF / MAP / O2)
        val maf = liveData["MAF"] ?: liveData["maf"] ?: liveData["0110"]
        val map = liveData["MAP"] ?: liveData["map"] ?: liveData["010b"]
        val o2 = liveData["O2_B1S1"] ?: liveData["o2_b1s1"] ?: liveData["0114"]
        
        var sensorIssues = 0
        if (maf != null && maf < 1.0f) sensorIssues++
        if (map != null && map < 20f) sensorIssues++
        if (o2 != null && (o2 < 0.05f || o2 > 0.95f)) sensorIssues++ // stuck sensor

        if (sensorIssues > 0) {
            score -= 10
            warnings.add("Lecturas anómalas en sensores de admisión/combustión (MAF/MAP/O2)")
            repairCost += 150.0
            details["SENSORES"] = "❌ Sensores con valores anómalos. Penalización -10 pts."
        } else {
            details["SENSORES"] = "✅ Sensores MAF/MAP/O2 respondiendo normalmente."
        }

        // 9. Kilometraje OBD (vs Odometer Cluster)
        // Usualmente PID 01A6 o 0131 en OBD2
        val obdOdometer = liveData["DISTANCE_WITH_MIL"] ?: liveData["distance_with_mil"] ?: liveData["0131"] ?: -1f
        if (obdOdometer > 0f) {
            val diff = abs(odometerKmCluster - obdOdometer)
            if (diff > 15000f) {
                score -= 20
                criticalIssues.add("Discrepancia grave de kilometraje: Tablero ${odometerKmCluster}km vs ECU ${obdOdometer.toInt()}km - Posible Alteración")
                details["KILOMETRAJE"] = "❌ Discrepancia crítica (${diff.toInt()} km). Penalización -20 pts."
            } else {
                details["KILOMETRAJE"] = "✅ Consonancia entre Tablero (${odometerKmCluster}km) y OBD (${obdOdometer.toInt()}km)."
            }
        } else {
            // No se pudo leer kilometraje por OBD (suele ocurrir en autos antiguos)
            details["KILOMETRAJE"] = "ℹ️ Kilometraje en ECU no disponible (se asume tablero: ${odometerKmCluster}km)."
        }

        // 10. Estado General / Readiness Monitors
        val totalMonitors = readinessMonitors.size
        val incompleteMonitors = readinessMonitors.count { !it.value }
        if (incompleteMonitors > 2) {
            score -= 10
            warnings.add("Múltiples monitores de emisiones incompletos ($incompleteMonitors de $totalMonitors)")
            details["ESTADO_GENERAL"] = "⚠️ Monitores de preparación incompletos. Penalización -10 pts."
        } else {
            details["ESTADO_GENERAL"] = "✅ Monitores OBD listos para inspección de emisiones."
        }

        // Clamp score
        val finalScore = score.coerceIn(0, 100)

        // Categorize
        val category = when (finalScore) {
            in 90..100 -> "Excelente"
            in 80..89 -> "Bueno"
            in 60..79 -> "Requiere atención"
            else -> "Alto riesgo"
        }

        // Generate recommendations
        val recommendation = when {
            finalScore >= 90 -> "Vehículo en condiciones extraordinarias. Sin fallas mecánicas ni electrónicas activas. Compra altamente recomendada."
            finalScore >= 80 -> "Vehículo en buen estado general. Se identificaron advertencias menores. Recomendable proceder negociando costos menores."
            finalScore >= 60 -> "Atención: El vehículo presenta múltiples advertencias y fallas pendientes de reparación. Requiere mantenimiento a corto plazo."
            else -> "ALTO RIESGO: Se detectaron fallas críticas o discrepancias de kilometraje graves. Recomendamos suspender la compra o realizar una inspección física a fondo."
        }

        val report = VehicleInspectionReport(
            inspectionId = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            vin = resolvedVin,
            score0to100 = finalScore,
            criticalIssues = criticalIssues,
            warnings = warnings,
            estimatedRepairCost = repairCost,
            recommendation = recommendation,
            createdAt = System.currentTimeMillis(),
            category = category,
            dimensionsDetails = details
        )

        // Persist report as JSON locally
        saveReportToFile(context, report)

        return report
    }

    private fun saveReportToFile(context: Context, report: VehicleInspectionReport) {
        try {
            val dir = context.getExternalFilesDir("PeritoInspections")
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "Inspection_${report.inspectionId}.json")
            val jsonStr = json.encodeToString(report)
            file.writeText(jsonStr)
            Log.d("MeetPerito", "Saved inspection report to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MeetPerito", "Failed to save inspection report JSON", e)
        }
    }

    /**
     * Recupera el historial de reportes locales para un vehículo específico.
     */
    fun getInspectionHistory(context: Context, vehicleId: String): List<VehicleInspectionReport> {
        val list = mutableListOf<VehicleInspectionReport>()
        try {
            val dir = context.getExternalFilesDir("PeritoInspections") ?: return emptyList()
            val files = dir.listFiles { _, name -> name.startsWith("Inspection_") && name.endsWith(".json") }
            if (files != null) {
                for (file in files) {
                    val content = file.readText()
                    val rep = json.decodeFromString<VehicleInspectionReport>(content)
                    if (rep.vehicleId == vehicleId) {
                        list.add(rep)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MeetPerito", "Failed to retrieve inspection history", e)
        }
        return list.sortedByDescending { it.createdAt }
    }
}
