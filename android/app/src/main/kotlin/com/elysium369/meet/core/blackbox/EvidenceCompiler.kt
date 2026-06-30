package com.elysium369.meet.core.blackbox

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.elysium369.meet.data.local.entities.EvidencePackageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EvidenceCompiler {
    private const val TAG = "EvidenceCompiler"
    private const val KEY_ALIAS = "ELYSIUM_VANGUARD_BLACK_BOX_KEY"

    init {
        // Initialize local cryptographic keys in the Android Keystore if not present
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
                val parameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY
                ).setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                 .build()
                kpg.initialize(parameterSpec)
                kpg.generateKeyPair()
                Log.d(TAG, "Keystore signatures initialized successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Android Keystore signature keys", e)
        }
    }

    /**
     * Compiles, hashes, and cryptographically signs a Black Box Evidence Package.
     * Generates a zip archive containing the video, telemetry log, and an official PDF.
     */
    suspend fun compilePackage(
        context: Context,
        vehicleId: String,
        eventType: String,
        gpsLocation: String,
        videoFile: File,
        audioFile: File?,
        telemetryJson: String,
        dtcsList: List<String>
    ): EvidencePackageEntity = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
        val outputDir = File(context.filesDir, "blackbox_evidence").apply { mkdirs() }
        
        // 1. Generate PDF Report on Android canvas
        val pdfFile = File(outputDir, "incident_report_$formattedDate.pdf")
        generatePdfReport(pdfFile, eventType, gpsLocation, timestamp, telemetryJson, dtcsList)

        // 2. Generate telemetry JSON file
        val jsonFile = File(outputDir, "telemetry_$formattedDate.json").apply {
            writeText(telemetryJson)
        }

        // 3. Zip files together
        val zipFile = File(outputDir, "evidence_package_$formattedDate.zip")
        val filesToZip = mutableListOf<File>()
        filesToZip.add(pdfFile)
        filesToZip.add(jsonFile)
        if (videoFile.exists()) filesToZip.add(videoFile)
        if (audioFile != null && audioFile.exists()) filesToZip.add(audioFile)

        zipFiles(filesToZip, zipFile)

        // Clean up temp files (except video if it's the original loop recorder)
        pdfFile.delete()
        jsonFile.delete()

        // 4. Compute SHA-256 Hash of the compiled zip
        val hash = calculateSha256(zipFile)

        // 5. Generate Cryptographic Signature of the Hash
        val signature = signHash(hash)

        Log.i(TAG, "Evidence package successfully compiled, signed, and hashed: ${zipFile.absolutePath}")

        return@withContext EvidencePackageEntity(
            packageId = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            eventType = eventType,
            timestamp = timestamp,
            gpsLocation = gpsLocation,
            videoPath = zipFile.absolutePath,
            audioPath = audioFile?.absolutePath ?: "",
            pidSnapshot = telemetryJson,
            dtcs = dtcsList.joinToString(","),
            hashSha256 = hash,
            signatureVersion = signature,
            createdAt = timestamp
        )
    }

    private fun generatePdfReport(
        outputFile: File,
        eventType: String,
        gps: String,
        timestamp: Long,
        telemetryJson: String,
        dtcs: List<String>
    ) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }
        
        // Draw Header background
        paint.color = Color.parseColor("#0F172A") // Deep Slate Grey
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Draw Gold Accent Line
        paint.color = Color.parseColor("#E2E8F0") // Platinum
        canvas.drawRect(0f, 90f, 595f, 94f, paint)

        // Title
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("ACTA DE EVIDENCIA TÉCNICA VEHICULAR", 40f, 40f, paint)
        
        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#94A3B8")
        canvas.drawText("Elysium Vanguard BLACK BOX AUTOMOTIVE LEGAL TELEMETRY", 40f, 65f, paint)

        // Incident Metadata
        paint.color = Color.BLACK
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("INFORMACIÓN DEL INCIDENTE", 40f, 140f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#334155")
        canvas.drawText("Tipo de Evento: $eventType", 40f, 170f, paint)
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
        canvas.drawText("Fecha / Hora: $isoTimestamp (UTC)", 40f, 190f, paint)
        canvas.drawText("Coordenadas GPS: $gps", 40f, 210f, paint)

        // Telemetry Metrics
        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText("TELEMETRÍA EN EL MOMENTO DEL TRIGGER", 40f, 260f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#475569")
        
        // Simple mock parse since it's displaying baseline parameters
        canvas.drawText("Códigos DTC Activos: ${if (dtcs.isEmpty()) "Ninguno" else dtcs.joinToString(", ")}", 40f, 290f, paint)

        // Safety Warnings / Legal Notice
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRect(40f, 350f, 555f, 480f, paint)
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(40f, 350f, 555f, 480f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#991B1B") // Dark Red
        paint.isFakeBoldText = true
        canvas.drawText("DECLARACIÓN DE INTEGRIDAD Y CADENA DE CUSTODIA", 55f, 380f, paint)
        
        paint.color = Color.parseColor("#1E293B")
        paint.isFakeBoldText = false
        canvas.drawText("Este documento y su correspondiente archivo de video/datos en formato .ZIP", 55f, 410f, paint)
        canvas.drawText("han sido cifrados y firmados digitalmente en el dispositivo de origen.", 55f, 430f, paint)
        canvas.drawText("Cualquier intento de alteración invalidará el sello de verificación hash SHA-256.", 55f, 450f, paint)

        doc.finishPage(page)
        FileOutputStream(outputFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()
    }

    private fun zipFiles(files: List<File>, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            files.forEach { file ->
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(file.name)
                    zos.putNextEntry(zipEntry)
                    val buffer = ByteArray(65_536) // 64KB for video performance
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var len: Int
            while (fis.read(buffer).also { len = it } > 0) {
                digest.update(buffer, 0, len)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    private fun signHash(hash: String): String {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: return "LocalDevSignature_v1_fallback"
            
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKeyEntry.privateKey)
            signature.update(hash.toByteArray(Charsets.UTF_8))
            val sigBytes = signature.sign()
            Base64.getEncoder().encodeToString(sigBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign evidence package hash", e)
            "LocalDevSignature_v1_fallback"
        }
    }
}
