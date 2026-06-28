package com.elysium369.meet.core.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PrintReportData(
    val workshopName: String,
    val workshopAddress: String,
    val workshopPhone: String,
    val workshopEmail: String,
    val vehicleInfo: String,
    val dtcs: List<String>,
    val healthScore: Int,
    val summary: String,
    val isPostScan: Boolean
)

class BluetoothPrinterManager(private val context: Context) {

    private val TAG = "EV_PRINTER"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        
        return adapter.bondedDevices.filter { device ->
            val deviceClass = device.bluetoothClass?.deviceClass ?: 0
            val name = device.name?.lowercase(Locale.getDefault()) ?: ""
            name.contains("printer") || name.contains("print") || name.contains("thermal") || 
            name.contains("mpt") || name.contains("pos") || deviceClass == 1664 || deviceClass == 1536
        }
    }

    suspend fun printReport(
        device: BluetoothDevice,
        data: PrintReportData,
        onProgress: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null
        
        try {
            onProgress("Conectando con la impresora...")
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (_: Exception) {}
            
            socket.connect()
            outputStream = socket.outputStream
            
            onProgress("Imprimiendo reporte...")
            
            val escPos = EscPosBuilder(outputStream)
            escPos.initPrinter()
            
            // Header (Branding)
            escPos.alignCenter()
            escPos.setTextSizeDouble()
            escPos.printLine(data.workshopName.ifBlank { "Elysium Vanguard DIAGNOSTIC" })
            escPos.setTextSizeNormal()
            
            if (data.workshopAddress.isNotBlank()) escPos.printLine(data.workshopAddress)
            if (data.workshopPhone.isNotBlank()) escPos.printLine("Tel: ${data.workshopPhone}")
            if (data.workshopEmail.isNotBlank()) escPos.printLine("Email: ${data.workshopEmail}")
            
            escPos.printSeparator()
            
            // Title
            escPos.boldOn()
            escPos.printLine(if (data.isPostScan) "REPORTE DE DIAGNOSTICO POST-SCAN" else "REPORTE DE DIAGNOSTICO PRE-SCAN")
            escPos.boldOff()
            
            val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            escPos.printLine("Fecha: $currentDate")
            
            escPos.printSeparator()
            
            // Vehicle Info
            escPos.alignLeft()
            escPos.boldOn()
            escPos.printLine("VEHICULO:")
            escPos.boldOff()
            escPos.printLine(data.vehicleInfo)
            
            escPos.printSeparator()
            
            // Health Score
            escPos.boldOn()
            escPos.printLine("ESTADO DE SALUD GENERAL:")
            escPos.boldOff()
            escPos.printLine("Puntuacion: ${data.healthScore}/100")
            
            val healthText = when {
                data.healthScore > 85 -> "EXCELENTE (Rendimiento optimo)"
                data.healthScore > 60 -> "MODERADO (Atencion preventiva)"
                else -> "CRITICO (Reparacion inmediata)"
            }
            escPos.printLine("Evaluacion: $healthText")
            
            escPos.printSeparator()
            
            // DTCs
            escPos.boldOn()
            escPos.printLine("CODIGOS DE FALLA (DTCs):")
            escPos.boldOff()
            
            if (data.dtcs.isEmpty()) {
                escPos.printLine("No se detectaron fallas activas.")
            } else {
                data.dtcs.forEach { dtc ->
                    escPos.printLine("- $dtc")
                }
            }
            
            escPos.printSeparator()
            
            // Summary / AI analysis
            if (data.summary.isNotBlank()) {
                escPos.boldOn()
                escPos.printLine("ANALISIS Y DIAGNOSTICO:")
                escPos.boldOff()
                val lines = wrapText(data.summary, 32)
                lines.forEach { line ->
                    escPos.printLine(line)
                }
                escPos.printSeparator()
            }
            
            // Footer
            escPos.alignCenter()
            escPos.boldOn()
            escPos.printLine("GRACIAS POR SU PREFERENCIA")
            escPos.boldOff()
            escPos.printLine("Certificado por Elysium Vanguard AI")
            
            escPos.feed(5)
            
            onProgress("Impresión completada.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error printing via Bluetooth", e)
            onProgress("Error: ${e.message ?: "Conexión fallida"}")
            false
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun wrapText(text: String, limit: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        
        for (word in words) {
            if (currentLine.length + word.length + 1 > limit) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
            }
            if (currentLine.isNotEmpty()) {
                currentLine.append(" ")
            }
            currentLine.append(word)
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    private class EscPosBuilder(private val outputStream: OutputStream) {
        
        fun initPrinter() {
            outputStream.write(byteArrayOf(0x1B, 0x40)) // ESC @
        }

        fun alignLeft() {
            outputStream.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0
        }

        fun alignCenter() {
            outputStream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1
        }

        fun alignRight() {
            outputStream.write(byteArrayOf(0x1B, 0x61, 0x02)) // ESC a 2
        }

        fun setTextSizeNormal() {
            outputStream.write(byteArrayOf(0x1D, 0x21, 0x00)) // GS ! 0
        }

        fun setTextSizeDouble() {
            outputStream.write(byteArrayOf(0x1D, 0x21, 0x11)) // GS ! 0x11 (double width + double height)
        }

        fun boldOn() {
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x01)) // ESC E 1
        }

        fun boldOff() {
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00)) // ESC E 0
        }

        fun printLine(text: String) {
            outputStream.write((text + "\n").toByteArray(Charsets.ISO_8859_1))
        }

        fun printSeparator() {
            printLine("--------------------------------")
        }

        fun feed(lines: Int) {
            for (i in 0 until lines) {
                outputStream.write(byteArrayOf(0x0A))
            }
        }
    }
}
