package com.elysium369.meet.core.obd

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataLogger — Graba telemetría OBD en CSV de alta velocidad.
 * Formato compatible con Excel, Google Sheets, y herramientas de análisis.
 */
@Singleton
class DataLogger @Inject constructor() {

    data class LogSession(
        val startTime: Long,
        val fileName: String,
        val sampleCount: Int,
        val durationMs: Long,
        val pidsRecorded: Set<String>,
        val filePath: String
    )

    private var isRecording = false
    private var writer: FileWriter? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private var sampleCount: Int = 0
    private var headers: List<String> = emptyList()
    private var headersWritten = false
    private var recordedPids: MutableSet<String> = mutableSetOf()

    val recording: Boolean get() = isRecording

    fun startRecording(context: Context, sessionIdentifier: String? = null): Boolean {
        if (isRecording) return false
        try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ElysiumVanguard_Logs")
            dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val sessionLabel = sessionIdentifier?.let { 
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(it.toByteArray(Charsets.UTF_8))
                    .take(4)
                    .joinToString("") { b -> "%02x".format(b) }
            } ?: UUID.randomUUID().toString().take(8)
            val file = File(dir, "ElysiumVanguard_Log_${sessionLabel}_$timestamp.csv")
            writer = FileWriter(file)
            currentFile = file
            startTimeMs = System.currentTimeMillis()
            sampleCount = 0
            headersWritten = false
            recordedPids.clear()
            isRecording = true
            return true
        } catch (e: Exception) {
            return false
        }
    }


    fun recordSample(liveData: Map<String, Float>) {
        if (!isRecording || writer == null) return
        try {
            val sortedKeys = liveData.keys.sorted()
            // Write headers on first sample
            if (!headersWritten) {
                headers = sortedKeys
                writer?.write("Timestamp_ms,Elapsed_s,${headers.joinToString(",")}\n")
                headersWritten = true
            }
            recordedPids.addAll(sortedKeys)
            val now = System.currentTimeMillis()
            val elapsed = String.format("%.3f", (now - startTimeMs) / 1000.0)
            val values = headers.map { key ->
                liveData[key]?.let { String.format("%.4f", it) } ?: ""
            }
            writer?.write("$now,$elapsed,${values.joinToString(",")}\n")
            sampleCount++
            // Flush every 50 samples for safety
            if (sampleCount % 50 == 0) writer?.flush()
        } catch (_: Exception) { }
    }

    fun stopRecording(): LogSession? {
        if (!isRecording) return null
        isRecording = false
        val endTime = System.currentTimeMillis()
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) { }
        writer = null
        val file = currentFile ?: return null
        return LogSession(
            startTime = startTimeMs,
            fileName = file.name,
            sampleCount = sampleCount,
            durationMs = endTime - startTimeMs,
            pidsRecorded = recordedPids.toSet(),
            filePath = file.absolutePath
        )
    }

    fun getLogFiles(context: Context): List<File> {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ElysiumVanguard_Logs")
        return dir.listFiles()?.filter { it.extension == "csv" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun writeDtcScanReport(context: Context, report: DtcScanReport, vin: String? = null): String? {
        return try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ElysiumVanguard_DTC_Logs")
            dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(report.startedAtMs))
            val vinLabel = vin?.takeIf { it.isNotBlank() }?.take(8) ?: "UNKNOWN"
            val file = File(dir, "ElysiumVanguard_DTC_${vinLabel}_$timestamp.txt")

            FileWriter(file).use { writer ->
                writer.write("OBD2 Elysium Vanguard Professional DTC Scan\n")
                writer.write("Started: ${Date(report.startedAtMs)}\n")
                writer.write("Ended: ${Date(report.endedAtMs)}\n")
                writer.write("Protocol: ${report.protocol}\n")
                writer.write("Records: ${report.records.size}\n")
                writer.write("Completeness: ${report.completeness}\n")
                if (report.warnings.isNotEmpty()) {
                    writer.write("Warnings: ${report.warnings.joinToString(" | ")}\n")
                }
                writer.write("\n== Module Coverage ==\n")
                report.modules.forEach { module ->
                    writer.write(
                        "module=${module.moduleName}, target=${module.targetAddress ?: "-"}, " +
                            "response=${module.responseAddress ?: "-"}, alive=${module.isAlive}, outcome=${module.outcome}\n"
                    )
                    module.serviceReads.forEach { read ->
                        writer.write(
                            "  command=${read.command}, namespace=${read.coverage.namespace}, " +
                                "semantics=${read.coverage.semantics.joinToString("|")}, " +
                                "outcome=${read.outcome}, nrc=${read.negativeResponse?.responseCode ?: "-"}\n"
                        )
                    }
                }
                writer.write("\n== Parsed DTCs ==\n")
                report.records.forEach { record ->
                    writer.write(
                        listOf(
                            record.code,
                            "bucket=${record.bucket}",
                            "flags=${record.statusFlags.joinToString("|")}",
                            "service=${record.sourceService}",
                            "target=${record.targetAddress ?: "-"}",
                            "response=${record.responseAddress ?: "-"}",
                            "module=${record.moduleName ?: "-"}",
                            "udsStatus=${record.udsStatusByte?.let { String.format("0x%02X", it) } ?: "-"}",
                            "failureType=${record.udsFailureType ?: "-"}"
                        ).joinToString(", ")
                    )
                    writer.write("\n")
                }

                writer.write("\n== Raw OBD Exchanges ==\n")
                report.rawExchanges.forEach { exchange ->
                    writer.write("[${Date(exchange.timestampMs)}] target=${exchange.targetAddress ?: "-"} cmd=${exchange.command} parsed=${exchange.parsedRecordCount} outcome=${exchange.outcome}\n")
                    writer.write(exchange.rawResponse.ifBlank { "<empty>" }.trim())
                    writer.write("\n---\n")
                }
            }

            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
