package com.elysium369.meet.core.obd.audit

import android.util.Log
import com.elysium369.meet.core.obd.model.AdapterChipFamily
import com.elysium369.meet.core.obd.model.ObdAdapterMatrix
import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.math.sqrt

@Serializable
data class ObdHandshakeProbe(
    val command: String,
    val rttMs: Long,
    val response: String,
    val isSuccess: Boolean,
)

@Serializable
data class ObdHandshakeAuditReport(
    val deviceAddress: String,
    val deviceName: String,
    val detectedFamily: AdapterChipFamily,
    val chipVersion: String,
    val isClone: Boolean,
    val voltage: Float?,
    val minLatencyMs: Long,
    val avgLatencyMs: Long,
    val maxLatencyMs: Long,
    val jitterMs: Long,
    val samplesCount: Int,
    val supportsCanFd: Boolean,
    val supportsIsoTp: Boolean,
    val probes: List<ObdHandshakeProbe>,
    val auditTimestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): String {
        return try {
            Json { prettyPrint = false; ignoreUnknownKeys = true }.encodeToString(this)
        } catch (_: Exception) {
            """{"deviceAddress":"$deviceAddress","family":"${detectedFamily.name}","avgLatencyMs":$avgLatencyMs}"""
        }
    }
}

/**
 * ObdHandshakeAuditor — Audits round-trip timing, clone signatures, STN architecture,
 * and bus voltage for OBD-II adapters.
 */
class ObdHandshakeAuditor(
    private val transport: TransportInterface,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {

    private val TAG = "EV_OBD_AUDITOR"

    suspend fun performAudit(
        deviceAddress: String,
        deviceName: String,
        onProgress: ((String) -> Unit)? = null
    ): ObdHandshakeAuditReport = withContext(ioDispatcher) {
        val probes = mutableListOf<ObdHandshakeProbe>()
        onProgress?.invoke("Iniciando auditoría de latencia y handshake...")
        Log.i(TAG, "Starting handshake audit for adapter $deviceAddress ($deviceName)")

        transport.drain()
        for (i in 1..2) {
            transport.write("\r".toByteArray())
            delay(25)
        }
        transport.drain()

        // 1. Probe ATZ (Reset & Banner)
        onProgress?.invoke("Midiendo tiempo de respuesta inicial (ATZ)...")
        val atzProbe = executeProbe("ATZ\r", 2500)
        probes.add(atzProbe)
        delay(300) // Post-reset stabilization
        transport.drain()

        // 2. Probe Essential Config Commands
        val configCommands = listOf("ATE0\r", "ATL0\r", "ATS0\r", "ATH0\r")
        for (cmd in configCommands) {
            val probe = executeProbe(cmd, 600)
            probes.add(probe)
            delay(20)
        }

        // 3. Probe ATI (Chip Identification)
        onProgress?.invoke("Verificando firma de microcontrolador (ATI)...")
        val atiProbe = executeProbe("ATI\r", 800)
        probes.add(atiProbe)

        // 4. Probe ATRV (Voltage)
        onProgress?.invoke("Midiendo voltaje de línea (ATRV)...")
        val atrvProbe = executeProbe("ATRV\r", 600)
        probes.add(atrvProbe)

        // 5. Probe STI (STN Architecture Identification)
        onProgress?.invoke("Comprobando coprocesador STN / vLinker (STI)...")
        val stiProbe = executeProbe("STI\r", 600)
        probes.add(stiProbe)

        // 6. Probe ATDPN (Protocol query)
        val atdpnProbe = executeProbe("ATDPN\r", 600)
        probes.add(atdpnProbe)

        // Evaluate responses
        val chipVersion = when {
            atiProbe.isSuccess && atiProbe.response.isNotBlank() -> atiProbe.response
            atzProbe.isSuccess && atzProbe.response.isNotBlank() -> atzProbe.response
            else -> "ELM327"
        }

        val stiResponse = if (stiProbe.isSuccess) stiProbe.response else ""
        val capabilityProfile = ObdAdapterMatrix.resolveProfile(
            chipVersion = chipVersion,
            deviceName = deviceName,
            stiResponse = stiResponse
        )

        val parsedVoltage = parseVoltage(atrvProbe.response)

        // Compute Latency Metrics across valid probes (excluding ATZ reset which contains baud re-sync delay)
        val benchmarkProbes = probes.filter { it.command.trim() != "ATZ" && it.isSuccess && it.rttMs > 0 }
        val rtts = benchmarkProbes.map { it.rttMs }

        val minLatency = rtts.minOrNull() ?: capabilityProfile.defaultCommandDelayMs
        val maxLatency = rtts.maxOrNull() ?: capabilityProfile.defaultCommandDelayMs
        val avgLatency = if (rtts.isNotEmpty()) (rtts.average()).roundToLong() else capabilityProfile.defaultCommandDelayMs
        val jitter = calculateJitter(rtts)

        val report = ObdHandshakeAuditReport(
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            detectedFamily = capabilityProfile.family,
            chipVersion = chipVersion,
            isClone = capabilityProfile.isClone,
            voltage = parsedVoltage,
            minLatencyMs = minLatency,
            avgLatencyMs = avgLatency,
            maxLatencyMs = maxLatency,
            jitterMs = jitter,
            samplesCount = rtts.size,
            supportsCanFd = capabilityProfile.supportsCanFd,
            supportsIsoTp = capabilityProfile.supportsIsoTp,
            probes = probes,
            auditTimestamp = System.currentTimeMillis(),
        )

        Log.i(TAG, "Audit Complete: family=${report.detectedFamily}, avgRTT=${report.avgLatencyMs}ms, jitter=${report.jitterMs}ms, voltage=${report.voltage}V")
        onProgress?.invoke("Auditoría completada: ${report.detectedFamily.displayName} (RTT ${report.avgLatencyMs}ms)")
        report
    }

    private suspend fun executeProbe(cmd: String, timeoutMs: Long): ObdHandshakeProbe {
        val startNs = System.nanoTime()
        return try {
            transport.write(cmd.toByteArray())
            val buffer = StringBuilder()
            val startTime = System.currentTimeMillis()
            val cleanCmd = cmd.trim()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val chunk = transport.read(1024, timeoutMs = 30)
                if (chunk != null) {
                    val part = String(chunk, Charsets.ISO_8859_1)
                    buffer.append(part)
                    val text = buffer.toString()
                    if (text.contains(">") || text.contains("OK") || text.contains("?")) {
                        break
                    }
                }
            }

            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000).coerceAtLeast(1)
            var response = buffer.toString().trim()
            if (response.startsWith(cleanCmd, ignoreCase = true)) {
                response = response.substring(cleanCmd.length).trim()
            }
            response = response.replace(">", "").replace("\r", "").replace("\n", " ").trim()

            val isSuccess = response.isNotBlank() && !response.contains("?") && !response.contains("BUFFER FULL")
            ObdHandshakeProbe(
                command = cleanCmd,
                rttMs = elapsedMs,
                response = response,
                isSuccess = isSuccess
            )
        } catch (e: Exception) {
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000).coerceAtLeast(1)
            ObdHandshakeProbe(
                command = cmd.trim(),
                rttMs = elapsedMs,
                response = "ERROR: ${e.message ?: "timeout"}",
                isSuccess = false
            )
        }
    }

    private fun parseVoltage(raw: String): Float? {
        return Regex("([0-9]{1,2}(?:\\.[0-9])?)\\s*V", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
            ?.takeIf { it in 4.0f..32.0f }
    }

    private fun calculateJitter(rtts: List<Long>): Long {
        if (rtts.size < 2) return 0L
        val mean = rtts.average()
        val variance = rtts.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).roundToLong()
    }
}
